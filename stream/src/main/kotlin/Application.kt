package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import reactor.core.publisher.Mono
import java.time.Instant

@SpringBootApplication @EnableScheduling
class Application

fun main(args: Array<String>) {
    println("Starting Data Stream Collector...")
    runApplication<Application>(*args)
    println("Data Stream Collector has started.")
}

// Data Models
@JsonIgnoreProperties(ignoreUnknown = true)
data class YahooFinanceQuote(
    val symbol: String,
    val regularMarketPrice: Double,
    val regularMarketVolume: Long,
    val regularMarketChangePercent: Double,
    val regularMarketDayHigh: Double,
    val regularMarketDayLow: Double,
    val timestamp: Long = Instant.now().epochSecond,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YahooFinanceResponse(
    val quoteResponse: QuoteResponse,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuoteResponse(
    val result: List<YahooFinanceQuote>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageTimeSeriesDaily(
    @JsonProperty("Meta Data") val metaData: Map<String, String>,
    @JsonProperty("Time Series (Daily)") val timeSeries: Map<String, Map<String, String>>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EconomicIndicator(
    val indicator: String,
    val value: Double,
    val date: String,
    val timestamp: Long = Instant.now().epochSecond,
)

// Kafka message wrapper
data class StockMessage(
    val messageType: String, // "stock_quote", "economic_indicator", etc.
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

    @Value("\${app.alphavantage.api.key:#{null}}")
    private val alphaVantageApiKey: String? = null

    @Value("\${app.fred.api.key:#{null}}")
    private val fredApiKey: String? = null

    private val stockList: List<String> by lazy { stockSymbols.split(",").map { it.trim() } }

    @Value("\${app.yahoo.baseUrl:https://query1.finance.yahoo.com}")
    private lateinit var yahooBaseUrl: String

    @Value("\${app.fred.baseUrl:https://api.stlouisfed.org}")
    private lateinit var fredBaseUrl: String

    @Value("\${app.alphavantage.baseUrl:https://www.alphavantage.co}")
    private lateinit var alphaVantageBaseUrl: String

    @PostConstruct
    fun init() {
        logger.info("Data Collection Service initialized")
        logger.info("Monitoring stocks: {}", stockList)
        logger.info("Alpha Vantage API configured: {}", alphaVantageApiKey != null)
        logger.info("FRED API configured: {}", fredApiKey != null)
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    fun collectStockData() {
        logger.info("Starting stock data collection cycle...")

        val symbolsQuery = stockList.joinToString(",")
        val yahooUrl = "$yahooBaseUrl/v8/finance/chart/$symbolsQuery"

        webClient
            .get()
            .uri(yahooUrl)
            .retrieve()
            .bodyToMono(YahooFinanceResponse::class.java)
            .doOnSuccess { response ->
                response.quoteResponse.result.forEach { quote ->
                    try {
                        val message = StockMessage("stock_quote", quote)
                        val messageJson = objectMapper.writeValueAsString(message)

                        kafkaTemplate.send("stock_prices", quote.symbol, messageJson)
                        logger.debug(
                            "Sent stock data for {}: ${quote.regularMarketPrice}",
                            quote.symbol,
                        )
                    } catch (e: JsonProcessingException) {
                        logger.error("JSON serialization error for {}: {}", quote.symbol, e.message)
                    } catch (e: KafkaException) {
                        logger.error("Kafka error sending data for {}: {}", quote.symbol, e.message)
                    } catch (e: IllegalArgumentException) {
                        logger.error("Invalid data for {}: {}", quote.symbol, e.message)
                    } catch (e: RuntimeException) {
                        logger.error("Unexpected error for {}: {}", quote.symbol, e.message)
                    }
                }
                logger.info(
                    "Successfully collected data for {} stocks",
                    response.quoteResponse.result.size,
                )
            }
            .doOnError { error ->
                logger.error("Error fetching Yahoo Finance data: {}", error.message)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun collectMarketVolatility() {
        logger.info("Collecting market volatility indicators...")

        val vixUrl = "$yahooBaseUrl/v8/finance/chart/%5EVIX"

        webClient
            .get()
            .uri(vixUrl)
            .retrieve()
            .bodyToMono(YahooFinanceResponse::class.java)
            .doOnSuccess { response ->
                response.quoteResponse.result.forEach { vixData ->
                    try {
                        val indicator = EconomicIndicator(
                            indicator = "VIX",
                            value = vixData.regularMarketPrice,
                            date = Instant.now().toString(),
                        )
                        val message = StockMessage("volatility_indicator", indicator)
                        val messageJson = objectMapper.writeValueAsString(message)

                        kafkaTemplate.send("economic_indicators", "VIX", messageJson)
                        logger.info("Sent VIX data: {}", vixData.regularMarketPrice)
                    } catch (e: JsonProcessingException) {
                        logger.error("JSON serialization error for VIX data: {}", e.message)
                    } catch (e: KafkaException) {
                        logger.error("Kafka error sending VIX data: {}", e.message)
                    } catch (e: IllegalArgumentException) {
                        logger.error("Invalid VIX data: {}", e.message)
                    } catch (e: RuntimeException) {
                        logger.error("Unexpected error processing VIX data: {}", e.message)
                    }
                }
            }
            .doOnError { error ->
                logger.error("Error fetching VIX data: {}", error.message)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    @Scheduled(fixedRate = 900000) // Every 15 minutes
    fun collectEconomicIndicators() {
        if (fredApiKey == null) {
            logger.debug("FRED API key not configured, skipping economic indicators collection")
            return
        }

        logger.info("Collecting economic indicators from FRED...")

        // Get effective federal funds rate
        val fredPathAndQuery = "/fred/series/observations?series_id=FEDFUNDS&api_key=$fredApiKey&file_type=json&limit=1&sort_order=desc"
        val fredUrl = "$fredBaseUrl$fredPathAndQuery"

        webClient
            .get()
            .uri(fredUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSuccess { response ->
                try {
                    logger.info("Received FRED data: {}", response.take(100) + "...")

                    // Parse the response JSON to extract the federal funds rate
                    val rootNode = objectMapper.readTree(response)
                    val observations = rootNode["observations"]
                    val latest = observations?.firstOrNull()
                    val rate = latest?.get("value")?.asText()?.toDoubleOrNull()

                    if (rate == null) {
                        logger.warn("Could not parse federal funds rate from FRED response")
                        return@doOnSuccess
                    }

                    val indicator = EconomicIndicator(
                        indicator = "FEDERAL_FUNDS_RATE",
                        value = rate,
                        date = Instant.now().toString(),
                    )

                    val message = StockMessage("economic_indicator", indicator)
                    val messageJson = objectMapper.writeValueAsString(message)

                    kafkaTemplate.send("economic_indicators", "FEDFUNDS", messageJson)
                    logger.info("Sent FEDFUNDS rate to Kafka: {}", rate)

                } catch (e: JsonProcessingException) {
                    logger.error("JSON parsing error in FRED response: {}", e.message)
                } catch (e: KafkaException) {
                    logger.error("Kafka error sending FRED data: {}", e.message)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid FRED data: {}", e.message)
                } catch (e: RuntimeException) {
                    logger.error("Unexpected error processing FRED data: {}", e.message)
                }
            }
            .doOnError { error ->
                logger.error("Error fetching FRED data: {}", error.message)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }


   @Scheduled(fixedRate = 600000) // Every 10 minutes
    fun collectAlphaVantageData() {
        if (alphaVantageApiKey == null) {
            logger.debug("Alpha Vantage API key not configured, skipping")
            return
        }

        logger.info("Collecting intraday data from Alpha Vantage...")

        val symbol = "SPY"
        val alphaVantagePathAndQuery = "/query?function=TIME_SERIES_INTRADAY&symbol=$symbol&interval=5min&apikey=$alphaVantageApiKey"
        val alphaUrl = "$alphaVantageBaseUrl$alphaVantagePathAndQuery"

        webClient
            .get()
            .uri(alphaUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSuccess { response ->
                try {
                    logger.info(
                        "Received Alpha Vantage data for {}: {}",
                        symbol,
                        response.take(100) + "...",
                    )

                    // Send raw data to processing
                    val message = StockMessage(
                        "intraday_data",
                        mapOf("symbol" to symbol, "data" to response),
                    )
                    val messageJson = objectMapper.writeValueAsString(message)

                    kafkaTemplate.send("intraday_data", symbol, messageJson)
                    logger.info("Sent Alpha Vantage intraday data for symbol: {}", symbol)
                } catch (e: JsonProcessingException) {
                    logger.error("JSON serialization error for Alpha Vantage data ({}): {}", symbol, e.message)
                } catch (e: KafkaException) {
                    logger.error("Kafka error sending Alpha Vantage data ({}): {}", symbol, e.message)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid Alpha Vantage message for symbol {}: {}", symbol, e.message)
                } catch (e: RuntimeException) {
                    logger.error("Unexpected error processing Alpha Vantage data ({}): {}", symbol, e.message)
                }
            }
            .doOnError { error ->
                logger.error("Error fetching Alpha Vantage data for {}: {}", symbol, error.message)
            }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }
}
