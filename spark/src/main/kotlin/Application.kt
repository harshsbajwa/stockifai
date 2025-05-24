package com.harshsbajwa.stockifai.spark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaRDD
import org.apache.spark.streaming.Durations
import org.apache.spark.streaming.api.java.JavaInputDStream
import org.apache.spark.streaming.api.java.JavaStreamingContext
import org.apache.spark.streaming.kafka010.*
import org.slf4j.LoggerFactory

data class StockData(
        val symbol: String,
        val price: Double,
        val volume: Long,
        val timestamp: Long,
        val open: Double? = null,
        val high: Double? = null,
        val low: Double? = null,
        val close: Double? = null
)

data class ProcessedStockData(
        val symbol: String,
        val currentPrice: Double,
        val volume: Long,
        val timestamp: Long,
        val volatility: Double,
        val priceChange: Double,
        val priceChangePercent: Double,
        val volumeAverage: Double,
        val riskScore: Double,
        val trend: String,
        val support: Double?,
        val resistance: Double?
)

data class EconomicIndicator(
        val indicator: String,
        val value: Double,
        val timestamp: Long,
        val country: String? = null
)

data class StockMessage(val messageType: String, val data: Map<String, Any>, val timestamp: Long)

data class RiskMetrics(
        val var95: Double, // 95% Value at Risk
        val volatility: Double,
        val sharpeRatio: Double,
        val maxDrawdown: Double,
        val beta: Double
)

object SparkProcessorApplication {
    private val logger = LoggerFactory.getLogger(SparkProcessorApplication::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Thread-safe in-memory storage for calculations
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()
    private val volumeHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val timestampHistory = ConcurrentHashMap<String, MutableList<Long>>()

    // Market data for beta calculation (use SPY as market benchmark)
    private val marketPrices = mutableListOf<Double>()

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Initializing StockifAI Spark Processor Application...")

        try {
            val sparkConf = createSparkConfiguration()
            val jssc = createStreamingContext(sparkConf)
            val stream = createKafkaStream(jssc)

            processStream(stream)

            startStreamingContext(jssc)
        } catch (e: Exception) {
            logger.error("Failed to start Spark Processor Application", e)
            System.exit(1)
        }
    }

    private fun createSparkConfiguration(): SparkConf {
        return SparkConf()
                .setAppName("StockifAI-SparkProcessor")
                .set("spark.sql.adaptive.enabled", "true")
                .set("spark.sql.adaptive.coalescePartitions.enabled", "true")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
                .set("spark.streaming.receiver.writeAheadLog.enable", "true")
                .set("spark.streaming.kafka.consumer.poll.ms", "512")
                .set("spark.streaming.backpressure.enabled", "true")
                .apply {
                    if (!contains("spark.master")) {
                        setMaster("local[*]")
                    }
                }
    }

    private fun createStreamingContext(sparkConf: SparkConf): JavaStreamingContext {
        val batchIntervalSeconds =
                System.getenv("SPARK_BATCH_INTERVAL_SECONDS")?.toLongOrNull() ?: 10L
        val jssc = JavaStreamingContext(sparkConf, Durations.seconds(batchIntervalSeconds))

        val checkpointDir = System.getenv("SPARK_CHECKPOINT_DIR") ?: "/tmp/spark-checkpoint"
        jssc.checkpoint(checkpointDir)
        logger.info("Spark checkpoint directory set to: $checkpointDir")

        return jssc
    }

