@file:Suppress("MagicNumber")

package com.harshsbajwa.stockifai.processing

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.WriteApiBlocking
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import org.apache.spark.SparkConf
import org.apache.spark.api.java.function.VoidFunction2
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.*
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.streaming.Trigger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.exitProcess

data class StockData(
    val symbol: String,
    val price: Double,
    val volume: Long,
    val timestamp: Long,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double? = null,
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
    val resistance: Double?,
)

data class EconomicIndicator(
    val indicator: String,
    val value: Double,
    val timestamp: Long,
    val country: String? = null,
)

object Processing {
    private val logger = LoggerFactory.getLogger(Processing::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Database clients
    private lateinit var influxDBClient: InfluxDBClient
    private lateinit var influxOrg: String
    private lateinit var cassandraSession: CqlSession
    private lateinit var stockInsertStatement: PreparedStatement
    private lateinit var indicatorInsertStatement: PreparedStatement

    // Thread-safe in-memory storage for calculations
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()
    private val volumeHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val timestampHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val marketPrices = mutableListOf<Double>()

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Initializing Stockifai Spark Structured Streaming Processor...")

        try {
            initializeDatabaseConnections()
            val spark = createSparkSession()
            val queries = startStreamingQueries(spark)

            // Wait for termination
            queries.forEach { it.awaitTermination() }
        } catch (e: Exception) {
            logger.error("Failed to start Spark Processor Application", e)
            exitProcess(1)
        } finally {
            cleanup()
        }
    }

    private fun createSparkSession(): SparkSession {
        val sparkConf =
            SparkConf()
                .setAppName("Stockifai-StructuredStreaming")
                .set("spark.sql.adaptive.enabled", "true")
                .set("spark.sql.adaptive.coalescePartitions.enabled", "true")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
                .set("spark.sql.streaming.checkpointLocation", "/tmp/spark-checkpoint")
                .apply {
                    if (!contains("spark.master")) {
                        setMaster("local[*]")
                    }
                }

        return SparkSession.builder().config(sparkConf).getOrCreate()
    }

