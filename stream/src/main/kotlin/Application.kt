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
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootApplication @EnableScheduling
class Application

fun main(args: Array<String>) {
    println("Starting Data Stream Collector...")
    runApplication<Application>(*args)
    println("Data Stream Collector has started.")
}

// Data Models
@JsonIgnoreProperties(ignoreUnknown = true)
data class StockQuoteData(
    val symbol: String,
    val regularMarketPrice: Double?,
    val regularMarketVolume: Long?, // Volume is not available from Finnhub /quote, will be null
    val regularMarketChangePercent: Double?, // From Finnhub 'dp'
    val regularMarketDayHigh: Double?, // From Finnhub 'h'
    val regularMarketDayLow: Double?, // From Finnhub 'l'
    val regularMarketOpen: Double?, // From Finnhub 'o'
    val previousClosePrice: Double?, // From Finnhub 'pc'
    val timestamp: Long // From Finnhub 't' (epoch seconds)
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
    @JsonProperty("o") val o: List<Double>? = null,
    @JsonProperty("h") val h: List<Double>? = null,
    @JsonProperty("l") val l: List<Double>? = null,
    @JsonProperty("c") val c: List<Double>? = null,
    @JsonProperty("v") val v: List<Long>? = null,
    @JsonProperty("t") val t: List<Long>? = null,
    @JsonProperty("s") val s: String? = null, // Status ok or no_data
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EconomicIndicator(
    val indicator: String,
    val value: Double,
    val date: String,
    val timestamp: Long = Instant.now().epochSecond,
)

// Data class for individual FRED observation
@JsonIgnoreProperties(ignoreUnknown = true)
data class FredObservation(
    val date: String? = null,
    val value: String? = null // FRED values can be "." for missing, so keep as String initially
)

// Data class for the root FRED response
@JsonIgnoreProperties(ignoreUnknown = true)
data class FredSeriesResponse(
    val observations: List<FredObservation>? = null
)

// Kafka message wrapper
data class StockMessage(
    val messageType: String,
    val data: Any,
    val timestamp: Long = Instant.now().epochSecond,
)

@Service
class DataCollectionService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(DataCollectionService::class.java)
    private val webClient = WebClient.builder().build()

    @Value("\${app.stocks:AAPL,GOOGL,MSFT,TSLA,AMZN,NVDA,META,BRK-A,JNJ,V}")
    private lateinit var stockSymbols: String

    @Value("\${app.finnhub.api.key:#{null}}")
    private val finnhubApiKey: String? = null

    @Value("\${app.fred.api.key:#{null}}")
    private val fredApiKey: String? = null

    private val stockList: List<String> by lazy { stockSymbols.split(",").map { it.trim() } }

    @Value("\${app.finnhub.baseUrl:https://finnhub.io/api/v1}")
    private lateinit var finnhubBaseUrl: String

    @Value("\${app.fred.baseUrl:https://api.stlouisfed.org}")
    private lateinit var fredBaseUrl: String

    @PostConstruct
    fun init() {
        logger.info("Data Collection Service initialized")
        logger.info("Monitoring stocks: {}", stockList)
        logger.info("FRED API configured: {}", fredApiKey != null)
        logger.info("Finnhub API configured: {}", finnhubApiKey != null)
        if (finnhubApiKey == null) {
            logger.warn("FINNHUB_API_KEY is not set. Stock and VIX data collection will be skipped.")
        }
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    fun collectStockData() {
        if (finnhubApiKey == null) {
            logger.debug("Finnhub API key not set, skipping stock data collection.")
            return
        }
        logger.info("Starting stock data collection cycle using Finnhub...")

        stockList.forEach { symbol ->
            val finnhubUrl =
                UriComponentsBuilder
                    .fromUriString("$finnhubBaseUrl/quote")
                    .queryParam("symbol", symbol)
                    .queryParam("token", finnhubApiKey)
                    .toUriString()

            webClient
                .get()
                .uri(finnhubUrl)
                .retrieve()
                .bodyToMono(FinnhubQuote::class.java)
                .doOnSuccess { finnhubQuote ->
                    if (finnhubQuote.currentPrice == null && finnhubQuote.timestamp == null) {
                        // Finnhub often returns an empty object {} for invalid symbols or issues,
                        // rather than an HTTP error. currentPrice is a good indicator.
                        logger.warn("Received empty or invalid quote data for symbol {}: {}", symbol, finnhubQuote)
                        return@doOnSuccess
                    }
                    val stockQuote =
                        StockQuoteData(
                            symbol = symbol,
                            regularMarketPrice = finnhubQuote.currentPrice,
                            regularMarketVolume = null, // Finnhub /quote does not provide volume
                            regularMarketChangePercent = finnhubQuote.percentChange,
                            regularMarketDayHigh = finnhubQuote.highPriceOfDay,
                            regularMarketDayLow = finnhubQuote.lowPriceOfDay,
                            regularMarketOpen = finnhubQuote.openPriceOfDay,
                            previousClosePrice = finnhubQuote.previousClosePrice,
                            timestamp = finnhubQuote.timestamp ?: Instant.now().epochSecond,
                        )
                    try {
                        val message = StockMessage("stock_quote", stockQuote)
                        val messageJson = objectMapper.writeValueAsString(message)
                        kafkaTemplate.send("stock_prices", symbol, messageJson)
                        logger.debug(
                            "Sent stock data for {}: {}",
                            symbol,
                            stockQuote.regularMarketPrice,
                        )
                    } catch (e: JsonProcessingException) {
                        logger.error("JSON serialization error for {}: {}", symbol, e.message)
                    } catch (e: KafkaException) {
                        logger.error("Kafka error sending data for {}: {}", symbol, e.message)
                    } catch (e: IllegalArgumentException) {
                        logger.error("Invalid data for {}: {}", symbol, e.message)
                    } catch (e: RuntimeException) {
                        logger.error("Unexpected error for {}: {}", symbol, e.message)
                    }
                }.doOnError { error ->
                    logger.error("Error fetching Finnhub quote for {}: {}", symbol, error.message)
                }.onErrorResume { Mono.empty() }
                .subscribe()
        }
        // No overall success log here as calls are per-symbol
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes (or adjust as needed for VIX)
fun collectMarketVolatility() {
    if (fredApiKey == null) {
        logger.warn("FRED API key not configured, skipping VIXCLS data collection.")
        return
    }
    logger.info("Collecting VIXCLS data using FRED...")

    val seriesId = "VIXCLS"

    webClient
        .get()
        .uri(
            "$fredBaseUrl/fred/series/observations?series_id={seriesId}&api_key={apiKey}&file_type=json&limit=1&sort_order=desc",
            mapOf(
                "seriesId" to seriesId,
                "apiKey" to fredApiKey,
            )
        )
        .retrieve()
        .bodyToMono(String::class.java)
        .doOnSuccess { responseString ->
            try {
                logger.debug("Received FRED VIXCLS response string: {}", responseString.take(300) + "...")
                val fredResponse = objectMapper.readValue(responseString, FredSeriesResponse::class.java)

                val latestObservation = fredResponse.observations?.firstOrNull()
                if (latestObservation?.value == null || latestObservation.value == "." || latestObservation.date == null) {
                    logger.warn("No valid VIXCLS observation found in FRED response or value is '.'. Response: {}", responseString.take(500))
                    return@doOnSuccess
                }

                val vixValue = latestObservation.value.toDoubleOrNull()
                if (vixValue == null) {
                    logger.warn("Could not parse VIXCLS value '{}' from FRED response", latestObservation.value)
                    return@doOnSuccess
                }

                val indicator =
                    EconomicIndicator(
                        indicator = seriesId, // "VIXCLS"
                        value = vixValue,
                        date = latestObservation.date, // Date from FRED
                        timestamp = Instant.now().epochSecond // Current processing time as timestamp
                    )
                val message = StockMessage("volatility_indicator", indicator)
                val messageJson = objectMapper.writeValueAsString(message)

                kafkaTemplate.send("economic_indicators", seriesId, messageJson)
                logger.info("Sent {} data from FRED: {} on {}", seriesId, vixValue, latestObservation.date)

            } catch (e: JsonProcessingException) {
                logger.error("JSON processing error for FRED {} data: {}. Response: {}", seriesId, e.message, responseString.take(500))
            } catch (e: KafkaException) {
                logger.error("Kafka error sending FRED {} data: {}", seriesId, e.message)
            } catch (e: NoSuchElementException) {
                 logger.warn("No observations array found or it's empty in FRED response for {}. Response: {}", seriesId, responseString.take(500))
            } catch (e: Exception) { // Catch-all for other unexpected errors
                logger.error("Unexpected error processing FRED {} data: {}", seriesId, e.message, e)
            }
        }.doOnError { error ->
            logger.error("Error fetching {} data from FRED: {}", seriesId, error.message)
        }.onErrorResume { Mono.empty() }
        .subscribe()
    }

    @Scheduled(fixedRate = 900000) // Every 15 minutes
    fun collectEconomicIndicators() {
        if (fredApiKey == null) {
            logger.debug("FRED API key not configured, skipping economic indicators collection.")
            return
        }

        logger.info("Collecting economic indicators from FRED...")
        val fredPathAndQuery = "/fred/series/observations?series_id=FEDFUNDS&api_key=$fredApiKey&file_type=json&limit=1&sort_order=desc"
        val fredUrl = "$fredBaseUrl$fredPathAndQuery"

        webClient
            .get()
            .uri(fredUrl)
            .retrieve()
            .bodyToMono(String::class.java) // Process as string first for robust parsing
            .doOnSuccess { responseString ->
                try {
                    logger.debug("Received FRED data string: {}", responseString.take(200) + "...")

                    val rootNode = objectMapper.readTree(responseString)
                    val observations = rootNode.path("observations")
                    if (observations.isMissingNode || !observations.isArray || observations.isEmpty) {
                        logger.warn("No observations found in FRED response for FEDFUNDS.")
                        return@doOnSuccess
                    }
                    val latestObservation = observations.first()
                    val rateValue = latestObservation.path("value").asText()
                    val dateValue = latestObservation.path("date").asText()

                    if (rateValue == "." || rateValue.isBlank()) { // FRED uses "." for unavailable data
                        logger.warn("Federal funds rate value is unavailable in FRED response: {}", latestObservation)
                        return@doOnSuccess
                    }
                    val rate = rateValue.toDoubleOrNull()
                    if (rate == null) {
                        logger.warn("Could not parse federal funds rate value '{}' from FRED response", rateValue)
                        return@doOnSuccess
                    }

                    val indicator =
                        EconomicIndicator(
                            indicator = "FEDERAL_FUNDS_RATE",
                            value = rate,
                            date = dateValue,
                        )

                    val message = StockMessage("economic_indicator", indicator)
                    val messageJson = objectMapper.writeValueAsString(message)

                    kafkaTemplate.send("economic_indicators", "FEDFUNDS", messageJson)
                    logger.info("Sent FEDFUNDS rate to Kafka: {} on {}", rate, dateValue)
                } catch (e: JsonProcessingException) {
                    logger.error("JSON parsing error in FRED response: {}. Response: {}", e.message, responseString.take(500))
                } catch (e: KafkaException) {
                    logger.error("Kafka error sending FRED data: {}", e.message)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid FRED data: {}", e.message)
                } catch (e: RuntimeException) {
                    logger.error("Unexpected error processing FRED data: {}", e.message)
                }
            }.doOnError { error ->
                logger.error("Error fetching FRED data: {}", error.message)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    fun collectFinnhubIntradayCandleData() {
        if (finnhubApiKey == null) {
            logger.debug("Finnhub API key not set, skipping intraday candle data collection.")
            return
        }

        val symbol = "SPY" // Typically, intraday is monitored for a benchmark index/ETF
        logger.info("Collecting 5-min intraday candle data for {} from Finnhub...", symbol)

        val toTimestamp = Instant.now().epochSecond
        val fromTimestamp = Instant.now().minus(1, ChronoUnit.HOURS).epochSecond // Fetch last 1 hour of 5-min candles

        val finnhubUrl =
            UriComponentsBuilder
                .fromUriString("$finnhubBaseUrl/stock/candle")
                .queryParam("symbol", symbol)
                .queryParam("resolution", "5") // 5-minute candles
                .queryParam("from", fromTimestamp)
                .queryParam("to", toTimestamp)
                .queryParam("token", finnhubApiKey)
                .toUriString()
        
        webClient
            .get()
            .uri(finnhubUrl)
            .retrieve()
            .bodyToMono(String::class.java) // Fetch as string to send raw-ish data
            .doOnSuccess { responseString ->
                try {
                    // Finnhub returns {"s":"no_data"} if no candles are available.
                    // Or potentially an empty object for bad symbols, or actual candles.
                    // We can attempt to parse to check for no_data status
                    val candles = objectMapper.readValue(responseString, FinnhubStockCandles::class.java)
                    if (candles.s == "no_data" || (candles.c.isNullOrEmpty() && candles.t.isNullOrEmpty())) {
                        logger.info("No intraday candle data available for {} in the requested window.", symbol)
                        return@doOnSuccess
                    }
                    
                    logger.debug(
                        "Received Finnhub 5-min candles for {}: {}",
                        symbol,
                        responseString.take(200) + "...",
                    )

                    val message =
                        StockMessage(
                            "intraday_data",
                            mapOf("symbol" to symbol, "data" to responseString), // Send the JSON string
                        )
                    val messageJson = objectMapper.writeValueAsString(message)

                    kafkaTemplate.send("intraday_data", symbol, messageJson)
                    logger.info("Sent Finnhub 5-min candle data for symbol: {}", symbol)
                } catch (e: JsonProcessingException) {
                    logger.error("JSON processing error for Finnhub candles ({}): {}. Response: {}", symbol, e.message, responseString.take(500))
                } catch (e: KafkaException) {
                    logger.error("Kafka error sending Finnhub candles ({}): {}", symbol, e.message)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid Finnhub candle message for symbol {}: {}", symbol, e.message)
                } catch (e: RuntimeException) {
                    logger.error("Unexpected error processing Finnhub candles ({}): {}", symbol, e.message)
                }
            }.doOnError { error ->
                logger.error("Error fetching Finnhub 5-min candles for {}: {}", symbol, error.message)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }
}