    private fun createKafkaStream(
            jssc: JavaStreamingContext
    ): JavaInputDStream<ConsumerRecord<String, String>> {
        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "kafka:9093"
        val kafkaGroupId = System.getenv("KAFKA_CONSUMER_GROUP_ID") ?: "stock-spark-processor-group"
        val kafkaAutoOffsetReset = System.getenv("KAFKA_AUTO_OFFSET_RESET") ?: "latest"

        val kafkaParams =
                mapOf<String, Any>(
                        "bootstrap.servers" to kafkaBootstrapServers,
                        "key.deserializer" to StringDeserializer::class.java,
                        "value.deserializer" to StringDeserializer::class.java,
                        "group.id" to kafkaGroupId,
                        "auto.offset.reset" to kafkaAutoOffsetReset,
                        "enable.auto.commit" to true,
                        "auto.commit.interval.ms" to 1000,
                        "session.timeout.ms" to 30000,
                        "heartbeat.interval.ms" to 3000,
                        "max.poll.records" to 500,
                        "max.poll.interval.ms" to 300000,
                        "request.timeout.ms" to 40000,
                        "connections.max.idle.ms" to 540000
                )

        val topicsEnv = System.getenv("KAFKA_TOPICS") ?: "stock_prices,economic_indicators"
        val topics = topicsEnv.split(",").map { it.trim() }.toSet()
        logger.info("Kafka consumer configured - servers: $kafkaBootstrapServers, topics: $topics")

        return KafkaUtils.createDirectStream(
                jssc,
                LocationStrategies.PreferConsistent(),
                ConsumerStrategies.Subscribe<String, String>(topics, kafkaParams)
        )
    }

    private fun processStream(stream: JavaInputDStream<ConsumerRecord<String, String>>) {
        stream.foreachRDD { rdd, time ->
            if (!rdd.isEmpty) {
                logger.info("Processing batch for time: $time, Messages: ${rdd.count()}")

                try {
                    // Separate processing by topic
                    val stockPriceRecords = rdd.filter { it.topic() == "stock_prices" }
                    val economicIndicatorRecords =
                            rdd.filter { it.topic() == "economic_indicators" }

                    if (!stockPriceRecords.isEmpty) {
                        processStockPriceData(stockPriceRecords)
                    }

                    if (!economicIndicatorRecords.isEmpty) {
                        processEconomicIndicatorData(economicIndicatorRecords)
                    }

                    logger.info("Successfully processed batch for time: $time")
                } catch (e: Exception) {
                    logger.error("Error processing batch for time: $time", e)
                }
            }
        }
    }

    private fun processStockPriceData(rdd: JavaRDD<ConsumerRecord<String, String>>) {
        val processedData =
                rdd.mapPartitions { partition ->
                    val results = mutableListOf<ProcessedStockData>()

                    partition.forEach { record ->
                        try {
                            val stockData = parseStockData(record.value())
                            if (stockData != null) {
                                val processedStock = processStockData(stockData)
                                results.add(processedStock)
                                logger.debug(
                                        "Processed stock data for ${stockData.symbol}: volatility=${processedStock.volatility}, riskScore=${processedStock.riskScore}"
                                )
                            }
                        } catch (e: Exception) {
                            logger.warn("Failed to process stock record: ${record.value()}", e)
                        }
                    }

                    results.iterator()
                }

        storeProcessedStockData(processedData)
    }

    private fun processEconomicIndicatorData(rdd: JavaRDD<ConsumerRecord<String, String>>) {
        val processedData =
                rdd.mapPartitions { partition ->
                    val results = mutableListOf<EconomicIndicator>()

                    partition.forEach { record ->
                        try {
                            val indicator = parseEconomicIndicator(record.value())
                            if (indicator != null) {
                                results.add(indicator)
                                logger.debug(
                                        "Processed economic indicator: ${indicator.indicator} = ${indicator.value}"
                                )
                            }
                        } catch (e: Exception) {
                            logger.warn(
                                    "Failed to process economic indicator record: ${record.value()}",
                                    e
                            )
                        }
                    }

                    results.iterator()
                }

        storeEconomicIndicatorData(processedData)
    }

