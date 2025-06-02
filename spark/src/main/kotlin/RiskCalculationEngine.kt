package com.harshsbajwa.stockifai.processing

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.CqlSessionBuilder
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.spark.SparkConf
import org.apache.spark.api.java.function.VoidFunction2
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.avro.functions.from_avro
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types.*
import org.apache.spark.sql.api.java.UDF1
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class CalculatedRiskMetrics(
    val symbol: String,
    val timestamp: Long,
    val close: Double,
    val volume: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val priceChange: Double?,
    val priceChangePercent: Double?,
    val volatility: Double?,
    val riskScore: Double?,
    val trend: String = "NEUTRAL",
    val volumeAverage: Double?,
    val calculationDate: String
)

data class ProcessedNews(
    val id: String = UUID.randomUUID().toString(),
    val headline: String,
    val summary: String,
    val sentiment: String,
    val timestamp: Long,
    val source: String?,
    val relatedSymbol: String?
)

data class ProcessedEconomicData(
    val seriesId: String,
    val value: Double,
    val observationDate: String,
    val processingTimestamp: Long
)

object RiskCalculationEngine {
    private val logger = LoggerFactory.getLogger(RiskCalculationEngine::class.java)
    private lateinit var influxDBClient: InfluxDBClient
    private lateinit var cassandraSession: CqlSession

