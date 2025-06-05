package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.avro.finnhub.MarketNews
import com.harshsbajwa.stockifai.avro.finnhub.StockCandle
import com.harshsbajwa.stockifai.avro.fred.EconomicObservation
import com.harshsbajwa.stockifai.stream.config.AppConfig
import jakarta.annotation.PostConstruct
import org.apache.avro.util.Utf8
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.TopicExistsException
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
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
data class FinnhubNews(
    val category: String? = null,
    val datetime: Long? = null,
    val headline: String? = null,
    val id: Long? = null,
    val image: String? = null,
    val related: String? = null,
    val source: String? = null,
    val summary: String? = null,
    val url: String? = null,
)

// FRED API Response Models
@JsonIgnoreProperties(ignoreUnknown = true)
data class FredObservation(
    val date: String? = null,
    val value: String? = null,
    @JsonProperty("realtime_start") val realtimeStart: String? = null,
    @JsonProperty("realtime_end") val realtimeEnd: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FredSeriesResponse(
    val observations: List<FredObservation>? = null,
)

// Alphavantage API Response Models
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphavantageMetaData(
    @JsonProperty("1. Information") val information: String?,
    @JsonProperty("2. Symbol") val symbol: String?,
    @JsonProperty("3. Last Refreshed") val lastRefreshed: String?,
    @JsonProperty("4. Interval") val interval: String?,
    @JsonProperty("4. Output Size") val outputSizeDaily: String?,
    @JsonProperty("5. Output Size") val outputSizeIntraday: String?,
    @JsonProperty("5. Time Zone") val timeZoneDaily: String?,
    @JsonProperty("6. Time Zone") val timeZoneIntraday: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphavantageCandleData(
    @JsonProperty("1. open") val open: String?,
    @JsonProperty("2. high") val high: String?,
    @JsonProperty("3. low") val low: String?,
    @JsonProperty("4. close") val close: String?,
    @JsonProperty("5. volume") val volume: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphavantageIntradayResponse(
    @JsonProperty("Meta Data") val metaData: AlphavantageMetaData? = null,
    @JsonProperty("Time Series (1min)") val timeSeries1min: Map<String, AlphavantageCandleData>? = null,
    @JsonProperty("Time Series (5min)") val timeSeries5min: Map<String, AlphavantageCandleData>? = null,
    @JsonProperty("Time Series (15min)") val timeSeries15min: Map<String, AlphavantageCandleData>? = null,
    @JsonProperty("Time Series (30min)") val timeSeries30min: Map<String, AlphavantageCandleData>? = null,
    @JsonProperty("Time Series (60min)") val timeSeries60min: Map<String, AlphavantageCandleData>? = null,
    @JsonProperty("Note") val note: String? = null,
    @JsonProperty("Information") val information: String? = null,
    @JsonProperty("Error Message") val errorMessage: String? = null,
) {
    fun getTimeSeriesForInterval(interval: String): Map<String, AlphavantageCandleData>? =
        when (interval) {
            "1min" -> timeSeries1min
            "5min" -> timeSeries5min
            "15min" -> timeSeries15min
            "30min" -> timeSeries30min
            "60min" -> timeSeries60min
            else -> null
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphavantageDailyResponse(
    @JsonProperty("Meta Data") val metaData: AlphavantageMetaData? = null,
    @JsonProperty("Time Series (Daily)") val timeSeriesDaily: Map<String, AlphavantageCandleData>? = null,
    @JsonProperty("Note") val note: String? = null,
    @JsonProperty("Information") val information: String? = null,
    @JsonProperty("Error Message") val errorMessage: String? = null,
)

// Service Metrics
data class ServiceMetrics(
    val successfulRequests: AtomicLong = AtomicLong(0),
    val failedRequests: AtomicLong = AtomicLong(0),
    val lastSuccessfulCollection: AtomicLong = AtomicLong(0),
    val apiQuotaUsage: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap(),
)

@Component
class KafkaTopicConfiguration(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String,
) {
    private val logger = LoggerFactory.getLogger(KafkaTopicConfiguration::class.java)

    @PostConstruct
    fun createTopics() {
        val adminClient =
            AdminClient.create(
                mapOf(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ),
            )

        val topics =
            listOf(
                NewTopic("dead-letter-queue", 1, 1.toShort()),
                NewTopic("data-quality-metrics", 1, 1.toShort()),
            )

        try {
            val result = adminClient.createTopics(topics)
            result.all().get(30, TimeUnit.SECONDS)
            logger.info("Successfully created monitoring topics")
        } catch (e: TopicExistsException) {
            logger.info("Monitoring topics already exist")
        } catch (e: Exception) {
            logger.warn("Failed to create monitoring topics: ${e.message}")
        } finally {
            adminClient.close()
        }
    }
}

@Service
class DataQualityMonitoringService(
    private val avroKafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val logger = LoggerFactory.getLogger(DataQualityMonitoringService::class.java)
    private val invalidRecordCount = AtomicLong(0)
    private val validRecordCount = AtomicLong(0)

    fun recordValidRecord(
        source: String,
        recordType: String,
    ) {
        validRecordCount.incrementAndGet()
        logger.debug("Valid {} record from {}", recordType, source)
    }

    fun recordInvalidRecord(
        source: String,
        recordType: String,
        reason: String,
        data: Any? = null,
    ) {
        invalidRecordCount.incrementAndGet()
        logger.warn(
            "Invalid {} record from {}: {}. Data: {}",
            recordType,
            source,
            reason,
            data?.toString()?.take(100),
        )

        // Send to dead letter topic for analysis
        try {
            val deadLetterRecord =
                mapOf(
                    "source" to source,
                    "recordType" to recordType,
                    "reason" to reason,
                    "timestamp" to Instant.now().toString(),
                    "data" to data?.toString()?.take(500),
                )

            avroKafkaTemplate.send("dead-letter-queue", source, deadLetterRecord)
        } catch (e: Exception) {
            logger.error("Failed to send record to dead letter queue", e)
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun logDataQualityMetrics() {
        val valid = validRecordCount.get()
        val invalid = invalidRecordCount.get()
        val total = valid + invalid
        val qualityRate = if (total > 0) (valid.toDouble() / total * 100) else 100.0

        logger.info(
            "Data Quality Metrics - Valid: {}, Invalid: {}, Quality Rate: {:.2f}%",
            valid,
            invalid,
            qualityRate,
        )

        if (qualityRate < 95.0) {
            logger.warn("Data quality degraded! Quality rate: {:.2f}%", qualityRate)
        }
    }
}

@Service
class FinnhubDataIngestor(
    private val avroKafkaTemplate: KafkaTemplate<String, Any>,
    private val appConfig: AppConfig,
) {
    private val logger = LoggerFactory.getLogger(FinnhubDataIngestor::class.java)
    private val webClient =
        WebClient
            .builder()
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024) }
            .build()

    internal val metrics = ServiceMetrics()

    private val stockList: List<String> by lazy {
        appConfig.stocks
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private val currentQuoteSymbolIndex = AtomicInteger(0)
    private val finnhubApiKey: String? by lazy { appConfig.finnhub.api.key }

    @PostConstruct
    fun init() {
        logger.info("=== Finnhub Data Ingestor Initialized ===")
        logger.info("Collection enabled: {}", appConfig.collection.enabled)
        logger.info("Monitoring {} stocks for quotes: {}", stockList.size, stockList)
        logger.info("Finnhub API configured for quotes/news: {}", finnhubApiKey != null)

        if (finnhubApiKey == null) {
            logger.warn("FINNHUB_API_KEY is not set. Finnhub data collection will be skipped.")
        }
    }

    @Scheduled(fixedRate = 60000)
    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 1000))
    fun fetchQuoteDataScheduled() {
        if (!appConfig.collection.enabled || finnhubApiKey == null || stockList.isEmpty()) {
            return
        }

        val symbol = stockList[currentQuoteSymbolIndex.getAndIncrement() % stockList.size]
        fetchStockQuote(symbol)
    }

    private fun fetchStockQuote(symbol: String) {
        val finnhubUrl =
            UriComponentsBuilder
                .fromUriString("${appConfig.finnhub.baseUrl}/quote")
                .queryParam("symbol", symbol)
                .queryParam("token", finnhubApiKey)
                .toUriString()

        webClient
            .get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(FinnhubQuote::class.java)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).maxAttempts(2))
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess { quote ->
                processStockQuote(symbol, quote)
                metrics.apiQuotaUsage.computeIfAbsent("finnhub_quote") { AtomicLong(0) }.incrementAndGet()
            }.doOnError { error ->
                handleFinnhubError(symbol, "quote", error)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processStockQuote(
        symbol: String,
        quote: FinnhubQuote,
    ) {
        try {
            if (symbol.isBlank()) {
                logger.warn("Skipping quote with empty symbol")
                metrics.failedRequests.incrementAndGet()
                return
            }

            val timestampMillis =
                if (quote.timestamp != null && quote.timestamp > 0) {
                    quote.timestamp * 1000L
                } else {
                    System.currentTimeMillis()
                }

            val currentPrice = quote.currentPrice?.takeIf { it > 0 } ?: return
            val open = quote.openPriceOfDay?.takeIf { it > 0 } ?: currentPrice
            val high = quote.highPriceOfDay?.takeIf { it > 0 } ?: currentPrice
            val low = quote.lowPriceOfDay?.takeIf { it > 0 } ?: currentPrice
            val close = currentPrice

            if (high < low || open < 0 || close < 0 || high < close || low > close) {
                logger.warn(
                    "Invalid OHLC relationships for {}: O={}, H={}, L={}, C={}",
                    symbol,
                    open,
                    high,
                    low,
                    close,
                )
                metrics.failedRequests.incrementAndGet()
                return
            }

            logger.debug(
                "Creating StockCandle for {}: timestamp={} ({}), O={}, H={}, L={}, C={}",
                symbol,
                timestampMillis,
                Instant.ofEpochMilli(timestampMillis),
                open,
                high,
                low,
                close,
            )

            val stockCandle =
                StockCandle
                    .newBuilder()
                    .setSymbol(Utf8(symbol))
                    .setOpen(open)
                    .setHigh(high)
                    .setLow(low)
                    .setClose(close)
                    .setVolume(0L)
                    .setTimestamp(Instant.ofEpochMilli(timestampMillis))
                    .build()

            publishToKafka("finnhub-ohlcv-data", symbol, stockCandle)
            metrics.successfulRequests.incrementAndGet()
            metrics.lastSuccessfulCollection.set(Instant.now().epochSecond)

            logger.info(
                "Successfully processed quote for {} at price {} timestamp {}",
                symbol,
                close,
                Instant.ofEpochMilli(timestampMillis),
            )
        } catch (e: Exception) {
            logger.error("Error processing quote data for {}: {}", symbol, e.message, e)
            metrics.failedRequests.incrementAndGet()
        }
    }

    private fun isValidPriceData(
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): Boolean {
        return true
        // open > 0 && high > 0 && low > 0 && close > 0 &&
        // high >= low && high >= open && high >= close &&
        // low <= open && low <= close // &&
        // open < 1_000_000 && high < 1_000_000 && low < 1_000_000 && close < 1_000_000  // Reasonable price limits
    }

    // Fetch market news every 5 minutes
    @Scheduled(fixedRate = 300000)
    @Retryable(value = [Exception::class], maxAttempts = 2, backoff = Backoff(delay = 2000))
    fun fetchMarketNewsScheduled() {
        if (!appConfig.collection.enabled || finnhubApiKey == null) {
            return
        }
        fetchMarketNews("general")
    }

    private fun fetchMarketNews(category: String) {
        val finnhubUrl =
            UriComponentsBuilder
                .fromUriString("${appConfig.finnhub.baseUrl}/news")
                .queryParam("category", category)
                .queryParam("token", finnhubApiKey)
                .toUriString()

        webClient
            .get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(Array<FinnhubNews>::class.java)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
            .timeout(Duration.ofSeconds(15))
            .doOnSuccess { newsArray ->
                processMarketNews(category, newsArray?.toList() ?: emptyList())
                metrics.apiQuotaUsage.computeIfAbsent("finnhub_news") { AtomicLong(0) }.incrementAndGet()
            }.doOnError { error ->
                handleFinnhubError(category, "news", error)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processMarketNews(
        category: String,
        newsList: List<FinnhubNews>,
    ) {
        var validCount = 0
        var invalidCount = 0

        newsList.forEach { news ->
            try {
                if (isValidNewsRecord(news)) {
                    val originalDatetimeSeconds = news.datetime
                    val datetimeMillis = originalDatetimeSeconds?.let { it * 1000L }

                    val marketNews =
                        MarketNews
                            .newBuilder()
                            .setCategory(Utf8(news.category ?: category))
                            .setDatetime(
                                datetimeMillis?.let { Instant.ofEpochMilli(it) }
                                    ?: Instant.now(),
                            ).setHeadline(Utf8(news.headline!!))
                            .setId(news.id ?: System.currentTimeMillis())
                            .setImage(news.image?.let { Utf8(it) })
                            .setRelated(news.related?.let { Utf8(it) })
                            .setSource(Utf8(news.source ?: "Finnhub"))
                            .setSummary(Utf8(news.summary!!))
                            .setUrl(Utf8(news.url!!))
                            .build()

                    publishToKafka("finnhub-market-news-data", news.related ?: category, marketNews)
                    validCount++
                    metrics.successfulRequests.incrementAndGet()
                } else {
                    invalidCount++
                    logger.warn(
                        "Invalid news record skipped: headline={}, summary={}, url={}",
                        news.headline?.take(50),
                        news.summary?.take(50),
                        news.url,
                    )
                }
            } catch (e: Exception) {
                invalidCount++
                logger.error("Error processing news item: {}", e.message, e)
                metrics.failedRequests.incrementAndGet()
            }
        }

        logger.info("Processed news for category {}: {} valid, {} invalid", category, validCount, invalidCount)
    }

    private fun isValidNewsRecord(news: FinnhubNews): Boolean =
        !news.headline.isNullOrBlank() &&
            !news.summary.isNullOrBlank() &&
            !news.url.isNullOrBlank() &&
            news.headline!!.length <= 1000 &&
            news.summary!!.length <= 5000 &&
            news.url!!.startsWith("http")

    private fun publishToKafka(
        topic: String,
        key: String,
        message: Any,
    ) {
        try {
            val future = avroKafkaTemplate.send(topic, key, message)
            future.whenComplete { _, ex ->
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

    private fun handleFinnhubError(
        identifier: String,
        type: String,
        error: Throwable,
    ) {
        metrics.failedRequests.incrementAndGet()
        when (error) {
            is WebClientResponseException -> {
                val errorBody = error.responseBodyAsString
                when (error.statusCode.value()) {
                    429 ->
                        logger.warn(
                            "Rate limit exceeded for Finnhub API ($type for {}). Response: {}",
                            identifier,
                            errorBody,
                        )
                    403 ->
                        logger.error(
                            "Finnhub API authentication failed ($type for {}). Response: {}",
                            identifier,
                            errorBody,
                        )
                    401 ->
                        logger.error(
                            "Finnhub API unauthorized (check API key) ($type for {}). Response: {}",
                            identifier,
                            errorBody,
                        )
                    else ->
                        logger.error(
                            "Finnhub API error for {} ($type): {} - {}. Response: {}",
                            identifier,
                            type,
                            error.statusCode,
                            error.message,
                            errorBody,
                        )
                }
            }
            else -> logger.error("Error fetching Finnhub $type data for {}: {}", identifier, error.message, error)
        }
    }
}

@Service
class FREDDataIngestor(
    private val avroKafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(FREDDataIngestor::class.java)
    private val webClient =
        WebClient
            .builder()
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024) }
            .build()

    internal val metrics = ServiceMetrics()

    @Value("\${app.fred.api.key:#{null}}")
    private val fredApiKey: String? = null

    @Value("\${app.fred.baseUrl:https://api.stlouisfed.org}")
    private lateinit var fredBaseUrl: String

    @Value("\${app.collection.enabled:true}")
    private val collectionEnabled: Boolean = true

    // Key economic indicators to track
    private val economicIndicators =
        listOf(
            "VIXCLS", // CBOE Volatility Index
            "SP500", // S&P 500 Index
            "NASDAQCOM", // NASDAQ Composite Index
            "DGS10", // 10-Year Treasury Constant Maturity Rate
            "CPIAUCSL", // Consumer Price Index
            "UNRATE", // Unemployment Rate
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
        val fredUrl =
            UriComponentsBuilder
                .fromUriString("$fredBaseUrl/fred/series/observations")
                .queryParam("series_id", seriesId)
                .queryParam("api_key", fredApiKey)
                .queryParam("file_type", "json")
                .queryParam("limit", "10")
                .queryParam("sort_order", "desc")
                .toUriString()

        webClient
            .get()
            .uri(fredUrl)
            .retrieve()
            .bodyToMono(FredSeriesResponse::class.java)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
            .timeout(Duration.ofSeconds(15))
            .doOnSuccess { response ->
                processFredResponse(seriesId, response)
            }.doOnError { error ->
                handleFredError(seriesId, error)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun processFredResponse(
        seriesId: String,
        response: FredSeriesResponse,
    ) {
        response.observations?.forEach { observation ->
            try {
                if (observation.date != null && observation.value != null) {
                    val economicObservation =
                        EconomicObservation
                            .newBuilder()
                            .setSeriesId(Utf8(seriesId))
                            .setObservationDate(Utf8(observation.date))
                            .setValue(Utf8(observation.value))
                            .setRealTimeStart(Utf8(observation.realtimeStart ?: ""))
                            .setRealTimeEnd(Utf8(observation.realtimeEnd ?: ""))
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

    private fun publishToKafka(
        topic: String,
        key: String,
        message: Any,
    ) {
        try {
            val future = avroKafkaTemplate.send(topic, key, message)
            future.whenComplete { _, ex ->
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

    private fun handleFredError(
        seriesId: String,
        error: Throwable,
    ) {
        metrics.failedRequests.incrementAndGet()
        when (error) {
            is WebClientResponseException -> {
                val errorBody = error.responseBodyAsString
                when (error.statusCode.value()) {
                    429 -> logger.warn("Rate limit exceeded for FRED API ({})", seriesId, errorBody)
                    400 -> logger.error("Invalid FRED API request for {}: {}", seriesId, error.message, errorBody)
                    else ->
                        logger.error(
                            "FRED API error for {}: {} - {}",
                            seriesId,
                            error.statusCode,
                            error.message,
                            errorBody,
                        )
                }
            }
            else -> logger.error("Error fetching FRED data for {}: {}", seriesId, error.message, error)
        }
    }
}

@Service
class AlphavantageApiQuotaService {
    private val logger = LoggerFactory.getLogger(AlphavantageApiQuotaService::class.java)
    private val MAX_CALLS_PER_DAY = 25
    private var callsToday = AtomicInteger(0)
    private var lastResetDate = AtomicReference(LocalDate.now(ZoneId.of("UTC")))

    fun canMakeCall(): Boolean {
        resetIfNewDay()
        val currentCalls = callsToday.get()
        if (currentCalls < MAX_CALLS_PER_DAY) {
            return true
        }
        logger.warn("Alphavantage API daily quota of $MAX_CALLS_PER_DAY reached. Calls made: $currentCalls.")
        return false
    }

    fun recordCall() {
        resetIfNewDay()
        if (callsToday.get() < MAX_CALLS_PER_DAY) {
            val newCount = callsToday.incrementAndGet()
            logger.info("Alphavantage API call recorded. Calls today: $newCount/$MAX_CALLS_PER_DAY")
        } else {
            logger.warn("Attempted to record Alphavantage API call, but quota already reached.")
        }
    }

    private fun resetIfNewDay() {
        val today = LocalDate.now(ZoneId.of("UTC"))
        if (lastResetDate.get().isBefore(today)) {
            synchronized(this) {
                if (lastResetDate.get().isBefore(today)) {
                    logger.info(
                        "New day detected (UTC). Resetting Alphavantage API call count from ${callsToday.get()} to 0.",
                    )
                    callsToday.set(0)
                    lastResetDate.set(today)
                }
            }
        }
    }

    fun getCallsMadeToday(): Int = callsToday.get()

    fun getQuotaLimit(): Int = MAX_CALLS_PER_DAY
}

@Service
class AlphavantageDataIngestor(
    private val avroKafkaTemplate: KafkaTemplate<String, Any>,
    private val appConfig: AppConfig,
    private val quotaService: AlphavantageApiQuotaService,
) {
    private val logger = LoggerFactory.getLogger(AlphavantageDataIngestor::class.java)
    private val webClient =
        WebClient
            .builder()
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .build()

    private val stockList: List<String> by lazy {
        appConfig.stocks
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private val dailyStockFetchIndex = AtomicInteger(0)
    private val intradayStockFetchIndex = AtomicInteger(0)

    private val easternZoneId = ZoneId.of("US/Eastern")
    private val ymdHmsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(easternZoneId)
    private val ymdFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(easternZoneId)
    private val alphavantageApiKey: String? by lazy { appConfig.alphavantage.api.key }

    @PostConstruct
    fun init() {
        logger.info("=== Alphavantage Data Ingestor Initialized ===")
        logger.info("Collection enabled: {}", appConfig.collection.enabled)
        logger.info("Monitoring {} stocks via Alphavantage for historical candles: {}", stockList.size, stockList)
        logger.info("Alphavantage API Key Configured: {}", !alphavantageApiKey.isNullOrBlank())
        if (alphavantageApiKey.isNullOrBlank()) {
            logger.warn("ALPHAVANTAGE_API_KEY is not set. Alphavantage candle data collection will be skipped.")
        }
    }

    @Scheduled(cron = "0 5 0 * * ?") // 12:05 AM UTC daily
    fun fetchHistoricalDataScheduled() {
        if (!appConfig.collection.enabled || alphavantageApiKey.isNullOrBlank() || stockList.isEmpty()) {
            logger.info("Alphavantage collection disabled, API key missing, or no stocks to monitor.")
            return
        }
        logger.info(
            "Starting daily Alphavantage historical data fetch. Calls made today before start: {}/{}",
            quotaService.getCallsMadeToday(),
            quotaService.getQuotaLimit(),
        )

        val dailyStocksToFetch = 2
        for (i in 0 until dailyStocksToFetch) {
            if (!quotaService.canMakeCall()) {
                logger.warn("Alphavantage quota reached. Stopping daily fetch.")
                break
            }
            val symbol = stockList[dailyStockFetchIndex.getAndIncrement() % stockList.size]
            fetchTimeSeriesDaily(symbol)
            try {
                Thread.sleep(15000)
            } catch (e: InterruptedException) {
                logger.warn("Alphavantage daily fetch sleep interrupted for $symbol")
                Thread.currentThread().interrupt()
            }
        }

        if (quotaService.canMakeCall()) {
            val symbolForIntraday = stockList[intradayStockFetchIndex.getAndIncrement() % stockList.size]
            fetchTimeSeriesIntraday(symbolForIntraday, "5min", outputSize = "full")
        } else {
            logger.warn("Alphavantage quota reached. Skipping intraday fetch.")
        }
        logger.info(
            "Finished daily Alphavantage data fetch. Calls made today: {}/{}",
            quotaService.getCallsMadeToday(),
            quotaService.getQuotaLimit(),
        )
    }

    private fun fetchTimeSeriesDaily(symbol: String) {
        logger.info("Fetching TIME_SERIES_DAILY for $symbol from Alphavantage")
        val apiUrl =
            UriComponentsBuilder
                .fromUriString("${appConfig.alphavantage.baseUrl}/query")
                .queryParam("function", "TIME_SERIES_DAILY")
                .queryParam("symbol", symbol)
                .queryParam("outputsize", "full")
                .queryParam("apikey", alphavantageApiKey)
                .queryParam("datatype", "json")
                .toUriString()

        webClient
            .get()
            .uri(apiUrl)
            .retrieve()
            .bodyToMono(AlphavantageDailyResponse::class.java)
            .doOnSubscribe { logger.debug("Subscribing to Alphavantage DAILY for {}", symbol) }
            .doOnSuccess { response ->
                if (handleAlphavantageApiResponseNotes(
                        symbol,
                        "DAILY",
                        response.note,
                        response.information,
                        response.errorMessage,
                    )
                ) {
                    quotaService.recordCall()
                    processDailyData(symbol, response)
                }
            }.doOnError { error -> handleAlphavantageError(symbol, "DAILY", error) }
            .subscribe()
    }

    private fun fetchTimeSeriesIntraday(
        symbol: String,
        interval: String,
        month: String? = null,
        outputSize: String = "compact",
    ) {
        logger.info("Fetching TIME_SERIES_INTRADAY for $symbol, interval $interval from Alphavantage")
        val apiUrlBuilder =
            UriComponentsBuilder
                .fromUriString("${appConfig.alphavantage.baseUrl}/query")
                .queryParam("function", "TIME_SERIES_INTRADAY")
                .queryParam("symbol", symbol)
                .queryParam("interval", interval)
                .queryParam("apikey", alphavantageApiKey)
                .queryParam("outputsize", outputSize)
                .queryParam("extended_hours", "true")
                .queryParam("datatype", "json")
        month?.let { apiUrlBuilder.queryParam("month", it) }
        webClient
            .get()
            .uri(apiUrlBuilder.toUriString())
            .retrieve()
            .bodyToMono(AlphavantageIntradayResponse::class.java)
            .doOnSubscribe { logger.debug("Subscribing to Alphavantage INTRADAY for {}", symbol) }
            .doOnSuccess { response ->
                if (handleAlphavantageApiResponseNotes(
                        symbol,
                        "INTRADAY ($interval)",
                        response.note,
                        response.information,
                        response.errorMessage,
                    )
                ) {
                    quotaService.recordCall()
                    processIntradayData(symbol, interval, response)
                }
            }.doOnError { error -> handleAlphavantageError(symbol, "INTRADAY ($interval)", error) }
            .subscribe()
    }

    private fun handleAlphavantageApiResponseNotes(
        symbol: String,
        type: String,
        note: String?,
        info: String?,
        errorMsg: String?,
    ): Boolean {
        var isDataExpected = true
        if (note != null) {
            logger.warn("Alphavantage API Note for $symbol ($type): $note")
            if (note.contains("API call frequency") || note.contains("Premium")) {
                isDataExpected = false
            }
        }
        if (info != null) {
            logger.info("Alphavantage API Information for $symbol ($type): $info")
            if (info.contains("demo API key") || info.contains("Thank you")) {
                isDataExpected = false
            }
        }
        if (errorMsg != null) {
            logger.error("Alphavantage API Error Message for $symbol ($type): $errorMsg")
            isDataExpected = false
        }
        if (!isDataExpected) {
            logger.warn("Not recording API call for $symbol ($type) due to API note/info/error indicating no data.")
        }
        return isDataExpected
    }

    private fun processDailyData(
        symbol: String,
        response: AlphavantageDailyResponse,
    ) {
        val timeZone = response.metaData?.timeZoneDaily ?: "US/Eastern"
        val dailyZoneId =
            try {
                ZoneId.of(timeZone)
            } catch (
                e: Exception,
            ) {
                logger.warn("Invalid timezone '$timeZone' for $symbol daily, defaulting to US/Eastern.")
                easternZoneId
            }

        response.timeSeriesDaily?.forEach { (dateStr, data) ->
            try {
                val localDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val zonedDateTime = localDate.atStartOfDay(dailyZoneId)
                val timestamp = zonedDateTime.toInstant()

                publishCandle(symbol, data, timestamp, "DAILY")
            } catch (e: Exception) {
                logger.error("Error processing Alphavantage daily candle for $symbol on $dateStr: ${e.message}", e)
            }
        }
        logger.info("Processed ${response.timeSeriesDaily?.size ?: 0} Alphavantage daily candles for $symbol.")
    }

    private fun processIntradayData(
        symbol: String,
        interval: String,
        response: AlphavantageIntradayResponse,
    ) {
        val timeZone = response.metaData?.timeZoneIntraday ?: "US/Eastern"
        val intradayZoneId =
            try {
                ZoneId.of(timeZone)
            } catch (
                e: Exception,
            ) {
                logger.warn("Invalid timezone '$timeZone' for $symbol intraday, defaulting to US/Eastern.")
                easternZoneId
            }

        val dateTimeParser = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(intradayZoneId)

        val timeSeries = response.getTimeSeriesForInterval(interval)
        timeSeries?.forEach { (dateTimeStr, data) ->
            try {
                val timestamp = ZonedDateTime.parse(dateTimeStr, dateTimeParser).toInstant()
                publishCandle(symbol, data, timestamp, "INTRADAY_$interval")
            } catch (e: Exception) {
                logger.error(
                    "Error processing Alphavantage intraday candle for $symbol at $dateTimeStr ($interval): ${e.message}",
                    e,
                )
            }
        }
        logger.info("Processed ${timeSeries?.size ?: 0} Alphavantage intraday ($interval) candles for $symbol.")
    }

    private fun publishCandle(
        symbol: String,
        data: AlphavantageCandleData,
        timestamp: Instant,
        type: String,
    ) {
        try {
            val open = data.open?.toDoubleOrNull() ?: 0.0
            val high = data.high?.toDoubleOrNull() ?: 0.0
            val low = data.low?.toDoubleOrNull() ?: 0.0
            val close = data.close?.toDoubleOrNull() ?: 0.0
            val volume = data.volume?.toLongOrNull() ?: 0L

            if (symbol.isBlank() || !isValidPriceData(open, high, low, close)) {
                logger.warn(
                    "Invalid Alphavantage candle data ($type) for {} at {}: O={}, H={}, L={}, C={}",
                    symbol,
                    timestamp,
                    open,
                    high,
                    low,
                    close,
                )
                return
            }

            val stockCandle =
                StockCandle
                    .newBuilder()
                    .setSymbol(Utf8(symbol))
                    .setOpen(open)
                    .setHigh(high)
                    .setLow(low)
                    .setClose(close)
                    .setVolume(volume)
                    .setTimestamp(Instant.ofEpochMilli(timestamp.toEpochMilli()))
                    .build()

            publishToKafka("finnhub-ohlcv-data", symbol, stockCandle)
            logger.debug(
                "Published Alphavantage candle ($type) for {} at {}: C={}",
                symbol,
                timestamp,
                close,
            )
        } catch (e: Exception) {
            logger.error(
                "Error building or publishing Alphavantage candle ($type) for $symbol at $timestamp: ${e.message}",
                e,
            )
        }
    }

    private fun isValidPriceData(
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): Boolean {
        return open > 0 &&
            high > 0 &&
            low > 0 &&
            close > 0 &&
            high >= low &&
            high >= open &&
            high >= close &&
            low <= open &&
            low <= close // &&
        // open < 1_000_000 && high < 1_000_000 && low < 1_000_000 && close < 1_000_000
    }

    private fun handleAlphavantageError(
        symbol: String,
        type: String,
        error: Throwable,
    ) {
        when (error) {
            is WebClientResponseException -> {
                logger.error(
                    "Alphavantage API error for $symbol ($type): ${error.statusCode} - ${error.message}. Body: ${error.responseBodyAsString}",
                    error,
                )
            }
            else -> logger.error("Error fetching Alphavantage $type data for $symbol: ${error.message}", error)
        }
    }

    private fun publishToKafka(
        topic: String,
        key: String,
        message: Any,
    ) {
        try {
            val future = avroKafkaTemplate.send(topic, key, message)
            future.whenComplete { _, ex ->
                if (ex !=
                    null
                ) {
                    logger.error("Failed to send Alphavantage data to Kafka [{}]: {} - {}", topic, key, ex.message)
                } else {
                    logger.debug("Sent Alphavantage data to Kafka [{}]: {}", topic, key)
                }
            }
        } catch (e: KafkaException) {
            logger.error("Kafka error for Alphavantage data {} on topic {}: {}", key, topic, e.message)
        }
    }
}

@Service
class MetricsService(
    private val finnhubIngestor: FinnhubDataIngestor,
    private val fredIngestor: FREDDataIngestor,
    private val alphavantageApiQuotaService: AlphavantageApiQuotaService,
) {
    private val logger = LoggerFactory.getLogger(MetricsService::class.java)

    @Scheduled(fixedRate = 60000) // Every minute
    fun logMetrics() {
        logServiceMetrics("FinnhubQuotes", finnhubIngestor.metrics, "finnhub_quote")
        logServiceMetrics("FinnhubNews", finnhubIngestor.metrics, "finnhub_news")
        logServiceMetrics("FRED", fredIngestor.metrics, "fred")

        logger.info(
            "Alphavantage API calls today: {}/{}",
            alphavantageApiQuotaService.getCallsMadeToday(),
            alphavantageApiQuotaService.getQuotaLimit(),
        )
    }

    private fun logServiceMetrics(
        serviceName: String,
        metrics: ServiceMetrics,
        quotaKey: String,
    ) {
        val successful = metrics.successfulRequests.get()
        val failed = metrics.failedRequests.get()
        val total = successful + failed
        val successRate = if (total > 0) (successful * 100.0 / total) else 0.0
        val lastSuccess = metrics.lastSuccessfulCollection.get()
        val timeSinceLastSuccess = if (lastSuccess > 0) Instant.now().epochSecond - lastSuccess else -1
        val apiCalls = metrics.apiQuotaUsage[quotaKey]?.get() ?: 0L

        logger.info(
            "{} Metrics - Success: {}, Failed: {}, Rate: {:.1f}%, Last: {}s ago, API Calls: {}",
            serviceName,
            successful,
            failed,
            successRate,
            timeSinceLastSuccess,
            apiCalls,
        )
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    fun resetHourlyMetrics() {
        finnhubIngestor.metrics.apiQuotaUsage.forEach { (_, counter) -> counter.set(0) }
        fredIngestor.metrics.apiQuotaUsage.forEach { (_, counter) -> counter.set(0) }
        logger.info("Hourly API usage counts reset for Finnhub & FRED.")
    }
}