    private fun parseStockData(json: String): StockData? {
        return try {
            // Handle both wrapped and direct stock data formats
            val data =
                    if (json.contains("messageType")) {
                        val message = objectMapper.readValue<StockMessage>(json)
                        message.data
                    } else {
                        objectMapper.readValue<Map<String, Any>>(json)
                    }

            StockData(
                    symbol = data["symbol"] as String,
                    price = ((data["regularMarketPrice"] ?: data["price"]) as Number).toDouble(),
                    volume = ((data["regularMarketVolume"] ?: data["volume"]) as Number).toLong(),
                    timestamp = (data["timestamp"] as? Number)?.toLong()
                                    ?: System.currentTimeMillis(),
                    high = ((data["regularMarketDayHigh"] ?: data["high"]) as? Number)?.toDouble(),
                    low = ((data["regularMarketDayLow"] ?: data["low"]) as? Number)?.toDouble(),
                    open = ((data["regularMarketOpen"] ?: data["open"]) as? Number)?.toDouble(),
                    close =
                            ((data["regularMarketPreviousClose"] ?: data["close"]) as? Number)
                                    ?.toDouble()
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse stock data JSON: $json", e)
            null
        }
    }

    private fun parseEconomicIndicator(json: String): EconomicIndicator? {
        return try {
            val data =
                    if (json.contains("messageType")) {
                        val message = objectMapper.readValue<StockMessage>(json)
                        message.data
                    } else {
                        objectMapper.readValue<Map<String, Any>>(json)
                    }

            EconomicIndicator(
                    indicator = data["indicator"] as String,
                    value = (data["value"]!! as Number).toDouble(),
                    timestamp = (data["timestamp"] as? Number)?.toLong()
                                    ?: System.currentTimeMillis(),
                    country = data["country"] as? String
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse economic indicator JSON: $json", e)
            null
        }
    }

    private fun processStockData(stockData: StockData): ProcessedStockData {
        val symbol = stockData.symbol

        // Thread-safe updates to history
        synchronized(priceHistory) {
            val prices = priceHistory.getOrPut(symbol) { mutableListOf() }
            val volumes = volumeHistory.getOrPut(symbol) { mutableListOf() }
            val timestamps = timestampHistory.getOrPut(symbol) { mutableListOf() }

            prices.add(stockData.price)
            volumes.add(stockData.volume)
            timestamps.add(stockData.timestamp)

            // Keep only last 200 data points for calculations
            val maxHistory = 200
            if (prices.size > maxHistory) {
                prices.removeAt(0)
                volumes.removeAt(0)
                timestamps.removeAt(0)
            }

            // Update market prices if this is SPY (market benchmark)
            if (symbol == "SPY") {
                marketPrices.add(stockData.price)
                if (marketPrices.size > maxHistory) marketPrices.removeAt(0)
            }
        }

        // Calculate enhanced metrics
        val currentPrices = priceHistory[symbol] ?: listOf(stockData.price)
        val currentVolumes = volumeHistory[symbol] ?: listOf(stockData.volume)

        val volatility = calculateVolatility(currentPrices)
        val priceChange =
                if (currentPrices.size > 1) {
                    stockData.price - currentPrices[currentPrices.size - 2]
                } else 0.0

        val priceChangePercent =
                if (currentPrices.size > 1) {
                    (priceChange / currentPrices[currentPrices.size - 2]) * 100
                } else 0.0

        val volumeAverage = currentVolumes.average()
        val riskScore = calculateRiskScore(currentPrices, volatility, abs(priceChangePercent))
        val trend = determineTrend(currentPrices)
        val supportResistance = calculateSupportResistance(currentPrices)

        return ProcessedStockData(
                symbol = symbol,
                currentPrice = stockData.price,
                volume = stockData.volume,
                timestamp = stockData.timestamp,
                volatility = volatility,
                priceChange = priceChange,
                priceChangePercent = priceChangePercent,
                volumeAverage = volumeAverage,
                riskScore = riskScore,
                trend = trend,
                support = supportResistance.first,
                resistance = supportResistance.second
        )
    }

    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0

        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            val return_ = (prices[i] - prices[i - 1]) / prices[i - 1]
            returns.add(return_)
        }

        if (returns.isEmpty()) return 0.0

        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance) * sqrt(252.0) // Annualized volatility
    }

    private fun calculateRiskScore(
            prices: List<Double>,
            volatility: Double,
            priceChangePercent: Double
    ): Double {
        if (prices.size < 10) return 0.0

        // Calculate Value at Risk (95% confidence)
        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            returns.add((prices[i] - prices[i - 1]) / prices[i - 1])
        }

        val sortedReturns = returns.sorted()
        val var95 =
                if (sortedReturns.isNotEmpty()) {
                    val index = (0.05 * sortedReturns.size).toInt()
                    abs(sortedReturns.getOrElse(index) { 0.0 })
                } else 0.0

        // Calculate max drawdown
        val maxDrawdown = calculateMaxDrawdown(prices)

        // Combine metrics into risk score (0-100 scale)
        val riskScore =
                ((volatility * 50) +
                                (var95 * 100) +
                                (maxDrawdown * 25) +
                                (abs(priceChangePercent) * 2))
                        .coerceIn(0.0, 100.0)

        return riskScore
    }

    private fun calculateMaxDrawdown(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0

        var maxDrawdown = 0.0
        var peak = prices[0]

        for (price in prices) {
            if (price > peak) {
                peak = price
            }
            val drawdown = (peak - price) / peak
            maxDrawdown = max(maxDrawdown, drawdown)
        }

        return maxDrawdown
    }

    private fun determineTrend(prices: List<Double>): String {
        if (prices.size < 5) return "UNKNOWN"

        val recent = prices.takeLast(5)
        val older = prices.takeLast(10).take(5)

        val recentAvg = recent.average()
        val olderAvg = older.average()

        return when {
            recentAvg > olderAvg * 1.02 -> "BULLISH"
            recentAvg < olderAvg * 0.98 -> "BEARISH"
            else -> "SIDEWAYS"
        }
    }

    private fun calculateSupportResistance(prices: List<Double>): Pair<Double?, Double?> {
        if (prices.size < 20) return Pair(null, null)

        val recent20 = prices.takeLast(20)
        val support = recent20.minOrNull()
        val resistance = recent20.maxOrNull()

        return Pair(support, resistance)
    }

    private fun storeProcessedStockData(data: JavaRDD<ProcessedStockData>) {
        try {
            val collected = data.collect()
            collected.forEach { processedStock ->
                logger.info(
                        "Processed ${processedStock.symbol}: price=${processedStock.currentPrice}, " +
                                "volatility=${String.format("%.4f", processedStock.volatility)}, " +
                                "risk=${String.format("%.2f", processedStock.riskScore)}, " +
                                "trend=${processedStock.trend}"
                )

                // TODO: Implement InfluxDB storage
                // writeToInfluxDB(processedStock)
            }

            // Log high-risk stocks
            val highRiskStocks = collected.filter { it.riskScore > 70 }
            if (highRiskStocks.isNotEmpty()) {
                logger.warn(
                        "HIGH RISK ALERT - Stocks with risk score > 70: ${highRiskStocks.map { "${it.symbol}(${String.format("%.2f", it.riskScore)})" }}"
                )
            }
        } catch (e: Exception) {
            logger.error("Error storing processed stock data", e)
        }
    }

    private fun storeEconomicIndicatorData(data: JavaRDD<EconomicIndicator>) {
        try {
            data.collect().forEach { indicator ->
                logger.info("Economic indicator: ${indicator.indicator} = ${indicator.value}")
                // TODO: Implement Cassandra storage
                // writeToCassandra(indicator)
            }
        } catch (e: Exception) {
            logger.error("Error storing economic indicator data", e)
        }
    }

    private fun startStreamingContext(jssc: JavaStreamingContext) {
        logger.info("Starting Spark Streaming context...")

        // Graceful shutdown hook
        Runtime.getRuntime()
                .addShutdownHook(
                        Thread {
                            logger.info(
                                    "Shutdown hook triggered, stopping Spark Streaming context..."
                            )
                            try {
                                jssc.stop(true, true)
                                logger.info("Spark Streaming context stopped gracefully.")
                            } catch (e: Exception) {
                                logger.error("Error during shutdown", e)
                            }
                        }
                )

        jssc.start()
        logger.info(
                "Spark Streaming context started. Monitoring stock market risks and volatility..."
        )

        try {
            jssc.awaitTermination()
        } catch (e: InterruptedException) {
            logger.warn("Spark Streaming context interrupted", e)
            Thread.currentThread().interrupt()
        }
    }

    // TODO: Implement these methods
    private fun writeToInfluxDB(data: ProcessedStockData) {
        // Implementation for InfluxDB storage
    }

    private fun writeToCassandra(data: EconomicIndicator) {
        // Implementation for Cassandra storage
    }
}
