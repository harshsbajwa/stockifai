@file:Suppress("MagicNumber")

package com.harshsbajwa.stockifai.processing

import com.harshsbajwa.stockifai.avro.finnhub.MarketNews
import com.harshsbajwa.stockifai.avro.finnhub.StockCandle
import com.harshsbajwa.stockifai.avro.fred.EconomicObservation
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.influxdb.v3.client.InfluxDBClient
import com.influxdb.v3.client.Point
import org.apache.spark.SparkConf
import org.apache.spark.sql.*
import org.apache.spark.sql.avro.functions.from_avro
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.*
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types.DataTypes
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlin.system.exitProcess

// Risk Calculation Data Models
data class CalculatedRiskMetrics(
    val symbol: String,
    val timestamp: Long,
    val close: Double,
    val volume: Long,
    val historicalVolatility30d: Double?,
    val beta: Double?,
    val var99: Double?,
    val cvar99: Double?,
    val calculationDate: String
)

data class ProcessedNews(
    val symbol: String?,
    val headline: String,
    val summary: String,
    val sentiment: String, // POSITIVE, NEGATIVE, NEUTRAL
    val timestamp: Long
)

data class ProcessedEconomicData(
    val seriesId: String,
    val value: Double,
    val date: String,
    val timestamp: Long
)

object RiskCalculationEngine {
    private val logger = LoggerFactory.getLogger(RiskCalculationEngine::class.java)

    // Database clients
    private lateinit var influxDBClient: InfluxDBClient
    private lateinit var cassandraSession: CqlSession

    // Prepared statements
    private lateinit var riskMetricsInsertStatement: PreparedStatement
    private lateinit var economicDataInsertStatement: PreparedStatement

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Initializing StockifAI Risk Calculation Engine...")