    // Schemas for Avro deserialization
    private val stockCandleStructType = StructType(arrayOf(
        StructField("symbol", DataTypes.StringType, true, Metadata.empty()),
        StructField("open", DataTypes.DoubleType, true, Metadata.empty()),
        StructField("high", DataTypes.DoubleType, true, Metadata.empty()),
        StructField("low", DataTypes.DoubleType, true, Metadata.empty()),
        StructField("close", DataTypes.DoubleType, true, Metadata.empty()),
        StructField("volume", DataTypes.LongType, true, Metadata.empty()),
        StructField("timestamp", DataTypes.TimestampType, true, Metadata.empty())
    ))

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Initializing StockifAI Risk Calculation Engine...")
        try {
            initializeDatabaseConnections()
            val spark = createSparkSession()
            val queries = startStreamingQueries(spark)
            queries.forEach { it.awaitTermination() }
        } catch (e: Exception) {
            logger.error("Failed to start Risk Calculation Engine", e)
            exitProcess(1)
        } finally {
            cleanup()
        }
    }

    private fun createSparkSession(): SparkSession {
        val sparkVersion = System.getenv("SPARK_VERSION") ?: "3.5.5"
        val sparkConf = SparkConf()
            .setAppName("StockifAI-RiskCalculationEngine")
            .set("spark.sql.adaptive.enabled", "true")
            .set("spark.sql.adaptive.coalescePartitions.enabled", "true")
            .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .set("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
            .set("spark.sql.streaming.checkpointLocation", "/tmp/spark-checkpoint-risk-engine")
            .set("spark.sql.avro.datetimeRebaseModeInRead", "CORRECTED")
            .set("spark.sql.avro.datetimeRebaseModeInWrite", "CORRECTED")

        if (sparkConf.get("spark.master", null) == null) {
            sparkConf.setMaster("local[*]")
        }

        return SparkSession.builder()
            .config(sparkConf)
            .config("spark.jars.packages", "org.apache.spark:spark-avro_2.12:$sparkVersion")
            .getOrCreate()
    }

    private fun initializeDatabaseConnections() {
        // InfluxDB Cloud
        val influxUrl = System.getenv("INFLUXDB_URL") ?: "https://us-east-1-1.aws.cloud2.influxdata.com"
        val influxToken = System.getenv("INFLUXDB_TOKEN")
        val influxOrg = System.getenv("INFLUXDB_ORG")
        val defaultInfluxBucket = System.getenv("INFLUXDB_DEFAULT_BUCKET") ?: "stockdata"

        logger.info("Initializing InfluxDB Cloud client: URL=$influxUrl, Org=$influxOrg")
        influxDBClient = InfluxDBClientFactory.create(influxUrl, influxToken.toCharArray(), influxOrg, defaultInfluxBucket)
        try {
            logger.info("InfluxDB client initialized. Ping: ${influxDBClient.ping()}")
        } catch (e: Exception) {
            logger.error("Failed to check InfluxDB health: ${e.message}", e)
            exitProcess(1)
        }

        // Datastax AstraDB
        val astraSecureBundlePath = System.getenv("ASTRA_SECURE_CONNECT_BUNDLE_PATH")
        val astraClientId = System.getenv("ASTRA_CLIENT_ID")
        val astraClientSecret = System.getenv("ASTRA_CLIENT_SECRET")
        val keyspaceName = System.getenv("CASSANDRA_KEYSPACE") ?: "stock_keyspace"
        val astraLocalDatacenter = System.getenv("ASTRA_LOCAL_DATACENTER")

        if (astraSecureBundlePath.isNullOrBlank() || astraClientId.isNullOrBlank() || astraClientSecret.isNullOrBlank()) {
            logger.error("AstraDB connection details (ASTRA_SECURE_CONNECT_BUNDLE_PATH, ASTRA_CLIENT_ID, ASTRA_CLIENT_SECRET) are not fully provided. Exiting.")
            exitProcess(1)
        }
        logger.info("Initializing AstraDB client: Keyspace=$keyspaceName, BundlePath=$astraSecureBundlePath")

        val sessionBuilder: CqlSessionBuilder = CqlSession.builder()
            .withCloudSecureConnectBundle(java.nio.file.Paths.get(astraSecureBundlePath))
            .withAuthCredentials(astraClientId, astraClientSecret)
            .withKeyspace(keyspaceName)

        astraLocalDatacenter?.let { if (it.isNotBlank()) sessionBuilder.withLocalDatacenter(it) }
        cassandraSession = sessionBuilder.build()

        createCassandraTables()
        logger.info("AstraDB session initialized and tables ensured for keyspace $keyspaceName.")
    }

    private fun createCassandraTables() {
        logger.info("Ensuring Cassandra (AstraDB) tables exist...")
        cassandraSession.execute("""
            CREATE TABLE IF NOT EXISTS stock_summaries (
                symbol text PRIMARY KEY,
                last_timestamp bigint,
                current_price double,
                latest_volume bigint,
                latest_volatility double,
                latest_risk_score double,
                latest_trend text,
                calculation_date text,
                price_change_today double,
                price_change_percent_today double
            )
        """)
        logger.info("Table 'stock_summaries' ensured.")

        cassandraSession.execute("""
            CREATE TABLE IF NOT EXISTS economic_indicator_summaries (
                indicator text PRIMARY KEY,
                last_timestamp bigint,
                latest_value double,
                observation_date text
            )
        """)
        logger.info("Table 'economic_indicator_summaries' ensured.")

        cassandraSession.execute("""
            CREATE TABLE IF NOT EXISTS market_news (
                id uuid,
                date_bucket text,
                timestamp bigint,
                headline text,
                summary text,
                sentiment text,
                source text,
                related_symbol text,
                PRIMARY KEY (date_bucket, timestamp, id)
            ) WITH CLUSTERING ORDER BY (timestamp DESC, id ASC)
        """)
        logger.info("Table 'market_news' ensured.")
        logger.info("AstraDB tables schema check complete.")
    }

    class AvroDeserializeUDF(private val schemaRegistryUrl: String, private val topicName: String) : UDF1<ByteArray?, Row?> {
        @Transient private var schemaRegistryClient: CachedSchemaRegistryClient? = null
        @Transient private var avroDeserializer: KafkaAvroDeserializer? = null

        private fun getDeserializer(): KafkaAvroDeserializer {
            if (avroDeserializer == null) {
                if (schemaRegistryClient == null) {
                    schemaRegistryClient = CachedSchemaRegistryClient(schemaRegistryUrl, 100)
                }
                avroDeserializer = KafkaAvroDeserializer(schemaRegistryClient)
            }
            return avroDeserializer!!
        }

        override fun call(avroBytes: ByteArray?): Row? {
            if (avroBytes == null) return null
            return try {
                val genericRecord = getDeserializer().deserialize(topicName, avroBytes) as? GenericRecord
                genericRecord?.let {
                    val symbol = it.get("symbol")?.toString()
                    val open = it.get("open") as? Double
                    val high = it.get("high") as? Double
                    val low = it.get("low") as? Double
                    val close = it.get("close") as? Double
                    val volume = it.get("volume") as? Long
                    val timestampMillis = it.get("timestamp") as? Long
                    val sqlTimestamp = timestampMillis?.let { ts -> Timestamp(ts) }

                    if (listOf(symbol, open, high, low, close, volume, sqlTimestamp).all { fld -> fld != null }) {
                        GenericRowWithSchema(arrayOf(symbol, open, high, low, close, volume, sqlTimestamp), stockCandleStructType)
                    } else {
                        logger.warn("Null field found in Avro record for topic $topicName: Symbol=$symbol, O=$open, H=$high, L=$low, C=$close, V=$volume, TS=$timestampMillis")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to deserialize Avro message for topic $topicName: ${e.message}", e)
                null
            }
        }
    }

    private fun createAvroDeserializerUDF(schemaRegistryUrl: String, topic: String): UserDefinedFunction {
        return udf(AvroDeserializeUDF(schemaRegistryUrl, topic), stockCandleStructType)
    }

    private fun startStreamingQueries(spark: SparkSession): List<StreamingQuery> {
        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
        val schemaRegistryUrl = System.getenv("SCHEMA_REGISTRY_URL") ?: "http://localhost:8081"
        
        val queries = mutableListOf<StreamingQuery>()

        try {
            val ohlcvQuery = processOHLCVData(spark, kafkaBootstrapServers, schemaRegistryUrl)
            queries.add(ohlcvQuery)
            logger.info("Successfully started OHLCV processing query.")
        } catch (e: Exception) {
            logger.error("Failed to start OHLCV processing query", e)
        }

        try {
            val newsQuery = processMarketNews(spark, kafkaBootstrapServers, schemaRegistryUrl)
            queries.add(newsQuery)
            logger.info("Successfully started News processing query.")
        } catch (e: Exception) {
            logger.error("Failed to start News processing query", e)
        }

        try {
            val economicQuery = processEconomicData(spark, kafkaBootstrapServers, schemaRegistryUrl)
            queries.add(economicQuery)
            logger.info("Successfully started Economic Data processing query.")
        } catch (e: Exception) {
            logger.error("Failed to start Economic Data processing query", e)
        }
        logger.info("Started ${queries.size} streaming queries.")
        return queries
    }

    private fun processOHLCVData(spark: SparkSession, kafkaBootstrapServers: String, schemaRegistryUrl: String): StreamingQuery {
        val ohlcvRawStream = spark.readStream()
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaBootstrapServers)
            .option("subscribe", "finnhub-ohlcv-data")
            .option("startingOffsets", "earliest")
            .option("failOnDataLoss", "false")
            .load()

        val avroDeserUDF = createAvroDeserializerUDF(schemaRegistryUrl, "finnhub-ohlcv-data")

        val deserializedStream = ohlcvRawStream
            .select(col("value"), col("timestamp").alias("kafka_ingest_time"))
            .filter(col("value").isNotNull)
            .withColumn("deserialized_struct", avroDeserUDF.apply(col("value")))
            .filter(col("deserialized_struct").isNotNull)
            .select("deserialized_struct.*")

        val ohlcvStream = deserializedStream
            .filter(
                col("symbol").isNotNull.and(col("symbol").notEqual(""))
                .and(col("close").isNotNull.and(col("close").gt(0)))
                .and(col("open").isNotNull.and(col("open").gt(0)))
                .and(col("high").isNotNull.and(col("high").gt(0)))
                .and(col("low").isNotNull.and(col("low").gt(0)))
                .and(col("high").geq(col("low")))
                .and(col("timestamp").isNotNull)
            )
            .withWatermark("timestamp", "10 minutes")

        return ohlcvStream.writeStream()
            .foreachBatch(VoidFunction2 { batchDF: Dataset<Row>, batchId: Long ->
                if (!batchDF.isEmpty) {
                    logger.info("Processing OHLCV batch $batchId with ${batchDF.count()} records.")
                    processOHLCVBatch(batchDF)
                }
            })
            .trigger(Trigger.ProcessingTime(30, TimeUnit.SECONDS))
            .option("checkpointLocation", "/tmp/spark-checkpoint-ohlcv")
            .queryName("OHLCVProcessingStream")
            .start()
    }

    private fun processMarketNews(spark: SparkSession, kafkaBootstrapServers: String, schemaRegistryUrl: String): StreamingQuery {
        val newsRawStream = spark.readStream()
            .format("kafka")
            .option("kafka.bootstrap.servers", kafkaBootstrapServers)
            .option("subscribe", "finnhub-market-news-data")
            .option("startingOffsets", "latest")
            .option("failOnDataLoss", "false")
            .load()

        val newsStream = newsRawStream
            .filter(col("value").isNotNull)
            .select(
                from_avro(
                    col("value"), 
                    getMarketNewsSchemaString(),
                    mapOf("mode" to "PERMISSIVE", "columnNameOfCorruptRecord" to "_corrupt_record")
                ).alias("data")
            )
            .select("data.*")
            .filter(col("_corrupt_record").isNull)
            .filter(col("headline").isNotNull.and(col("summary").isNotNull).and(col("datetime").isNotNull))
            .withColumn("event_timestamp", (col("datetime").divide(1000)).cast(DataTypes.TimestampType))
            .withWatermark("event_timestamp", "30 minutes")

        return newsStream.writeStream()
            .foreachBatch(VoidFunction2 { batchDF: Dataset<Row>, batchId: Long ->
                if (!batchDF.isEmpty) {
                    logger.info("Processing News batch $batchId with ${batchDF.count()} records.")
                    processNewsBatch(batchDF)
                }
            })
            .trigger(Trigger.ProcessingTime(1, TimeUnit.MINUTES))
            .option("checkpointLocation", "/tmp/spark-checkpoint-news")
            .queryName("NewsProcessingStream")
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

        val economicStream = economicRawStream
            .filter(col("value").isNotNull)
            .select(
                from_avro(
                    col("value"),
                    getEconomicObservationSchemaString(),
                    mapOf("mode" to "PERMISSIVE", "columnNameOfCorruptRecord" to "_corrupt_record")
                ).alias("avro_data")
            )
            .filter(col("avro_data._corrupt_record").isNull)
            .select(
                col("avro_data.seriesId").alias("seriesId"),
                col("avro_data.observationDate").alias("observationDate"),
                col("avro_data.value").alias("value_str")
            )
            .filter(col("value_str").isNotNull.and(col("value_str").notEqual(".")))
            .withColumn("parsedValue", col("value_str").cast(DataTypes.DoubleType))
            .filter(col("parsedValue").isNotNull)

        return economicStream.writeStream()
            .foreachBatch(VoidFunction2 { batchDF: Dataset<Row>, batchId: Long ->
                if (!batchDF.isEmpty) {
                    logger.info("Processing Economic Data batch $batchId with ${batchDF.count()} records.")
                    processEconomicBatch(batchDF)
                }
            })
            .trigger(Trigger.ProcessingTime(5, TimeUnit.MINUTES))
            .option("checkpointLocation", "/tmp/spark-checkpoint-economic")
            .queryName("EconomicDataProcessingStream")
            .start()
    }

    private fun processOHLCVBatch(batchDF: Dataset<Row>) {
        val rowsList = batchDF.collectAsList()
        rowsList.forEach { row ->
            try {
                val symbol = row.getAs<String>("symbol")
                if (symbol.isNullOrBlank()) return@forEach

                val timestampValue = row.getAs<java.sql.Timestamp>("timestamp")?.time ?: System.currentTimeMillis()
                val close = row.getAs<Double>("close")
                val open = row.getAs<Double>("open")
                val high = row.getAs<Double>("high")
                val low = row.getAs<Double>("low")
                val volume = row.getAs<Long>("volume")

                if (listOf(close, open, high, low, volume).any { it == null } || close <= 0 || open <= 0 || high <= 0 || low <= 0) {
                    logger.warn("Invalid OHLCV data for $symbol at $timestampValue, skipping.")
                    return@forEach
                }

                val volatility = if (high > low && close > 0) (high - low) / close else 0.0
                val priceChange = close - open
                val priceChangePercent = if (open > 0) (priceChange / open) * 100.0 else 0.0
                val riskScore = volatility * 100.0
                val trend = when {
                    priceChangePercent > 1.0 -> "BULLISH"
                    priceChangePercent < -1.0 -> "BEARISH"
                    else -> "NEUTRAL"
                }

                val metrics = CalculatedRiskMetrics(
                    symbol = symbol, timestamp = timestampValue, close = close, volume = volume,
                    open = open, high = high, low = low, priceChange = priceChange,
                    priceChangePercent = priceChangePercent, volatility = volatility,
                    riskScore = riskScore, trend = trend,
                    volumeAverage = volume.toDouble(),
                    calculationDate = LocalDate.now().toString()
                )
                
                writeOhlcvToInfluxDB(metrics)
                writeCalculatedMetricsToInfluxDB(metrics)
                updateStockSummaryInCassandra(metrics)
                
            } catch (e: Exception) {
                val symbolForError = try { row.getAs<String>("symbol") } catch (_: Exception) { "UNKNOWN_SYMBOL" }
                logger.error("Error processing OHLCV row for $symbolForError: ${e.message}", e)
            }
        }
    }

    private fun processNewsBatch(batchDF: Dataset<Row>) {
        val rowsList = batchDF.collectAsList()
        rowsList.forEach { row ->
            try {
                val headline = row.getAs<String>("headline")
                val summary = row.getAs<String>("summary")
                val timestampValue = row.getAs<Long>("datetime")
                val source = row.getAs<String>("source")
                val relatedSymbol = row.getAs<String>("related")

                if (headline.isNullOrBlank() || summary.isNullOrBlank() || timestampValue == null) {
                     logger.warn("Skipping news item due to missing headline, summary, or datetime.")
                    return@forEach
                }

                val processedNews = ProcessedNews(
                    headline = headline, summary = summary,
                    sentiment = analyzeSentiment(headline, summary),
                    timestamp = timestampValue,
                    source = source ?: "Unknown",
                    relatedSymbol = relatedSymbol
                )
                writeNewsToCassandra(processedNews)
            } catch (e: Exception) {
                logger.error("Error processing news row: ${e.message}", e)
            }
        }
    }

    private fun processEconomicBatch(batchDF: Dataset<Row>) {
        val rowsList = batchDF.collectAsList()
        rowsList.forEach { row ->
            try {
                val seriesId = row.getAs<String>("seriesId")
                val value = row.getAs<Double>("parsedValue")
                val observationDate = row.getAs<String>("observationDate")

                if (seriesId.isNullOrBlank()) {
                    logger.warn("Skipping economic data due to missing seriesId.")
                    return@forEach
                }
                
                val processedEconomic = ProcessedEconomicData(
                    seriesId = seriesId, value = value, observationDate = observationDate,
                    processingTimestamp = System.currentTimeMillis()
                )
                writeEconomicObservationToInfluxDB(processedEconomic)
                updateEconomicIndicatorSummaryInCassandra(processedEconomic)
            } catch (e: Exception) {
                val seriesForError = try { row.getAs<String>("seriesId") } catch (_: Exception) { "UNKNOWN_SERIES" }
                logger.error("Error processing economic data for $seriesForError: ${e.message}", e)
            }
        }
    }
    
    private fun getInfluxBucket(bucketType: String): String {
        return when (bucketType.lowercase()) {
            "stock" -> System.getenv("INFLUXDB_STOCK_TIMESERIES_BUCKET") ?: "stock_timeseries"
            "economic" -> System.getenv("INFLUXDB_ECONOMIC_TIMESERIES_BUCKET") ?: "economic_timeseries"
            else -> {
                logger.warn("Unknown bucket type '$bucketType', using default.")
                System.getenv("INFLUXDB_DEFAULT_BUCKET") ?: "stockdata"
            }
        }
    }

    private fun writeOhlcvToInfluxDB(metrics: CalculatedRiskMetrics) {
        try {
            val bucket = getInfluxBucket("stock")
            val org = System.getenv("INFLUXDB_ORG") ?: "YOUR_INFLUXDB_ORG"
            val point = Point.measurement("ohlcv")
                .addTag("symbol", metrics.symbol)
                .addField("open", metrics.open)
                .addField("high", metrics.high)
                .addField("low", metrics.low)
                .addField("close", metrics.close)
                .addField("volume", metrics.volume.toDouble())
                .time(metrics.timestamp, WritePrecision.MS)
            influxDBClient.getWriteApiBlocking().writePoint(bucket, org, point)
        } catch (e: Exception) {
            logger.error("InfluxDB Error (OHLCV) for ${metrics.symbol}: ${e.message}", e)
        }
    }

    private fun writeCalculatedMetricsToInfluxDB(metrics: CalculatedRiskMetrics) {
        try {
            val bucket = getInfluxBucket("stock")
            val org = System.getenv("INFLUXDB_ORG") ?: "YOUR_INFLUXDB_ORG"
            val trendNumeric = when(metrics.trend.uppercase()) {
                "BULLISH" -> 1.0
                "BEARISH" -> -1.0
                else -> 0.0
            }
            val point = Point.measurement("calculated_metrics")
                .addTag("symbol", metrics.symbol)
                .addField("volatility", metrics.volatility ?: 0.0)
                .addField("risk_score", metrics.riskScore ?: 0.0)
                .addField("price_change", metrics.priceChange ?: 0.0)
                .addField("price_change_percent", metrics.priceChangePercent ?: 0.0)
                .addField("trend_numeric", trendNumeric)
                .time(metrics.timestamp, WritePrecision.MS)
            influxDBClient.getWriteApiBlocking().writePoint(bucket, org, point)
        } catch (e: Exception) {
            logger.error("InfluxDB Error (CalculatedMetrics) for ${metrics.symbol}: ${e.message}", e)
        }
    }

    private fun updateStockSummaryInCassandra(metrics: CalculatedRiskMetrics) {
        try {
            val stmt = cassandraSession.prepare(
                """
                INSERT INTO stock_summaries 
                (symbol, last_timestamp, current_price, latest_volume, latest_volatility, 
                 latest_risk_score, latest_trend, calculation_date, 
                 price_change_today, price_change_percent_today) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            )
            cassandraSession.execute(stmt.bind(
                metrics.symbol, metrics.timestamp, metrics.close, metrics.volume,
                metrics.volatility ?: 0.0, metrics.riskScore ?: 0.0, metrics.trend,
                metrics.calculationDate, metrics.priceChange ?: 0.0, metrics.priceChangePercent ?: 0.0
            ))
        } catch (e: Exception) {
            logger.error("Cassandra Error (StockSummary) for ${metrics.symbol}: ${e.message}", e)
        }
    }

    private fun writeNewsToCassandra(news: ProcessedNews) {
        try {
            val newsId = UUID.fromString(news.id)
            val dateBucket = LocalDate.ofInstant(
                Instant.ofEpochMilli(news.timestamp), 
                ZoneId.systemDefault()
            ).toString()

            val stmt = cassandraSession.prepare(
                """
                INSERT INTO market_news 
                (id, date_bucket, timestamp, headline, summary, sentiment, source, related_symbol) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """
            )
            cassandraSession.execute(stmt.bind(
                newsId, dateBucket, news.timestamp, news.headline, news.summary,
                news.sentiment, news.source, news.relatedSymbol
            ))
        } catch (e: Exception) {
            logger.error("Cassandra Error (News) for ${news.headline.take(30)}: ${e.message}", e)
        }
    }

    private fun writeEconomicObservationToInfluxDB(data: ProcessedEconomicData) {
        try {
            val bucket = getInfluxBucket("economic")
            val org = System.getenv("INFLUXDB_ORG") ?: "YOUR_INFLUXDB_ORG"
            val observationInstant = LocalDate.parse(data.observationDate).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val point = Point.measurement("fred_observations")
                .addTag("series_id", data.seriesId)
                .addField("value", data.value)
                .time(observationInstant.toEpochMilli(), WritePrecision.MS)
            influxDBClient.getWriteApiBlocking().writePoint(bucket, org, point)
        } catch (e: Exception) {
            logger.error("InfluxDB Error (Economic) for ${data.seriesId}: ${e.message}", e)
        }
    }

    private fun updateEconomicIndicatorSummaryInCassandra(data: ProcessedEconomicData) {
        try {
            val stmt = cassandraSession.prepare(
                """
                INSERT INTO economic_indicator_summaries 
                (indicator, last_timestamp, latest_value, observation_date) 
                VALUES (?, ?, ?, ?)
                """
            )
            cassandraSession.execute(stmt.bind(
                data.seriesId, data.processingTimestamp, data.value, data.observationDate
            ))
        } catch (e: Exception) {
            logger.error("Cassandra Error (EconomicSummary) for ${data.seriesId}: ${e.message}", e)
        }
    }

    private fun analyzeSentiment(headline: String, summary: String): String {
        val text = (headline + " " + summary).lowercase()
        val positiveWords = listOf("gain", "rise", "up", "surge", "bull", "positive", "growth", "strong", "beat", "exceed", "optimistic", "rally", "booming", "record high")
        val negativeWords = listOf("fall", "drop", "down", "decline", "bear", "negative", "loss", "weak", "miss", "plunge", "pessimistic", "slump", "crisis", "recession")
        
        val positiveCount = positiveWords.count { text.contains(it) }
        val negativeCount = negativeWords.count { text.contains(it) }
        
        return when {
            positiveCount > negativeCount + 1 -> "POSITIVE"
            negativeCount > positiveCount + 1 -> "NEGATIVE"
            else -> "NEUTRAL"
        }
    }

    private fun getStockCandleSchemaString(): String {
        return """
        {
          "type": "record", "name": "StockCandle", "namespace": "com.harshsbajwa.stockifai.avro.finnhub",
          "fields": [
            {"name": "symbol", "type": "string"}, {"name": "open", "type": "double"},
            {"name": "high", "type": "double"}, {"name": "low", "type": "double"},
            {"name": "close", "type": "double"}, {"name": "volume", "type": "long"},
            {"name": "timestamp", "type": {"type": "long", "logicalType": "timestamp-millis"}}
          ]
        }
        """
    }
    private fun getMarketNewsSchemaString(): String {
        return """
        {
          "type": "record", "name": "MarketNews", "namespace": "com.harshsbajwa.stockifai.avro.finnhub",
          "fields": [
            {"name": "category", "type": "string"},
            {"name": "datetime", "type": {"type": "long", "logicalType": "timestamp-millis"}},
            {"name": "headline", "type": "string"}, {"name": "id", "type": "long"},
            {"name": "image", "type": ["null", "string"], "default": null},
            {"name": "related", "type": ["null", "string"], "default": null},
            {"name": "source", "type": "string"}, {"name": "summary", "type": "string"},
            {"name": "url", "type": "string"}
          ]
        }
        """
    }
    private fun getEconomicObservationSchemaString(): String {
        return """
        {
          "type": "record", "name": "EconomicObservation", "namespace": "com.harshsbajwa.stockifai.avro.fred",
          "fields": [
            {"name": "seriesId", "type": "string"}, {"name": "observationDate", "type": "string"},
            {"name": "value", "type": "string"}, {"name": "realTimeStart", "type": "string"},
            {"name": "realTimeEnd", "type": "string"}
          ]
        }
        """
    }

    private fun cleanup() {
        try {
            if (this::influxDBClient.isInitialized) influxDBClient.close()
            if (this::cassandraSession.isInitialized && !cassandraSession.isClosed) {
                cassandraSession.close()
            }
            logger.info("Database connections closed.")
        } catch (e: Exception) {
            logger.error("Error during cleanup", e)
        }
    }
}

// Entry point function for Kotlin
fun main(args: Array<String>) {
    RiskCalculationEngine.main(args)
}