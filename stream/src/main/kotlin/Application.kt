package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.apache.kafka.common.KafkaException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@SpringBootApplication
@EnableScheduling 
@EnableRetry
class Application

fun main(args: Array<String>) {
    println("Starting Stockifai Data Stream Collector...")
    runApplication<Application>(*args)
    println("Stockifai Data Stream Collector has started successfully.")
}

// Enhanced Data Models
@JsonIgnoreProperties(ignoreUnknown = true)
data class StockQuoteData(
    val symbol: String,
    val regularMarketPrice: Double?,
    val regularMarketVolume: Long?,
    val regularMarketChangePercent: Double?,
    val regularMarketDayHigh: Double?,
    val regularMarketDayLow: Double?,
    val regularMarketOpen: Double?,
    val previousClosePrice: Double?,
    val timestamp: Long,
    val marketState: String = "REGULAR" // REGULAR, PRE, POST, CLOSED
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FinnhubQuote(
    @JsonProperty("c") val currentPrice: Double? = null,
    @JsonProperty("h") val highPriceOfDay: Double? = null,
    @JsonProperty("l") val lowPriceOfDay: Double? = null,
    @JsonProperty("o") val openPriceOfDay: Double? = null,
    @JsonProperty("pc") val previousClosePrice: Double? = null,
    @JsonProperty("t") val timestamp: Long? = null,
    @JsonProperty("d") val change: Double? = null,
    @JsonProperty("dp") val percentChange: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FinnhubStockCandles(
    @JsonProperty("o") val open: List<Double>? = null,
    @JsonProperty("h") val high: List<Double>? = null,
    @JsonProperty("l") val low: List<Double>? = null,
    @JsonProperty("c") val close: List<Double>? = null,
    @JsonProperty("v") val volume: List<Long>? = null,
    @JsonProperty("t") val timestamps: List<Long>? = null,
    @JsonProperty("s") val status: String? = null, // "ok" or "no_data"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EconomicIndicator(
    val indicator: String,
    val value: Double,
    val date: String,
    val timestamp: Long = Instant.now().epochSecond,
    val source: String = "FRED"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FredObservation(
    val date: String? = null,
    val value: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FredSeriesResponse(
    val observations: List<FredObservation>? = null
)

// Enhanced Kafka message wrapper with metadata
data class StockMessage(
    val messageType: String,
    val data: Any,
    val timestamp: Long = Instant.now().epochSecond,
    val source: String = "stockifai-stream",
    val version: String = "1.0"
)

// Health and metrics tracking
data class ServiceMetrics(
    val successfulRequests: AtomicLong = AtomicLong(0),
    val failedRequests: AtomicLong = AtomicLong(0),
    val lastSuccessfulCollection: AtomicLong = AtomicLong(0),
    val apiQuotaUsage: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()
)

@Service
class DataCollectionService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(DataCollectionService::class.java)
    private val webClient = WebClient.builder()
        .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024) }
        .build()
    
    private val metrics = ServiceMetrics()

    @Value("\${app.stocks:AAPL,GOOGL,MSFT,TSLA,AMZN,NVDA,META,BRK.A,JNJ,V,SPY,QQQ}")
    private lateinit var stockSymbols: String

    @Value("\${app.finnhub.api.key:#{null}}")
    private val finnhubApiKey: String? = null

    @Value("\${app.fred.api.key:#{null}}")
    private val fredApiKey: String? = null

    @Value("\${app.finnhub.baseUrl:https://finnhub.io/api/v1}")
    private lateinit var finnhubBaseUrl: String

    @Value("\${app.fred.baseUrl:https://api.stlouisfed.org}")
    private lateinit var fredBaseUrl: String

    @Value("\${app.collection.enabled:true}")
    private val collectionEnabled: Boolean = true

    @Value("\${app.market.hours.only:false}")
    private val marketHoursOnly: Boolean = false

    private val stockList: List<String> by lazy { 
        stockSymbols.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    // Economic indicators to track
    private val economicIndicators = listOf(
        "FEDFUNDS" to "Federal Funds Rate",
        "VIXCLS" to "VIX Volatility Index",
        "DGS10" to "10-Year Treasury Rate",
        "UNRATE" to "Unemployment Rate",
        "CPIAUCSL" to "Consumer Price Index"
    )

    @PostConstruct
    fun init() {
        logger.info("=== Stockifai Data Collection Service Initialized ===")
        logger.info("Collection enabled: {}", collectionEnabled)
        logger.info("Market hours only: {}", marketHoursOnly)
        logger.info("Monitoring {} stocks: {}", stockList.size, stockList)
        logger.info("Economic indicators configured: {}", economicIndicators.size)
        logger.info("Finnhub API configured: {}", finnhubApiKey != null)
        logger.info("FRED API configured: {}", fredApiKey != null)
        
        if (finnhubApiKey == null) {
            logger.warn("FINNHUB_API_KEY is not set. Stock data collection will be skipped.")
        }
        if (fredApiKey == null) {
            logger.warn("FRED_API_KEY is not set. Economic indicators collection will be skipped.")
        }
        
        // Validate Kafka connectivity
        validateKafkaConnectivity()
    }

    private fun validateKafkaConnectivity() {
        try {
            val testMessage = StockMessage("health_check", mapOf("status" to "ok"))
            val messageJson = objectMapper.writeValueAsString(testMessage)
            kafkaTemplate.send("stock_prices", "HEALTH_CHECK", messageJson).get()
            logger.info("Kafka connectivity validated successfully")
        } catch (e: Exception) {
            logger.error("Kafka connectivity validation failed: {}", e.message)
        }
    }

    private fun isMarketHours(): Boolean {
        if (!marketHoursOnly) return true
        
        val now = Instant.now().atZone(ZoneOffset.of("-05:00")) // EST
        val hour = now.hour
        val dayOfWeek = now.dayOfWeek.value
        
        // Monday (1) to Friday (5), 9:30 AM to 4:00 PM EST
        return dayOfWeek in 1..5 && hour in 9..16
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 1000))
    fun collectStockData() {
        if (!collectionEnabled || finnhubApiKey == null || !isMarketHours()) {
            logger.debug("Stock data collection skipped - enabled: {}, api_key: {}, market_hours: {}", 
                collectionEnabled, finnhubApiKey != null, isMarketHours())
            return
        }

        logger.info("Starting stock data collection cycle for {} symbols...", stockList.size)
        val startTime = System.currentTimeMillis()

        stockList.forEach { symbol ->
            collectStockQuote(symbol)
        }

        val duration = System.currentTimeMillis() - startTime
        logger.info("Completed stock data collection cycle in {}ms", duration)
        metrics.lastSuccessfulCollection.set(Instant.now().epochSecond)
    }

    private fun collectStockQuote(symbol: String) {
        val finnhubUrl = UriComponentsBuilder
            .fromUriString("$finnhubBaseUrl/quote")
            .queryParam("symbol", symbol)
            .queryParam("token", finnhubApiKey)
            .toUriString()

        webClient.get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(FinnhubQuote::class.java)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess { finnhubQuote ->
                processStockQuote(symbol, finnhubQuote)
            }
            .doOnError { error ->
                handleStockDataError(symbol, error)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processStockQuote(symbol: String, finnhubQuote: FinnhubQuote) {
        if (finnhubQuote.currentPrice == null || finnhubQuote.currentPrice <= 0) {
            logger.warn("Invalid quote data for {}: price={}", symbol, finnhubQuote.currentPrice)
            metrics.failedRequests.incrementAndGet()
            return
        }

        val stockQuote = StockQuoteData(
            symbol = symbol,
            regularMarketPrice = finnhubQuote.currentPrice,
            regularMarketVolume = null, // Finnhub /quote endpoint doesn't provide volume
            regularMarketChangePercent = finnhubQuote.percentChange,
            regularMarketDayHigh = finnhubQuote.highPriceOfDay,
            regularMarketDayLow = finnhubQuote.lowPriceOfDay,
            regularMarketOpen = finnhubQuote.openPriceOfDay,
            previousClosePrice = finnhubQuote.previousClosePrice,
            timestamp = finnhubQuote.timestamp ?: Instant.now().epochSecond,
            marketState = determineMarketState()
        )

        publishToKafka("stock_prices", symbol, StockMessage("stock_quote", stockQuote))
        metrics.successfulRequests.incrementAndGet()
        metrics.apiQuotaUsage.computeIfAbsent("finnhub") { AtomicLong(0) }.incrementAndGet()
        
        logger.debug("{} - Price: ${%.2f}, Change: ${%.2f}%", 
            symbol, stockQuote.regularMarketPrice, stockQuote.regularMarketChangePercent ?: 0.0)
    }

    private fun determineMarketState(): String {
        val now = Instant.now().atZone(ZoneOffset.of("-05:00"))
        val hour = now.hour
        val minute = now.minute
        val currentTime = hour * 100 + minute
        
        return when {
            currentTime < 930 -> "PRE"
            currentTime > 1600 -> "POST"
            now.dayOfWeek.value in 1..5 -> "REGULAR"
            else -> "CLOSED"
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Retryable(value = [Exception::class], maxAttempts = 2, backoff = Backoff(delay = 2000))
    fun collectMarketVolatility() {
        if (!collectionEnabled || fredApiKey == null) {
            logger.debug("Market volatility collection skipped - enabled: {}, fred_key: {}", 
                collectionEnabled, fredApiKey != null)
            return
        }

        logger.info("Collecting VIX volatility data from FRED...")
        collectFredIndicator("VIXCLS", "volatility_indicator")
    }

    @Scheduled(fixedRate = 900000) // Every 15 minutes
    @Retryable(value = [Exception::class], maxAttempts = 2, backoff = Backoff(delay = 2000))
    fun collectEconomicIndicators() {
        if (!collectionEnabled || fredApiKey == null) {
            logger.debug("Economic indicators collection skipped - enabled: {}, fred_key: {}", 
                collectionEnabled, fredApiKey != null)
            return
        }

        logger.info("Collecting economic indicators from FRED...")
        economicIndicators.forEach { (seriesId, description) ->
            try {
                collectFredIndicator(seriesId, "economic_indicator")
                Thread.sleep(200) // Rate limiting - FRED allows 120 requests/minute
            } catch (e: Exception) {
                logger.error("Failed to collect indicator {}: {}", seriesId, e.message)
            }
        }
    }

    private fun collectFredIndicator(seriesId: String, messageType: String) {
        val fredUrl = UriComponentsBuilder
            .fromUriString("$fredBaseUrl/fred/series/observations")
            .queryParam("series_id", seriesId)
            .queryParam("api_key", fredApiKey)
            .queryParam("file_type", "json")
            .queryParam("limit", "1")
            .queryParam("sort_order", "desc")
            .toUriString()

        webClient.get()
            .uri(fredUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
            .timeout(Duration.ofSeconds(15))
            .doOnSuccess { responseString ->
                processFredResponse(seriesId, messageType, responseString)
            }
            .doOnError { error ->
                handleFredError(seriesId, error)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processFredResponse(seriesId: String, messageType: String, responseString: String) {
        try {
            val fredResponse = objectMapper.readValue(responseString, FredSeriesResponse::class.java)
            val latestObservation = fredResponse.observations?.firstOrNull()

            if (latestObservation?.value == null || latestObservation.value == "." || latestObservation.date == null) {
                logger.warn("No valid {} observation found", seriesId)
                return
            }

            val indicatorValue = latestObservation.value.toDoubleOrNull()
            if (indicatorValue == null) {
                logger.warn("Could not parse {} value: '{}'", seriesId, latestObservation.value)
                return
            }

            val indicator = EconomicIndicator(
                indicator = seriesId,
                value = indicatorValue,
                date = latestObservation.date,
                timestamp = Instant.now().epochSecond
            )

            publishToKafka("economic_indicators", seriesId, StockMessage(messageType, indicator))
            metrics.successfulRequests.incrementAndGet()
            metrics.apiQuotaUsage.computeIfAbsent("fred") { AtomicLong(0) }.incrementAndGet()
            
            logger.info("{} = {} ({})", seriesId, indicatorValue, latestObservation.date)
        } catch (e: Exception) {
            logger.error("Error processing FRED response for {}: {}", seriesId, e.message)
            metrics.failedRequests.incrementAndGet()
        }
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    fun collectIntradayData() {
        if (!collectionEnabled || finnhubApiKey == null || !isMarketHours()) {
            return
        }

        logger.info("Collecting intraday candle data...")
        val symbols = listOf("SPY", "QQQ", "IWM") // Major market ETFs
        
        symbols.forEach { symbol ->
            collectIntradayCandles(symbol)
        }
    }

    private fun collectIntradayCandles(symbol: String) {
        val toTimestamp = Instant.now().epochSecond
        val fromTimestamp = Instant.now().minus(2, ChronoUnit.HOURS).epochSecond

        val finnhubUrl = UriComponentsBuilder
            .fromUriString("$finnhubBaseUrl/stock/candle")
            .queryParam("symbol", symbol)
            .queryParam("resolution", "5")
            .queryParam("from", fromTimestamp)
            .queryParam("to", toTimestamp)
            .queryParam("token", finnhubApiKey)
            .toUriString()

        webClient.get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(FinnhubStockCandles::class.java)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess { candles ->
                processIntradayCandles(symbol, candles)
            }
            .doOnError { error ->
                logger.error("Error fetching intraday data for {}: {}", symbol, error.message)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processIntradayCandles(symbol: String, candles: FinnhubStockCandles) {
        if (candles.status == "no_data" || candles.close.isNullOrEmpty()) {
            logger.debug("No intraday data available for {}", symbol)
            return
        }

        val candleData = mapOf(
            "symbol" to symbol,
            "candles" to candles,
            "count" to candles.close?.size
        )

        publishToKafka("intraday_data", symbol, StockMessage("intraday_candles", candleData))
        logger.info("Intraday data collected for {}: {} candles", symbol, candles.close?.size ?: 0)
    }

    private fun publishToKafka(topic: String, key: String, message: StockMessage) {
        try {
            val messageJson = objectMapper.writeValueAsString(message)
            kafkaTemplate.send(topic, key, messageJson)
                .addCallback(
                    { logger.debug("Sent to Kafka [{}]: {}", topic, key) },
                    { ex -> logger.error("Failed to send to Kafka [{}]: {} - {}", topic, key, ex.message) }
                )
        } catch (e: JsonProcessingException) {
            logger.error("JSON serialization error for {}: {}", key, e.message)
            metrics.failedRequests.incrementAndGet()
        } catch (e: KafkaException) {
            logger.error("Kafka error for {}: {}", key, e.message)
            metrics.failedRequests.incrementAndGet()
        }
    }

    private fun handleStockDataError(symbol: String, error: Throwable) {
        metrics.failedRequests.incrementAndGet()
        when (error) {
            is WebClientResponseException -> {
                when (error.statusCode.value()) {
                    429 -> logger.warn("Rate limit exceeded for Finnhub API ({})", symbol)
                    403 -> logger.error("Finnhub API authentication failed ({})", symbol)
                    else -> logger.error("Finnhub API error for {}: {} - {}", symbol, error.statusCode, error.message)
                }
            }
            else -> logger.error("Error fetching stock data for {}: {}", symbol, error.message)
        }
    }

    private fun handleFredError(seriesId: String, error: Throwable) {
        metrics.failedRequests.incrementAndGet()
        when (error) {
            is WebClientResponseException -> {
                when (error.statusCode.value()) {
                    429 -> logger.warn("Rate limit exceeded for FRED API ({})", seriesId)
                    400 -> logger.error("Invalid FRED API request for {}: {}", seriesId, error.message)
                    else -> logger.error("FRED API error for {}: {} - {}", seriesId, error.statusCode, error.message)
                }
            }
            else -> logger.error("Error fetching FRED data for {}: {}", seriesId, error.message)
        }
    }

    @Scheduled(fixedRate = 60000) // Every minute
    fun logMetrics() {
        val successful = metrics.successfulRequests.get()
        val failed = metrics.failedRequests.get()
        val total = successful + failed
        val successRate = if (total > 0) (successful * 100.0 / total) else 0.0
        val lastSuccess = metrics.lastSuccessfulCollection.get()
        val timeSinceLastSuccess = Instant.now().epochSecond - lastSuccess

        logger.info("Metrics - Success: {}, Failed: {}, Rate: {:.1f}%, Last: {}s ago", 
            successful, failed, successRate, timeSinceLastSuccess)
        
        metrics.apiQuotaUsage.forEach { (api, count) ->
            logger.debug("API Usage - {}: {} requests", api, count.get())
        }
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    fun resetHourlyMetrics() {
        metrics.apiQuotaUsage.clear()
        logger.info("Hourly API usage metrics reset")
    }
}