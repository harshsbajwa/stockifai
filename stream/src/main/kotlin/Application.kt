package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
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
        val yahooUrl = "https://query1.finance.yahoo.com/v8/finance/chart/$symbolsQuery"

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
                    } catch (e: Exception) {
                        logger.error(
                            "Error processing stock data for {}: {}",
                            quote.symbol,
                            e.message,
                        )
                    }
                }
                logger.info(
                    "Successfully collected data for {} stocks",
                    response.quoteResponse.result.size,
                )
            }.doOnError { error ->
                logger.error("Error fetching Yahoo Finance data: {}", error.message)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun collectMarketVolatility() {
        logger.info("Collecting market volatility indicators...")

        // VIX data from Yahoo Finance
        val vixUrl = "https://query1.finance.yahoo.com/v8/finance/chart/%5EVIX"

        webClient
            .get()
            .uri(vixUrl)
            .retrieve()
            .bodyToMono(YahooFinanceResponse::class.java)
            .doOnSuccess { response ->
                response.quoteResponse.result.forEach { vixData ->
                    try {
                        val indicator =
                            EconomicIndicator(
                                indicator = "VIX",
                                value = vixData.regularMarketPrice,
                                date = Instant.now().toString(),
                            )
                        val message = StockMessage("volatility_indicator", indicator)
                        val messageJson = objectMapper.writeValueAsString(message)

                        kafkaTemplate.send("economic_indicators", "VIX", messageJson)
                        logger.info("Sent VIX data: {}", vixData.regularMarketPrice)
                    } catch (e: Exception) {
                        logger.error("Error processing VIX data: {}", e.message)
                    }
                }
            }.doOnError { error -> logger.error("Error fetching VIX data: {}", error.message) }
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
        val fredUrl =
            "https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=$fredApiKey&file_type=json&limit=1&sort_order=desc"

        webClient
            .get()
            .uri(fredUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSuccess { response ->
                try {
                    // Parse FRED response and send to Kafka
                    logger.info("Received FRED data: {}", response.take(100) + "...")

                    val indicator =
                        EconomicIndicator(
                            indicator = "FEDERAL_FUNDS_RATE",
                            value = 0.0, // Parse from actual response
                            date = Instant.now().toString(),
                        )

                    val message = StockMessage("economic_indicator", indicator)
                    val messageJson = objectMapper.writeValueAsString(message)

                    kafkaTemplate.send("economic_indicators", "FEDFUNDS", messageJson)
                } catch (e: Exception) {
                    logger.error("Error processing FRED data: {}", e.message)
                }
            }.doOnError { error -> logger.error("Error fetching FRED data: {}", error.message) }
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

        // Get intraday data for a key stock (e.g., SPY - S&P 500 ETF)
        val symbol = "SPY"
        val alphaUrl =
            "https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=$symbol&interval=5min&apikey=$alphaVantageApiKey"

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
                    val message =
                        StockMessage(
                            "intraday_data",
                            mapOf("symbol" to symbol, "data" to response),
                        )
                    val messageJson = objectMapper.writeValueAsString(message)

                    kafkaTemplate.send("intraday_data", symbol, messageJson)
                } catch (e: Exception) {
                    logger.error("Error processing Alpha Vantage data: {}", e.message)
                }
            }.doOnError { error ->
                logger.error("Error fetching Alpha Vantage data: {}", error.message)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }
}
