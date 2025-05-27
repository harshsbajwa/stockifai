package com.harshsbajwa.stockifai.stream

import com.harshsbajwa.stockifai.avro.finnhub.MarketNews
import com.harshsbajwa.stockifai.avro.finnhub.StockCandle
import com.harshsbajwa.stockifai.avro.fred.EconomicObservation
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@SpringBootApplication
@EnableScheduling 
@EnableRetry
class Application

fun main(args: Array<String>) {
    println("Starting Stockifai Data Ingest Services...")
    runApplication<Application>(*args)
    println("Stockifai Data Ingest Services started successfully.")
}

// Finnhub API Response Models
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
    @JsonProperty("s") val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FinnhubNews(
    val category: String? = null,
    val datetime: Long? = null,
    val headline: String? = null,
    val id: Long? = null,
    val image: String? = null,
    val related: String? = null,
    val source: String? = null,
    val summary: String? = null,
    val url: String? = null
)

// FRED API Response Models
@JsonIgnoreProperties(ignoreUnknown = true)
data class FredObservation(
    val date: String? = null,
    val value: String? = null,
    @JsonProperty("realtime_start") val realtimeStart: String? = null,
    @JsonProperty("realtime_end") val realtimeEnd: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FredSeriesResponse(
    val observations: List<FredObservation>? = null
)

// Service Metrics
data class ServiceMetrics(
    val successfulRequests: AtomicLong = AtomicLong(0),
    val failedRequests: AtomicLong = AtomicLong(0),
    val lastSuccessfulCollection: AtomicLong = AtomicLong(0),
    val apiQuotaUsage: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()
)

@Service
class FinnhubDataIngestor(
    private val avroKafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(FinnhubDataIngestor::class.java)
    private val webClient = WebClient.builder()
        .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024) }
        .build()
    
    private val metrics = ServiceMetrics()

    @Value("\${app.stocks:AAPL,GOOGL,MSFT,TSLA,AMZN,NVDA,META,SPY,QQQ}")
    private lateinit var stockSymbols: String

    @Value("\${app.finnhub.api.key:#{null}}")
    private val finnhubApiKey: String? = null

    @Value("\${app.finnhub.baseUrl:https://finnhub.io/api/v1}")
    private lateinit var finnhubBaseUrl: String

    @Value("\${app.collection.enabled:true}")
    private val collectionEnabled: Boolean = true

    private val stockList: List<String> by lazy { 
        stockSymbols.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    
    private val currentSymbolIndex = AtomicInteger(0)

    @PostConstruct
    fun init() {
        logger.info("=== Finnhub Data Ingestor Initialized ===")
        logger.info("Collection enabled: {}", collectionEnabled)
        logger.info("Monitoring {} stocks: {}", stockList.size, stockList)
        logger.info("Finnhub API configured: {}", finnhubApiKey != null)
        
        if (finnhubApiKey == null) {
            logger.warn("FINNHUB_API_KEY is not set. Stock data collection will be skipped.")
        }
    }

    // Fetch OHLCV for one symbol every 2 seconds to stay within 60 calls/min limit
    @Scheduled(fixedRate = 2000)
    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 1000))
    fun fetchOhlcvDataScheduled() {
        if (!collectionEnabled || finnhubApiKey == null || stockList.isEmpty()) {
            return
        }

        val symbol = stockList[currentSymbolIndex.getAndIncrement() % stockList.size]
        fetchStockCandles(symbol)
    }

    private fun fetchStockCandles(symbol: String) {
        val toTimestamp = Instant.now().epochSecond
        val fromTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).epochSecond

        val finnhubUrl = UriComponentsBuilder
            .fromUriString("$finnhubBaseUrl/stock/candle")
            .queryParam("symbol", symbol)
            .queryParam("resolution", "D")
            .queryParam("from", fromTimestamp)
            .queryParam("to", toTimestamp)
            .queryParam("token", finnhubApiKey)
            .toUriString()

        webClient.get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(FinnhubStockCandles::class.java)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess { candles ->
                processStockCandles(symbol, candles)
            }
            .doOnError { error ->
                handleStockDataError(symbol, error)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processStockCandles(symbol: String, candles: FinnhubStockCandles) {
        if (candles.status == "no_data" || candles.close.isNullOrEmpty()) {
            logger.debug("No candle data available for {}", symbol)
            return
        }

        val size = candles.close.size
        for (i in 0 until size) {
            try {
                val stockCandle = StockCandle.newBuilder()
                    .setSymbol(symbol)
                    .setOpen(candles.open?.get(i) ?: 0.0)
                    .setHigh(candles.high?.get(i) ?: 0.0)
                    .setLow(candles.low?.get(i) ?: 0.0)
                    .setClose(candles.close[i])
                    .setVolume(candles.volume?.get(i) ?: 0L)
                    .setTimestamp(candles.timestamps?.get(i)?.times(1000) ?: System.currentTimeMillis())
                    .build()

                publishToKafka("finnhub-ohlcv-data", symbol, stockCandle)
                metrics.successfulRequests.incrementAndGet()
            } catch (e: Exception) {
                logger.error("Error processing candle data for {} at index {}: {}", symbol, i, e.message)
                metrics.failedRequests.incrementAndGet()
            }
        }

        logger.debug("Processed {} candles for {}", size, symbol)
        metrics.apiQuotaUsage.computeIfAbsent("finnhub") { AtomicLong(0) }.incrementAndGet()
    }

    // Fetch market news every 5 minutes
    @Scheduled(fixedRate = 300000)
    @Retryable(value = [Exception::class], maxAttempts = 2, backoff = Backoff(delay = 2000))
    fun fetchMarketNewsScheduled() {
        if (!collectionEnabled || finnhubApiKey == null) {
            return
        }

        fetchMarketNews("general")
    }

    private fun fetchMarketNews(category: String) {
        val finnhubUrl = UriComponentsBuilder
            .fromUriString("$finnhubBaseUrl/news")
            .queryParam("category", category)
            .queryParam("token", finnhubApiKey)
            .toUriString()

        webClient.get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(Array<FinnhubNews>::class.java)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
            .timeout(Duration.ofSeconds(15))
            .doOnSuccess { newsArray ->
                processMarketNews(category, newsArray?.toList() ?: emptyList())
            }
            .doOnError { error ->
                handleNewsError(category, error)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processMarketNews(category: String, newsList: List<FinnhubNews>) {
        newsList.forEach { news ->
            try {
                if (news.headline != null && news.summary != null && news.url != null) {
                    val marketNews = MarketNews.newBuilder()
                        .setCategory(category)
                        .setDatetime(news.datetime ?: System.currentTimeMillis())
                        .setHeadline(news.headline)
                        .setId(news.id ?: 0L)
                        .setImage(news.image)
                        .setRelated(news.related)
                        .setSource(news.source ?: "Finnhub")
                        .setSummary(news.summary)
                        .setUrl(news.url)
                        .build()

                    publishToKafka("finnhub-market-news-data", category, marketNews)
                    metrics.successfulRequests.incrementAndGet()
                }
            } catch (e: Exception) {
                logger.error("Error processing news item: {}", e.message)
                metrics.failedRequests.incrementAndGet()
            }
        }

        logger.info("Processed {} news items for category: {}", newsList.size, category)
        metrics.apiQuotaUsage.computeIfAbsent("finnhub") { AtomicLong(0) }.incrementAndGet()
    }

    private fun publishToKafka(topic: String, key: String, message: Any) {
        try {
            val future = avroKafkaTemplate.send(topic, key, message)
            future.whenComplete { result, ex ->
                if (ex != null) {
                    logger.error("Failed to send to Kafka [{}]: {} - {}", topic, key, ex.message)
                } else {
                    logger.debug("Sent to Kafka [{}]: {}", topic, key)
                }
            }
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

    private fun handleNewsError(category: String, error: Throwable) {
        metrics.failedRequests.incrementAndGet()
        when (error) {
            is WebClientResponseException -> {
                when (error.statusCode.value()) {
                    429 -> logger.warn("Rate limit exceeded for Finnhub news API ({})", category)
                    403 -> logger.error("Finnhub news API authentication failed ({})", category)
                    else -> logger.error("Finnhub news API error for {}: {} - {}", category, error.statusCode, error.message)
                }
            }
            else -> logger.error("Error fetching news for {}: {}", category, error.message)
        }
    }
}

@Service
class FREDDataIngestor(
    private val avroKafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(FREDDataIngestor::class.java)
    private val webClient = WebClient.builder()
        .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024) }
        .build()
    
    private val metrics = ServiceMetrics()

    @Value("\${app.fred.api.key:#{null}}")
    private val fredApiKey: String? = null

    @Value("\${app.fred.baseUrl:https://api.stlouisfed.org}")
    private lateinit var fredBaseUrl: String

    @Value("\${app.collection.enabled:true}")
    private val collectionEnabled: Boolean = true

    // Key economic indicators to track
    private val economicIndicators = listOf(
        "VIXCLS",    // CBOE Volatility Index
        "SP500",     // S&P 500 Index
        "NASDAQCOM", // NASDAQ Composite Index
        "DGS10",     // 10-Year Treasury Constant Maturity Rate
        "CPIAUCSL",  // Consumer Price Index
        "UNRATE"     // Unemployment Rate
    )

    @PostConstruct
    fun init() {
        logger.info("=== FRED Data Ingestor Initialized ===")
        logger.info("Collection enabled: {}", collectionEnabled)
        logger.info("Economic indicators configured: {}", economicIndicators.size)
        logger.info("FRED API configured: {}", fredApiKey != null)
        
        if (fredApiKey == null) {
            logger.warn("FRED_API_KEY is not set. Economic indicators collection will be skipped.")
        }
    }

    // Fetch economic indicators every 15 minutes
    @Scheduled(fixedRate = 900000)
    @Retryable(value = [Exception::class], maxAttempts = 2, backoff = Backoff(delay = 2000))
    fun fetchEconomicIndicatorsScheduled() {
        if (!collectionEnabled || fredApiKey == null) {
            return
        }

        logger.info("Collecting economic indicators from FRED...")
        economicIndicators.forEach { seriesId ->
            try {
                fetchSeriesObservations(seriesId)
                Thread.sleep(600) // Rate limiting - FRED allows ~120 requests/minute
            } catch (e: Exception) {
                logger.error("Failed to collect indicator {}: {}", seriesId, e.message)
            }
        }
    }

    private fun fetchSeriesObservations(seriesId: String) {
        val fredUrl = UriComponentsBuilder
            .fromUriString("$fredBaseUrl/fred/series/observations")
            .queryParam("series_id", seriesId)
            .queryParam("api_key", fredApiKey)
            .queryParam("file_type", "json")
            .queryParam("limit", "10")
            .queryParam("sort_order", "desc")
            .toUriString()

        webClient.get()
            .uri(fredUrl)
            .retrieve()
            .bodyToMono(FredSeriesResponse::class.java)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
            .timeout(Duration.ofSeconds(15))
            .doOnSuccess { response ->
                processFredResponse(seriesId, response)
            }
            .doOnError { error ->
                handleFredError(seriesId, error)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processFredResponse(seriesId: String, response: FredSeriesResponse) {
        response.observations?.forEach { observation ->
            try {
                if (observation.date != null && observation.value != null) {
                    val economicObservation = EconomicObservation.newBuilder()
                        .setSeriesId(seriesId)
                        .setObservationDate(observation.date)
                        .setValue(observation.value)
                        .setRealTimeStart(observation.realtimeStart ?: "")
                        .setRealTimeEnd(observation.realtimeEnd ?: "")
                        .build()

                    publishToKafka("fred-economic-observations-data", seriesId, economicObservation)
                    metrics.successfulRequests.incrementAndGet()
                }
            } catch (e: Exception) {
                logger.error("Error processing FRED observation for {}: {}", seriesId, e.message)
                metrics.failedRequests.incrementAndGet()
            }
        }

        logger.debug("Processed {} observations for {}", response.observations?.size ?: 0, seriesId)
        metrics.apiQuotaUsage.computeIfAbsent("fred") { AtomicLong(0) }.incrementAndGet()
    }

    private fun publishToKafka(topic: String, key: String, message: Any) {
        try {
            val future = avroKafkaTemplate.send(topic, key, message)
            future.whenComplete { result, ex ->
                if (ex != null) {
                    logger.error("Failed to send to Kafka [{}]: {} - {}", topic, key, ex.message)
                } else {
                    logger.debug("Sent to Kafka [{}]: {}", topic, key)
                }
            }
        } catch (e: KafkaException) {
            logger.error("Kafka error for {}: {}", key, e.message)
            metrics.failedRequests.incrementAndGet()
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
}

@Service
class MetricsService(
    private val finnhubIngestor: FinnhubDataIngestor,
    private val fredIngestor: FREDDataIngestor
) {
    private val logger = LoggerFactory.getLogger(MetricsService::class.java)

    @Scheduled(fixedRate = 60000) // Every minute
    fun logMetrics() {
        logServiceMetrics("Finnhub", finnhubIngestor.metrics)
        logServiceMetrics("FRED", fredIngestor.metrics)
    }

    private fun logServiceMetrics(serviceName: String, metrics: ServiceMetrics) {
        val successful = metrics.successfulRequests.get()
        val failed = metrics.failedRequests.get()
        val total = successful + failed
        val successRate = if (total > 0) (successful * 100.0 / total) else 0.0
        val lastSuccess = metrics.lastSuccessfulCollection.get()
        val timeSinceLastSuccess = if (lastSuccess > 0) Instant.now().epochSecond - lastSuccess else -1

        logger.info("{} Metrics - Success: {}, Failed: {}, Rate: {:.1f}%, Last: {}s ago", 
            serviceName, successful, failed, successRate, timeSinceLastSuccess)
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    fun resetHourlyMetrics() {
        finnhubIngestor.metrics.apiQuotaUsage.clear()
        fredIngestor.metrics.apiQuotaUsage.clear()
        logger.info("Hourly API usage metrics reset")
    }
}