    private fun initializeDatabaseConnections() {
        // Initialize InfluxDB client
        val influxUrl = System.getenv("INFLUXDB_URL") ?: "http://localhost:8086"
        val influxToken = System.getenv("INFLUXDB_TOKEN") ?: "your-token"
        this.influxOrg = System.getenv("INFLUXDB_ORG") ?: "stockifai"

        influxDBClient =
            InfluxDBClientFactory.create(influxUrl, influxToken.toCharArray(), this.influxOrg)
        logger.info("InfluxDB client initialized: $influxUrl, Org: ${this.influxOrg}")

        // Initialize Cassandra session
        val cassandraHost = System.getenv("CASSANDRA_HOST") ?: "localhost"
        val cassandraPort = System.getenv("CASSANDRA_PORT")?.toInt() ?: 9042
        val cassandraKeyspace = System.getenv("CASSANDRA_KEYSPACE") ?: "stockifai"

        cassandraSession =
            CqlSession
                .builder()
                .addContactPoint(java.net.InetSocketAddress(cassandraHost, cassandraPort))
                .withLocalDatacenter("datacenter1")
                .withKeyspace(cassandraKeyspace)
                .build()

        // Create keyspace and tables if they don't exist
        createCassandraSchema()

        // Prepare statements
        stockInsertStatement =
            cassandraSession.prepare(
            """
            INSERT INTO processed_stocks (symbol, timestamp, current_price, volume, volatility,
                                        price_change, price_change_percent, volume_average,
                                        risk_score, trend, support, resistance)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            )

        indicatorInsertStatement =
            cassandraSession.prepare(
            """
            INSERT INTO economic_indicators (indicator, timestamp, value, country)
            VALUES (?, ?, ?, ?)
            """,
            )

        logger.info(
            "Cassandra session initialized: $cassandraHost:$cassandraPort, Keyspace: $cassandraKeyspace",
        )
    }

    private fun createCassandraSchema() {
        val keyspaceName = System.getenv("CASSANDRA_KEYSPACE") ?: "stockifai"
        // Create keyspace
        cassandraSession.execute(
            """
            CREATE KEYSPACE IF NOT EXISTS $keyspaceName
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
            """,
        )

        // Create processed_stocks table
        cassandraSession.execute(
            """
            CREATE TABLE IF NOT EXISTS $keyspaceName.processed_stocks (
                symbol text,
                timestamp bigint,
                current_price double,
                volume bigint,
                volatility double,
                price_change double,
                price_change_percent double,
                volume_average double,
                risk_score double,
                trend text,
                support double,
                resistance double,
                PRIMARY KEY (symbol, timestamp)
            ) WITH CLUSTERING ORDER BY (timestamp DESC)
            """,
        )

        // Create economic_indicators table
        cassandraSession.execute(
            """
            CREATE TABLE IF NOT EXISTS $keyspaceName.economic_indicators (
                indicator text,
                timestamp bigint,
                value double,
                country text,
                PRIMARY KEY (indicator, timestamp)
            ) WITH CLUSTERING ORDER BY (timestamp DESC)
            """,
        )

        logger.info("Cassandra schema created/verified for keyspace $keyspaceName")
    }

    private fun startStreamingQueries(spark: SparkSession): List<StreamingQuery> {
        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
        val queries = mutableListOf<StreamingQuery>()

        // Stock prices streaming query
        val stockPricesQuery =
            spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "stock_prices")
                .option("startingOffsets", "latest")
                .option("maxOffsetsPerTrigger", "1000")
                .load()
                .select(col("value").cast("string").alias("json_data"))
                .writeStream()
                .foreachBatch(
                    VoidFunction2 { batch, batchId ->
                        processStockPriceBatch(batch, batchId)
                    },
                ).trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .start()

        queries.add(stockPricesQuery)

        // Economic indicators streaming query
        val economicIndicatorsQuery =
            spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "economic_indicators")
                .option("startingOffsets", "latest")
                .option("maxOffsetsPerTrigger", "500")
                .load()
                .select(col("value").cast("string").alias("json_data"))
                .writeStream()
                .foreachBatch(
                    VoidFunction2 { batch, batchId ->
                        processEconomicIndicatorBatch(batch, batchId)
                    },
                ).trigger(Trigger.ProcessingTime(30, TimeUnit.SECONDS))
                .start()

        queries.add(economicIndicatorsQuery)

        logger.info("Started ${queries.size} streaming queries")
        return queries
    }

    private fun processStockPriceBatch(
        batch: Dataset<Row>,
        batchId: Long,
    ) {
        if (batch.isEmpty) return

        logger.info("Processing stock price batch $batchId with ${batch.count()} records")

        try {
            val rows: List<Row> = batch.collectAsList()

            val listOfNullableProcessedData: List<ProcessedStockData?> =
                rows.map { row ->
                    try {
                        val jsonData = row.getAs<String>("json_data")
                        val stockData = parseStockData(jsonData)
                        stockData?.let(::processStockData)
                    } catch (e: Exception) {
                        logger.warn(
                            "Failed to process stock record in batch $batchId: ${e.message}",
                            e,
                        )
                        null
                    }
                }

            val processedData = listOfNullableProcessedData.filterNotNull()

            processedData.forEach { processedStock ->
                writeToInfluxDB(processedStock)
                writeToCassandra(processedStock)

                logger.debug(
                    "Processed ${processedStock.symbol}: price=${processedStock.currentPrice}, " +
                        "volatility=${String.format("%.4f", processedStock.volatility)}, " +
                        "risk=${String.format("%.2f", processedStock.riskScore)}",
                )
            }

            // Log high-risk stocks
            val highRiskStocks = processedData.filter { it.riskScore > 70 }
            if (highRiskStocks.isNotEmpty()) {
                logger.warn(
                    "HIGH RISK ALERT - Stocks with risk score > 70: ${highRiskStocks.map {
                        "${it.symbol}(${String.format("%.2f", it.riskScore)})"
                    }}",
                )
            }

            logger.info("Successfully processed batch $batchId")
        } catch (e: Exception) {
            logger.error("Error processing stock price batch $batchId", e)
        }
    }

    private fun processEconomicIndicatorBatch(
        batch: Dataset<Row>,
        batchId: Long,
    ) {
        if (batch.isEmpty) return

        logger.info("Processing economic indicator batch $batchId with ${batch.count()} records")

        try {
            val rows: List<Row> = batch.collectAsList()

            val listOfNullableIndicators: List<EconomicIndicator?> =
                rows.map { row ->
                    try {
                        val jsonData = row.getAs<String>("json_data")
                        parseEconomicIndicator(jsonData)
                    } catch (e: Exception) {
                        logger.warn(
                            "Failed to process economic indicator record in batch $batchId: ${e.message} for row: ${row.getAs<String>(
                                "json_data",
                            )?.take(100)}...",
                            e,
                        )
                        null
                    }
                }

            val indicators: List<EconomicIndicator> = listOfNullableIndicators.filterNotNull()

            indicators.forEach { indicator ->
                writeToInfluxDB(indicator)
                writeToCassandra(indicator)
                logger.info(
                    "Economic indicator: ${indicator.indicator} = ${indicator.value} (Timestamp: ${indicator.timestamp})",
                )
            }

            logger.info("Successfully processed economic indicator batch $batchId")
        } catch (e: Exception) {
            logger.error("Error processing economic indicator batch $batchId", e)
        }
    }

    private fun parseStockData(json: String): StockData? =
        try {
            val data =
                if (json.contains("\"messageType\"")) {
                    val message = objectMapper.readValue<Map<String, Any>>(json)
                    @Suppress("UNCHECKED_CAST")
                    message["data"] as? Map<String, Any>
                        ?: run {
                            logger.warn(
                                "StockData: 'data' field is missing or not a map in message: $json",
                            )
                            return null
                        }
                } else {
                    objectMapper.readValue<Map<String, Any>>(json)
                }

            StockData(
                symbol =
                    data["symbol"] as? String
                        ?: run {
                            logger.warn(
                                "StockData: 'symbol' missing or not a String: $data",
                            )
                            return null
                        },
                price =
                    ((data["regularMarketPrice"] ?: data["price"]) as? Number)
                        ?.toDouble()
                        ?: run {
                            logger.warn(
                                "StockData: 'price' missing or not a Number for symbol ${data["symbol"]}: $data",
                            )
                            return null
                        },
                volume =
                    ((data["regularMarketVolume"] ?: data["volume"]) as? Number)
                        ?.toLong()
                        ?: run {
                            logger.warn(
                                "StockData: 'volume' missing or not a Number for symbol ${data["symbol"]}: $data",
                            )
                            return null
                        },
                timestamp =
                    (data["timestamp"] as? Number)?.toLong()
                        ?: (data["regularMarketTime"] as? Number)
                            ?.toLong()
                            ?.times(1000)
                        ?: Instant.now().toEpochMilli(),
                high =
                    ((data["regularMarketDayHigh"] ?: data["high"]) as? Number)
                        ?.toDouble(),
                low = ((data["regularMarketDayLow"] ?: data["low"]) as? Number)?.toDouble(),
                open = ((data["regularMarketOpen"] ?: data["open"]) as? Number)?.toDouble(),
                close =
                    ((data["regularMarketPreviousClose"] ?: data["close"]) as? Number)
                        ?.toDouble(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse stock data JSON: $json", e)
            null
        }

    private fun parseEconomicIndicator(json: String): EconomicIndicator? =
        try {
            val data =
                if (json.contains("\"messageType\"")) {
                    val message = objectMapper.readValue<Map<String, Any>>(json)
                    @Suppress("UNCHECKED_CAST")
                    message["data"] as? Map<String, Any>
                        ?: run {
                            logger.warn(
                                "EconomicIndicator: 'data' field is missing or not a map in message: $json",
                            )
                            return null
                        }
                } else {
                    objectMapper.readValue<Map<String, Any>>(json)
                }

            EconomicIndicator(
                indicator =
                    data["indicator"] as? String
                        ?: run {
                            logger.warn(
                                "EconomicIndicator: 'indicator' missing or not a String: $data",
                            )
                            return null
                        },
                value =
                    (data["value"] as? Number)?.toDouble()
                        ?: run {
                            logger.warn(
                                "EconomicIndicator: 'value' missing or not a Number for indicator ${data["indicator"]}: $data",
                            )
                            return null
                        },
                timestamp =
                    (data["timestamp"] as? Number)?.toLong()
                        ?: Instant.now().toEpochMilli(),
                country = data["country"] as? String,
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse economic indicator JSON: $json", e)
            null
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
        val currentPrices = priceHistory[symbol]?.toList() ?: listOf(stockData.price)
        val currentVolumes = volumeHistory[symbol]?.toList() ?: listOf(stockData.volume)

        val volatility = calculateVolatility(currentPrices)
        val priceChange =
            if (currentPrices.size > 1) {
                stockData.price - currentPrices[currentPrices.size - 2]
            } else {
                0.0
            }

        val priceChangePercent =
            if (currentPrices.size > 1 && currentPrices[currentPrices.size - 2] != 0.0) {
                (priceChange / currentPrices[currentPrices.size - 2]) * 100
            } else {
                0.0
            }

        val volumeAverage = if (currentVolumes.isNotEmpty()) currentVolumes.average() else 0.0
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
            resistance = supportResistance.second,
        )
    }

    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0

        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            if (prices[i - 1] != 0.0) { // Avoid division by zero
                val returnVal = (prices[i] - prices[i - 1]) / prices[i - 1]
                returns.add(returnVal)
            }
        }

        if (returns.isEmpty()) return 0.0

        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance) * sqrt(252.0) // Annualized volatility (assuming 252 trading days)
    }

    private fun calculateRiskScore(
        prices: List<Double>,
        volatility: Double,
        priceChangePercent: Double,
    ): Double {
        if (prices.size < 10) return 0.0 // Need enough data points

        // Calculate Value at Risk (95% confidence)
        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            if (prices[i - 1] != 0.0) {
                returns.add((prices[i] - prices[i - 1]) / prices[i - 1])
            }
        }

        val sortedReturns = returns.sorted()
        val var95 =
            if (sortedReturns.isNotEmpty()) {
                val index =
                    (0.05 * sortedReturns.size).toInt().coerceAtMost(sortedReturns.size - 1)
                abs(sortedReturns.getOrElse(index) { 0.0 })
            } else {
                0.0
            }

        // Calculate max drawdown
        val maxDrawdown = calculateMaxDrawdown(prices)

        // Combine metrics into risk score (0-100 scale)
        val riskScore =
            (
                (volatility * 50) + // Weight for volatility
                    (var95 * 1000) + // VaR is a direct risk measure, higher weight
                    (maxDrawdown * 250) + // Max drawdown also significant
                    (abs(priceChangePercent))
            ) // Recent price change
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
            if (peak != 0.0) { // Avoid division by zero
                val drawdown = (peak - price) / peak
                maxDrawdown = max(maxDrawdown, drawdown)
            }
        }
        return maxDrawdown
    }

    private fun determineTrend(prices: List<Double>): String {
        if (prices.size < 10) return "UNKNOWN" // Need more data for a reliable short-term trend

        val recentWindow = prices.takeLast(5) // Last 5 data points
        val olderWindow = prices.takeLast(10).take(5) // Data points from 10th last to 6th last

        if (recentWindow.isEmpty() || olderWindow.isEmpty()) return "UNKNOWN"

        val recentAvg = recentWindow.average()
        val olderAvg = olderWindow.average()

        return when {
            recentAvg > olderAvg * 1.015 -> "BULLISH" // 1.5% increase threshold
            recentAvg < olderAvg * 0.985 -> "BEARISH" // 1.5% decrease threshold
            else -> "SIDEWAYS"
        }
    }

    private fun calculateSupportResistance(prices: List<Double>): Pair<Double?, Double?> {
        if (prices.size < 20) return Pair(null, null) // Need enough data for S/R levels

        val recentWindow = prices.takeLast(20) // Consider last 20 periods
        val support = recentWindow.minOrNull()
        val resistance = recentWindow.maxOrNull()

        return Pair(support, resistance)
    }

    private fun writeToInfluxDB(data: ProcessedStockData) {
        try {
            val writeApi: WriteApiBlocking = influxDBClient.writeApiBlocking
            val bucket = System.getenv("INFLUXDB_BUCKET") ?: "stockdata"

            val point =
                Point
                    .measurement("stock_metrics")
                    .addTag("symbol", data.symbol)
                    .addTag("trend", data.trend)
                    .addField("price", data.currentPrice)
                    .addField("volume", data.volume)
                    .addField("volatility", data.volatility)
                    .addField("price_change", data.priceChange)
                    .addField("price_change_percent", data.priceChangePercent)
                    .addField("volume_average", data.volumeAverage)
                    .addField("risk_score", data.riskScore)
                    .apply {
                        data.support?.let { addField("support", it) }
                        data.resistance?.let { addField("resistance", it) }
                    }.time(Instant.ofEpochMilli(data.timestamp), WritePrecision.MS)

            writeApi.writePoint(bucket, this.influxOrg, point)
            logger.debug("Written stock data to InfluxDB: ${data.symbol} at ${data.timestamp}")
        } catch (e: Exception) {
            logger.error("Failed to write stock data to InfluxDB for ${data.symbol}", e)
        }
    }

    private fun writeToInfluxDB(data: EconomicIndicator) {
        try {
            val writeApi: WriteApiBlocking = influxDBClient.writeApiBlocking
            val bucket = System.getenv("INFLUXDB_BUCKET") ?: "stockdata"

            val point =
                Point
                    .measurement("economic_indicators")
                    .addTag("indicator", data.indicator)
                    .apply {
                        data.country?.let { addTag("country", it) }
                    }
                    .addField("value", data.value)
                    .time(Instant.ofEpochMilli(data.timestamp), WritePrecision.MS)

            writeApi.writePoint(bucket, this.influxOrg, point)
            logger.info("Written economic indicator to InfluxDB: ${data.indicator} = ${data.value} at ${data.timestamp}")
        } catch (e: Exception) {
            logger.error("Failed to write economic indicator to InfluxDB for ${data.indicator}", e)
        }
    }

    private fun writeToCassandra(data: ProcessedStockData) {
        try {
            cassandraSession
                .executeAsync(
                    stockInsertStatement.bind(
                        data.symbol,
                        data.timestamp,
                        data.currentPrice,
                        data.volume,
                        data.volatility,
                        data.priceChange,
                        data.priceChangePercent,
                        data.volumeAverage,
                        data.riskScore,
                        data.trend,
                        data.support,
                        data.resistance,
                    ),
                ).toCompletableFuture()
            logger.debug("Written stock data to Cassandra: ${data.symbol} at ${data.timestamp}")
        } catch (e: Exception) {
            logger.error("Failed to write stock data to Cassandra for ${data.symbol}", e)
        }
    }

    private fun writeToCassandra(data: EconomicIndicator) {
        try {
            cassandraSession.executeAsync(
                indicatorInsertStatement.bind(
                    data.indicator,
                    data.timestamp,
                    data.value,
                    data.country,
                ),
            )
            logger.debug(
                "Written economic indicator to Cassandra: ${data.indicator} at ${data.timestamp}",
            )
        } catch (e: Exception) {
            logger.error("Failed to write economic indicator to Cassandra for ${data.indicator}", e)
        }
    }

    private fun cleanup() {
        try {
            if (::influxDBClient.isInitialized) influxDBClient.close()
            if (::cassandraSession.isInitialized && !cassandraSession.isClosed) {
                cassandraSession.close()
            }
            logger.info("Database connections closed")
        } catch (e: Exception) {
            logger.error("Error during cleanup", e)
        }
    }
}