        try {
            initializeDatabaseConnections()
            val spark = createSparkSession()
            val queries = startStreamingQueries(spark)

            // Wait for termination
            queries.forEach { it.awaitTermination() }
        } catch (e: Exception) {
            logger.error("Failed to start Risk Calculation Engine", e)
            exitProcess(1)
        } finally {
            cleanup()
        }
    }

    private fun createSparkSession(): SparkSession {
        val sparkConf = SparkConf()
            .setAppName("StockifAI-RiskCalculationEngine")
            .set("spark.sql.adaptive.enabled", "true")
            .set("spark.sql.adaptive.coalescePartitions.enabled", "true")
            .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .set("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
            .set("spark.sql.streaming.checkpointLocation", "/tmp/spark-checkpoint")
            .set("spark.sql.execution.arrow.pyspark.enabled", "true")
            .apply {
                if (!contains("spark.master")) {
                    setMaster("local[*]")
                }
            }

        return SparkSession.builder().config(sparkConf).getOrCreate()
    }

    private fun initializeDatabaseConnections() {
        // Initialize InfluxDB client (v3)
        val influxUrl = System.getenv("INFLUXDB_URL") ?: "http://localhost:8086"
        val influxToken = System.getenv("INFLUXDB_TOKEN") ?: "your-token"
        val influxDatabase = System.getenv("INFLUXDB_DATABASE") ?: "stockifai"

        influxDBClient = InfluxDBClient.getInstance(influxUrl, influxToken, influxDatabase)
        logger.info("InfluxDB client v3 initialized: $influxUrl, Database: $influxDatabase")

        // Initialize Cassandra session
        val cassandraHost = System.getenv("CASSANDRA_HOST") ?: "localhost"
        val cassandraPort = System.getenv("CASSANDRA_PORT")?.toInt() ?: 9042
        val cassandraKeyspace = System.getenv("CASSANDRA_KEYSPACE") ?: "finrisk_reference_data"

        cassandraSession = CqlSession.builder()
            .addContactPoint(java.net.InetSocketAddress(cassandraHost, cassandraPort))
            .withLocalDatacenter("datacenter1")
            .withKeyspace(cassandraKeyspace)
            .build()

        createCassandraSchema()
        prepareStatements()

        logger.info("Cassandra session initialized: $cassandraHost:$cassandraPort, Keyspace: $cassandraKeyspace")
    }

    private fun createCassandraSchema() {
        val keyspaceName = System.getenv("CASSANDRA_KEYSPACE") ?: "finrisk_reference_data"

        // Create keyspace
        cassandraSession.execute("""
            CREATE KEYSPACE IF NOT EXISTS $keyspaceName
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
        """)

        // Create instrument metadata table
        cassandraSession.execute("""
            CREATE TABLE IF NOT EXISTS $keyspaceName.instrument_metadata (
                symbol text PRIMARY KEY,
                name text,
                exchange text,
                currency text,
                sector text,
                industry text,
                description text,
                last_updated timestamp
            )
        """)

        // Create economic indicator metadata table
        cassandraSession.execute("""
            CREATE TABLE IF NOT EXISTS $keyspaceName.economic_indicator_metadata (
                series_id text PRIMARY KEY,
                title text,
                frequency text,
                units text,
                notes text,
                source text,
                last_updated timestamp
            )
        """)

        logger.info("Cassandra schema created/verified for keyspace $keyspaceName")
    }

    private fun prepareStatements() {
        // For now, we're primarily using InfluxDB for time-series data
    }

    private fun startStreamingQueries(spark: SparkSession): List<StreamingQuery> {
        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
        val schemaRegistryUrl = System.getenv("SCHEMA_REGISTRY_URL") ?: "http://localhost:8081"
        
        val queries = mutableListOf<StreamingQuery>()

        // OHLCV Data Processing Query
        val ohlcvQuery = processOHLCVData(spark, kafkaBootstrapServers, schemaRegistryUrl)
        queries.add(ohlcvQuery)

        // Market News Processing Query
        val newsQuery = processMarketNews(spark, kafkaBootstrapServers, schemaRegistryUrl)
        queries.add(newsQuery)

        // Economic Data Processing Query
        val economicQuery = processEconomicData(spark, kafkaBootstrapServers, schemaRegistryUrl)
        queries.add(economicQuery)

        logger.info("Started ${queries.size} streaming queries")
        return queries
    }

    private fun processOHLCVData(spark: SparkSession, kafkaBootstrapServers: String, schemaRegistryUrl: String): StreamingQuery {
        // Read from Kafka
        val ohlcvRawStream = spark.readStream()
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaBootstrapServers)
            .option("subscribe", "finnhub-ohlcv-data")
            .option("startingOffsets", "latest")
            .option("failOnDataLoss", "false")
            .load()

        // Schema for StockCandle Avro
        val stockCandleSchema = """{
            "type": "record",
            "name": "StockCandle",
            "namespace": "com.harshsbajwa.stockifai.avro.finnhub",
            "fields": [
                {"name": "symbol", "type": "string"},
                {"name": "open", "type": "double"},
                {"name": "high", "type": "double"},
                {"name": "low", "type": "double"},
                {"name": "close", "type": "double"},
                {"name": "volume", "type": "long"},
                {"name": "timestamp", "type": {"type": "long", "logicalType": "timestamp-millis"}}
            ]
        }"""

        val ohlcvStream = ohlcvRawStream
            .select(from_avro(col("value"), stockCandleSchema).alias("data"))
            .select("data.*")
            .withColumn("timestamp", (col("timestamp") / 1000).cast(DataTypes.TimestampType))
            .withWatermark("timestamp", "10 minutes")

        // Calculate daily returns
        val windowSpec = Window.partitionBy("symbol").orderBy("timestamp")
        val ohlcvWithReturns = ohlcvStream
            .withColumn("prev_close", lag("close", 1).over(windowSpec))
            .filter(col("prev_close").isNotNull)
            .withColumn("daily_return", log(col("close") / col("prev_close")))

        // For Beta calculation, we need market index data (SPY)
        val marketIndexStream = ohlcvWithReturns
            .filter(col("symbol") === "SPY")
            .select(col("timestamp"), col("daily_return").alias("market_return"))
            .withWatermark("timestamp", "10 minutes")

        // Join stock data with market data for Beta calculation
        val joinedStreamForBeta = ohlcvWithReturns
            .filter(col("symbol") =!= "SPY")
            .join(
                marketIndexStream,
                ohlcvWithReturns.col("timestamp") === marketIndexStream.col("timestamp"),
                "inner"
            )

        // Calculate risk metrics
        val riskMetricsStream = calculateRiskMetrics(joinedStreamForBeta)

        return riskMetricsStream.writeStream()
            .foreachBatch { (batchDF: Dataset[Row], batchId: Long) =>
                if (!batchDF.isEmpty) {
                    logger.info("Processing OHLCV batch $batchId with ${batchDF.count()} records")
                    
                    batchDF.collect().forEach { row =>
                        try {
                            val metrics = CalculatedRiskMetrics(
                                symbol = row.getAs[String]("symbol"),
                                timestamp = row.getAs[java.sql.Timestamp]("timestamp").getTime,
                                close = row.getAs[Double]("close"),
                                volume = row.getAs[Long]("volume"),
                                historicalVolatility30d = Option(row.getAs[Double]("hv_30d")).getOrElse(null),
                                beta = Option(row.getAs[Double]("beta")).getOrElse(null),
                                var99 = Option(row.getAs[Double]("var_99")).getOrElse(null),
                                cvar99 = Option(row.getAs[Double]("cvar_99")).getOrElse(null),
                                calculationDate = java.time.LocalDate.now().toString
                            )
                            
                            writeRiskMetricsToInfluxDB(metrics)
                            
                        } catch (e: Exception) {
                            logger.error("Error processing risk metrics for row: ${e.message}", e)
                        }
                    }
                }
            }
            .trigger(Trigger.ProcessingTime(30, TimeUnit.SECONDS))
            .start()
    }

    private fun calculateRiskMetrics(joinedStream: Dataset[Row]): Dataset[Row] {
        // Historical Volatility (30-day)
        val hv30dWindow = Window.partitionBy("symbol")
            .orderBy(col("timestamp").cast("long"))
            .rowsBetween(-29, 0)

        val withHV = joinedStream
            .withColumn("stddev_30d_returns", stddev_pop(col("daily_return")).over(hv30dWindow))
            .withColumn("hv_30d", col("stddev_30d_returns") * sqrt(lit(252.0)))

        // Beta calculation (using covariance and variance)
        val betaWindow = Window.partitionBy("symbol")
            .orderBy(col("timestamp").cast("long"))
            .rowsBetween(-251, 0) // 1 year

        val withBeta = withHV
            .withColumn("covar_stock_market", covar_pop(col("daily_return"), col("market_return")).over(betaWindow))
            .withColumn("var_market", var_pop(col("market_return")).over(betaWindow))
            .withColumn("beta", 
                when(col("var_market") =!= 0.0, col("covar_stock_market") / col("var_market"))
                .otherwise(null)
            )

        // VaR and CVaR using UDFs (simplified approach)
        val calculateVaRUDF = udf((returns: Seq[Double], confidenceLevel: Double) => {
            if (returns == null || returns.isEmpty) null
            else {
                val sortedReturns = returns.filterNot(_.isNaN).sorted
                if (sortedReturns.isEmpty) null
                else {
                    val index = (sortedReturns.length * (1.0 - confidenceLevel)).toInt
                    if (index < sortedReturns.length) sortedReturns(index) else null
                }
            }
        })

        val calculateCVaRUDF = udf((returns: Seq[Double], vaR: Double) => {
            if (returns == null || vaR == null) null
            else {
                val tailLosses = returns.filterNot(_.isNaN).filter(_ < vaR)
                if (tailLosses.isEmpty) null else tailLosses.sum / tailLosses.length
            }
        })

        val varCvarWindow = Window.partitionBy("symbol")
            .orderBy("timestamp")
            .rowsBetween(-251, 0) // 1 year for VaR calculation

        val withVaRCVaR = withBeta
            .withColumn("historical_returns_window", collect_list("daily_return").over(varCvarWindow))
            .filter(size(col("historical_returns_window")) >= 252)
            .withColumn("var_99", calculateVaRUDF(col("historical_returns_window"), lit(0.99)))
            .withColumn("cvar_99", calculateCVaRUDF(col("historical_returns_window"), col("var_99")))

        return withVaRCVaR
    }

    private fun processMarketNews(spark: SparkSession, kafkaBootstrapServers: String, schemaRegistryUrl: String): StreamingQuery {
        val newsRawStream = spark.readStream()
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaBootstrapServers)
            .option("subscribe", "finnhub-market-news-data,finnhub-company-news-data")
            .option("startingOffsets", "latest")
            .option("failOnDataLoss", "false")
            .load()

        val marketNewsSchema = """{
            "type": "record",
            "name": "MarketNews",
            "namespace": "com.harshsbajwa.stockifai.avro.finnhub",
            "fields": [
                {"name": "category", "type": "string"},
                {"name": "datetime", "type": {"type": "long", "logicalType": "timestamp-millis"}},
                {"name": "headline", "type": "string"},
                {"name": "id", "type": "long"},
                {"name": "image", "type": ["null", "string"], "default": null},
                {"name": "related", "type": ["null", "string"], "default": null},
                {"name": "source", "type": "string"},
                {"name": "summary", "type": "string"},
                {"name": "url", "type": "string"}
            ]
        }"""

        val newsStream = newsRawStream
            .select(from_avro(col("value"), marketNewsSchema).alias("data"))
            .select("data.*")
            .withColumn("timestamp", (col("datetime") / 1000).cast(DataTypes.TimestampType))

        return newsStream.writeStream()
            .foreachBatch { (batchDF: Dataset[Row], batchId: Long) =>
                if (!batchDF.isEmpty) {
                    logger.info("Processing news batch $batchId with ${batchDF.count()} records")
                    
                    batchDF.collect().forEach { row =>
                        try {
                            val processedNews = ProcessedNews(
                                symbol = Option(row.getAs[String]("related")).getOrElse(null),
                                headline = row.getAs[String]("headline"),
                                summary = row.getAs[String]("summary"),
                                sentiment = analyzeSentiment(row.getAs[String]("headline"), row.getAs[String]("summary")),
                                timestamp = row.getAs[java.sql.Timestamp]("timestamp").getTime
                            )
                            
                            writeNewsToInfluxDB(processedNews)
                            
                        } catch (e: Exception) {
                            logger.error("Error processing news for row: ${e.message}", e)
                        }
                    }
                }
            }
            .trigger(Trigger.ProcessingTime(60, TimeUnit.SECONDS))
            .start()
    }

    private fun processEconomicData(spark: SparkSession, kafkaBootstrapServers: String, schemaRegistryUrl: String): StreamingQuery {
        val economicRawStream = spark.readStream()
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaBootstrapServers)
            .option("subscribe", "fred-economic-observations-data")
            .option("startingOffsets", "latest")
            .option("failOnDataLoss", "false")
            .load()

        val economicObservationSchema = """{
            "type": "record",
            "name": "EconomicObservation",
            "namespace": "com.harshsbajwa.stockifai.avro.fred",
            "fields": [
                {"name": "seriesId", "type": "string"},
                {"name": "observationDate", "type": "string"},
                {"name": "value", "type": "string"},
                {"name": "realTimeStart", "type": "string"},
                {"name": "realTimeEnd", "type": "string"}
            ]
        }"""

        val economicStream = economicRawStream
            .select(from_avro(col("value"), economicObservationSchema).alias("data"))
            .select("data.*")
            .withColumn("observationDate", to_date(col("observationDate"), "yyyy-MM-dd"))
            .withColumn("parsedValue",
                when(col("value").equalTo("."), lit(null).cast(DataTypes.DoubleType))
                .otherwise(col("value").cast(DataTypes.DoubleType))
            )
            .filter(col("parsedValue").isNotNull)

        return economicStream.writeStream()
            .foreachBatch { (batchDF: Dataset[Row], batchId: Long) =>
                if (!batchDF.isEmpty) {
                    logger.info("Processing economic data batch $batchId with ${batchDF.count()} records")
                    
                    batchDF.collect().forEach { row =>
                        try {
                            val processedEconomic = ProcessedEconomicData(
                                seriesId = row.getAs[String]("seriesId"),
                                value = row.getAs[Double]("parsedValue"),
                                date = row.getAs[java.sql.Date]("observationDate").toString,
                                timestamp = System.currentTimeMillis()
                            )
                            
                            writeEconomicDataToInfluxDB(processedEconomic)
                            
                        } catch (e: Exception) {
                            logger.error("Error processing economic data for row: ${e.message}", e)
                        }
                    }
                }
            }
            .trigger(Trigger.ProcessingTime(60, TimeUnit.SECONDS))
            .start()
    }

    private fun writeRiskMetricsToInfluxDB(metrics: CalculatedRiskMetrics) {
        try {
            val points = mutableListOf<Point>()
            
            // Stock OHLCV measurement
            val ohlcvPoint = Point.measurement("stock_ohlcv")
                .addTag("symbol", metrics.symbol)
                .addField("close", metrics.close)
                .addField("volume", metrics.volume)
                .timestamp(Instant.ofEpochMilli(metrics.timestamp))
            points.add(ohlcvPoint)
            
            // Calculated risk metrics measurement
            val riskPoint = Point.measurement("calculated_risk_metrics")
                .addTag("symbol", metrics.symbol)
                .addTag("metric_type", "DAILY_SUMMARY")
                .apply {
                    metrics.historicalVolatility30d?.let { addField("hv_30d", it) }
                    metrics.beta?.let { addField("beta", it) }
                    metrics.var99?.let { addField("var_99", it) }
                    metrics.cvar99?.let { addField("cvar_99", it) }
                }
                .timestamp(Instant.ofEpochMilli(metrics.timestamp))
            points.add(riskPoint)
            
            influxDBClient.writePoints(points)
            logger.debug("Written risk metrics to InfluxDB: ${metrics.symbol}")
            
        } catch (e: Exception) {
            logger.error("Failed to write risk metrics to InfluxDB for ${metrics.symbol}", e)
        }
    }

    private fun writeNewsToInfluxDB(news: ProcessedNews) {
        try {
            val point = Point.measurement("market_news_events")
                .addTag("source", "Finnhub")
                .addTag("sentiment", news.sentiment)
                .apply {
                    news.symbol?.let { addTag("related_symbol", it) }
                }
                .addField("headline", news.headline)
                .addField("summary", news.summary)
                .addField("news_id", System.currentTimeMillis()) // Simple ID generation
                .timestamp(Instant.ofEpochMilli(news.timestamp))
            
            influxDBClient.writePoint(point)
            logger.debug("Written news to InfluxDB: ${news.headline.take(50)}...")
            
        } catch (e: Exception) {
            logger.error("Failed to write news to InfluxDB", e)
        }
    }

    private fun writeEconomicDataToInfluxDB(data: ProcessedEconomicData) {
        try {
            val point = Point.measurement("economic_indicator_observations")
                .addTag("series_id", data.seriesId)
                .addTag("units", getUnitsForSeries(data.seriesId))
                .addField("value", data.value)
                .timestamp(Instant.ofEpochMilli(data.timestamp))
            
            influxDBClient.writePoint(point)
            logger.info("Written economic data to InfluxDB: ${data.seriesId} = ${data.value}")
            
        } catch (e: Exception) {
            logger.error("Failed to write economic data to InfluxDB for ${data.seriesId}", e)
        }
    }

    private fun analyzeSentiment(headline: String, summary: String): String {
        // Simple sentiment analysis based on keywords
        val text = (headline + " " + summary).lowercase()
        
        val positiveWords = listOf("gain", "rise", "up", "surge", "bull", "positive", "growth", "strong", "beat", "exceed")
        val negativeWords = listOf("fall", "drop", "down", "decline", "bear", "negative", "loss", "weak", "miss", "plunge")
        
        val positiveCount = positiveWords.count { text.contains(it) }
        val negativeCount = negativeWords.count { text.contains(it) }
        
        return when {
            positiveCount > negativeCount -> "POSITIVE"
            negativeCount > positiveCount -> "NEGATIVE"
            else -> "NEUTRAL"
        }
    }

    private fun getUnitsForSeries(seriesId: String): String {
        return when (seriesId) {
            "VIXCLS" -> "Index"
            "SP500", "NASDAQCOM" -> "Index"
            "DGS10" -> "Percent"
            "CPIAUCSL" -> "Index"
            "UNRATE" -> "Percent"
            else -> "Unknown"
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

// Entry point function for Kotlin
fun main(args: Array<String>) {
    RiskCalculationEngine.main(args)
}
