package com.harshsbajwa.stockifai.processing

import com.datastax.oss.driver.api.core.CqlSession
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.QueryApi
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
class IntegrationTest {
    
    companion object {
        private val logger = LoggerFactory.getLogger(IntegrationTest::class.java)
        private val objectMapper: ObjectMapper = jacksonObjectMapper()
        
        private val network = Network.newNetwork()
        
        @Container
        val kafkaContainer = ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.1")
        ).withNetwork(network)
            .withNetworkAliases("kafka")
        
        @Container
        val influxDbContainer = InfluxDBContainer(
            DockerImageName.parse("influxdb:2.7")
        ).withNetwork(network)
            .withNetworkAliases("influxdb")
            .withDatabase("stockdata")
            .withUsername("admin")
            .withPassword("password")
            .withAdminToken("test-token")
            .withOrganization("stockifai")
            .withBucket("stockdata")
        
        @Container
        val cassandraContainer = CassandraContainer(
            DockerImageName.parse("cassandra:4.1")
        ).withNetwork(network)
            .withNetworkAliases("cassandra")
            .withExposedPorts(9042)
        
        private lateinit var kafkaProducer: KafkaProducer<String, String>
        private lateinit var influxDBClient: InfluxDBClient
        private lateinit var cassandraSession: CqlSession
        private lateinit var sparkSession: SparkSession
        
        @JvmStatic
        @BeforeAll
        fun setUp() {
            logger.info("Setting up test containers...")
            
            // Wait for containers to be ready
            assertTrue(kafkaContainer.isRunning, "Kafka container should be running")
            assertTrue(influxDbContainer.isRunning, "InfluxDB container should be running")
            assertTrue(cassandraContainer.isRunning, "Cassandra container should be running")
            
            setupKafka()
            setupInfluxDB()
            setupCassandra()
            setupSpark()
            
            logger.info("All test containers are ready")
        }
        
        private fun setupKafka() {
            // Create Kafka topics
            val adminProps = Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
            }
            
            AdminClient.create(adminProps).use { adminClient ->
                val topics = listOf(
                    NewTopic("stock_prices", 3, 1.toShort()),
                    NewTopic("economic_indicators", 1, 1.toShort())
                )
                adminClient.createTopics(topics).all().get(30, TimeUnit.SECONDS)
                logger.info("Kafka topics created successfully")
            }
            
            // Create Kafka producer
            val producerProps = Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.RETRIES_CONFIG, 3)
            }
            kafkaProducer = KafkaProducer(producerProps)
        }
        
        private fun setupInfluxDB() {
            val influxUrl = "http://${influxDbContainer.host}:${influxDbContainer.firstMappedPort}"
            influxDBClient = InfluxDBClientFactory.create(
                influxUrl,
                "test-token".toCharArray(),
                "stockifai"
            )
            
            // Verify connection
            val health = influxDBClient.health()
            assertTrue(health.status == com.influxdb.client.domain.HealthCheck.StatusEnum.PASS, 
                "InfluxDB should be healthy")
            logger.info("InfluxDB client initialized: $influxUrl")
        }
        
        private fun setupCassandra() {
            val contactPoint = java.net.InetSocketAddress(
                cassandraContainer.host, 
                cassandraContainer.firstMappedPort
            )
            
            cassandraSession = CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter("datacenter1")
                .build()
                
            // Create keyspace and tables
            cassandraSession.execute("""
                CREATE KEYSPACE IF NOT EXISTS stockifai
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
            """)
            
            cassandraSession.execute("USE stockifai")
            
            cassandraSession.execute("""
                CREATE TABLE IF NOT EXISTS processed_stocks (
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
            """)
            
            cassandraSession.execute("""
                CREATE TABLE IF NOT EXISTS economic_indicators (
                    indicator text,
                    timestamp bigint,
                    value double,
                    country text,
                    PRIMARY KEY (indicator, timestamp)
                ) WITH CLUSTERING ORDER BY (timestamp DESC)
            """)
            
            logger.info("Cassandra session initialized and schema created")
        }
        
        private fun setupSpark() {
            val sparkConf = SparkConf()
                .setAppName("SparkProcessorTest")
                .setMaster("local[2]")
                .set("spark.sql.adaptive.enabled", "true")
                .set("spark.sql.adaptive.coalescePartitions.enabled", "true")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
                .set("spark.sql.streaming.checkpointLocation", "/tmp/spark-checkpoint-test")
                
            sparkSession = SparkSession.builder().config(sparkConf).getOrCreate()
            sparkSession.sparkContext().setLogLevel("WARN")
            logger.info("Spark session initialized for testing")
        }
        
        @JvmStatic
        @AfterAll
        fun tearDown() {
            try {
                kafkaProducer.close()
                influxDBClient.close()
                cassandraSession.close()
                sparkSession.stop()
                logger.info("Test cleanup completed")
            } catch (e: Exception) {
                logger.warn("Error during test cleanup", e)
            }
        }
    }
    
    @BeforeEach
    fun setupEach() {
        // Set environment variables for the Processing object
        System.setProperty("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer.bootstrapServers)
        System.setProperty("INFLUXDB_URL", "http://${influxDbContainer.host}:${influxDbContainer.firstMappedPort}")
        System.setProperty("INFLUXDB_TOKEN", "test-token")
        System.setProperty("INFLUXDB_ORG", "stockifai")
        System.setProperty("INFLUXDB_BUCKET", "stockdata")
        System.setProperty("CASSANDRA_HOST", cassandraContainer.host)
        System.setProperty("CASSANDRA_PORT", cassandraContainer.firstMappedPort.toString())
        System.setProperty("CASSANDRA_KEYSPACE", "stockifai")
    }
    
    @Test
    fun `should process stock price data and write to InfluxDB and Cassandra`() {
        logger.info("Starting stock price processing test...")
        
        // Prepare test data
        val testStockData = mapOf(
            "symbol" to "AAPL",
            "regularMarketPrice" to 175.50,
            "regularMarketVolume" to 50000000,
            "timestamp" to Instant.now().toEpochMilli(),
            "regularMarketDayHigh" to 178.0,
            "regularMarketDayLow" to 173.0,
            "regularMarketOpen" to 174.0,
            "regularMarketPreviousClose" to 172.0
        )
        
        val jsonMessage = objectMapper.writeValueAsString(testStockData)
        
        // Send multiple messages to build history for calculations
        repeat(10) { i ->
            val adjustedData = testStockData.toMutableMap()
            adjustedData["regularMarketPrice"] = 175.50 + (i * 0.5) // Slight price variation
            adjustedData["regularMarketVolume"] = 50000000L + (i * 1000000L)
            adjustedData["timestamp"] = Instant.now().minusSeconds((10 - i) * 60L).toEpochMilli()
            
            val message = objectMapper.writeValueAsString(adjustedData)
            val record = ProducerRecord("stock_prices", "AAPL", message)
            kafkaProducer.send(record).get(5, TimeUnit.SECONDS)
        }
        
        logger.info("Sent 10 stock price messages to Kafka")
        
        // Start Spark streaming manually for test
        val processingInstance = TestableProcessing()
        processingInstance.initializeForTest(
            kafkaBootstrapServers = kafkaContainer.bootstrapServers,
            influxUrl = "http://${influxDbContainer.host}:${influxDbContainer.firstMappedPort}",
            influxToken = "test-token",
            influxOrg = "stockifai", 
            influxBucket = "stockdata",
            cassandraHost = cassandraContainer.host,
            cassandraPort = cassandraContainer.firstMappedPort,
            cassandraKeyspace = "stockifai"
        )
        
        val query = processingInstance.startStockPricesStreaming(sparkSession)
        
        // Let the stream process for a few seconds
        Thread.sleep(15_000)
        
        // Verify data in InfluxDB
        val influxQuery = """
            from(bucket: "stockdata")
            |> range(start: -1h)
            |> filter(fn: (r) => r._measurement == "stock_metrics")
            |> filter(fn: (r) => r.symbol == "AAPL")
        """.trimIndent()
        
        val queryApi = influxDBClient.queryApi
        val influxResults = queryApi.query(influxQuery, "stockifai")
        
        assertTrue(influxResults.isNotEmpty(), "Should have data in InfluxDB")
        logger.info("Found ${influxResults.size} records in InfluxDB")
        
        // Verify data in Cassandra
        val cassandraResults = cassandraSession.execute(
            "SELECT * FROM processed_stocks WHERE symbol = 'AAPL' LIMIT 10"
        )
        
        val stockRecords = cassandraResults.all()
        assertTrue(stockRecords.isNotEmpty(), "Should have data in Cassandra")
        
        val latestRecord = stockRecords.first()
        assertEquals("AAPL", latestRecord.getString("symbol"))
        assertTrue(latestRecord.getDouble("current_price") > 0.0)
        assertTrue(latestRecord.getDouble("volatility") >= 0.0)
        assertNotNull(latestRecord.getString("trend"))
        assertTrue(latestRecord.getDouble("risk_score") >= 0.0)
        
        logger.info("Successfully verified processed stock data in both databases")
        
        query.stop()
        processingInstance.cleanup()
    }
    
    @Test
    fun `should process economic indicator data and write to databases`() {
        logger.info("Starting economic indicator processing test...")
        
        val testIndicatorData = mapOf(
            "indicator" to "VIXCLS",
            "value" to 18.75,
            "timestamp" to Instant.now().toEpochMilli(),
            "country" to "US"
        )
        
        val jsonMessage = objectMapper.writeValueAsString(testIndicatorData)
        val record = ProducerRecord("economic_indicators", "VIXCLS", jsonMessage)
        kafkaProducer.send(record).get(5, TimeUnit.SECONDS)
        
        logger.info("Sent economic indicator message to Kafka")
        
        val processingInstance = TestableProcessing()
        processingInstance.initializeForTest(
            kafkaBootstrapServers = kafkaContainer.bootstrapServers,
            influxUrl = "http://${influxDbContainer.host}:${influxDbContainer.firstMappedPort}",
            influxToken = "test-token",
            influxOrg = "stockifai",
            influxBucket = "stockdata", 
            cassandraHost = cassandraContainer.host,
            cassandraPort = cassandraContainer.firstMappedPort,
            cassandraKeyspace = "stockifai"
        )
        
        val query = processingInstance.startEconomicIndicatorsStreaming(sparkSession)
        
        Thread.sleep(10_000)
        
        // Verify in InfluxDB
        val influxQuery = """
            from(bucket: "stockdata")
            |> range(start: -1h)
            |> filter(fn: (r) => r._measurement == "economic_indicators")
            |> filter(fn: (r) => r.indicator == "VIXCLS")
        """.trimIndent()
        
        val queryApi = influxDBClient.queryApi
        val influxResults = queryApi.query(influxQuery, "stockifai")
        assertTrue(influxResults.isNotEmpty(), "Should have economic indicator in InfluxDB")
        
        // Verify in Cassandra
        val cassandraResults = cassandraSession.execute(
            "SELECT * FROM economic_indicators WHERE indicator = 'VIXCLS'"
        )
        
        val indicatorRecords = cassandraResults.all()
        assertTrue(indicatorRecords.isNotEmpty(), "Should have economic indicator in Cassandra")
        
        val record = indicatorRecords.first()
        assertEquals("VIXCLS", record.getString("indicator"))
        assertEquals(18.75, record.getDouble("value"))
        assertEquals("US", record.getString("country"))
        
        logger.info("Successfully verified economic indicator data in both databases")
        
        query.stop()
        processingInstance.cleanup()
    }
    
    @Test 
    fun `should handle malformed JSON gracefully`() {
        logger.info("Testing malformed JSON handling...")
        
        val malformedJson = """{"symbol": "INVALID", "price": "not_a_number"}"""
        val record = ProducerRecord("stock_prices", "INVALID", malformedJson)
        kafkaProducer.send(record).get(5, TimeUnit.SECONDS)
        
        val processingInstance = TestableProcessing()
        processingInstance.initializeForTest(
            kafkaBootstrapServers = kafkaContainer.bootstrapServers,
            influxUrl = "http://${influxDbContainer.host}:${influxDbContainer.firstMappedPort}",
            influxToken = "test-token",
            influxOrg = "stockifai",
            influxBucket = "stockdata",
            cassandraHost = cassandraContainer.host,
            cassandraPort = cassandraContainer.firstMappedPort,
            cassandraKeyspace = "stockifai"
        )
        
        val query = processingInstance.startStockPricesStreaming(sparkSession)
        
        Thread.sleep(5_000)
        
        // Should not crash and continue processing
        assertTrue(query.isActive, "Stream should still be active after malformed message")
        
        query.stop()
        processingInstance.cleanup()
        
        logger.info("Successfully handled malformed JSON without crashing")
    }
    
    /**
     * Testable version of Processing object that allows dependency injection
     */
    class TestableProcessing {
        private lateinit var influxDBClient: InfluxDBClient
        private lateinit var cassandraSession: CqlSession
        
        fun initializeForTest(
            kafkaBootstrapServers: String,
            influxUrl: String,
            influxToken: String,
            influxOrg: String,
            influxBucket: String,
            cassandraHost: String,
            cassandraPort: Int,
            cassandraKeyspace: String
        ) {
            // Initialize InfluxDB
            influxDBClient = InfluxDBClientFactory.create(influxUrl, influxToken.toCharArray(), influxOrg)
            
            // Initialize Cassandra
            cassandraSession = CqlSession.builder()
                .addContactPoint(java.net.InetSocketAddress(cassandraHost, cassandraPort))
                .withLocalDatacenter("datacenter1")
                .withKeyspace(cassandraKeyspace)
                .build()
        }
        
        fun startStockPricesStreaming(spark: SparkSession) = 
            spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaContainer.bootstrapServers)
                .option("subscribe", "stock_prices")
                .option("startingOffsets", "earliest")
                .option("maxOffsetsPerTrigger", "100")
                .load()
                .select(org.apache.spark.sql.functions.col("value").cast("string").alias("json_data"))
                .writeStream()
                .foreachBatch { batch, batchId ->
                    // Simulate the processing logic from your Processing object
                    if (!batch.isEmpty) {
                        val rows = batch.collectAsList()
                        rows.forEach { row ->
                            try {
                                val jsonData = row.getAs<String>("json_data")
                                val stockData = parseStockData(jsonData)
                                stockData?.let { 
                                    val processed = processStockData(it)
                                    // Write to databases (simplified for test)
                                    logger.debug("Processed: ${processed.symbol} at ${processed.currentPrice}")
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to process record: ${e.message}")
                            }
                        }
                    }
                }
                .trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime(Duration.ofSeconds(2)))
                .start()
        
        fun startEconomicIndicatorsStreaming(spark: SparkSession) =
            spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaContainer.bootstrapServers)
                .option("subscribe", "economic_indicators")
                .option("startingOffsets", "earliest")
                .load()
                .select(org.apache.spark.sql.functions.col("value").cast("string").alias("json_data"))
                .writeStream()
                .foreachBatch { batch, batchId ->
                    if (!batch.isEmpty) {
                        val rows = batch.collectAsList()
                        rows.forEach { row ->
                            try {
                                val jsonData = row.getAs<String>("json_data")
                                val indicator = parseEconomicIndicator(jsonData)
                                indicator?.let {
                                    logger.debug("Processed indicator: ${it.indicator} = ${it.value}")
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to process indicator: ${e.message}")
                            }
                        }
                    }
                }
                .trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime(Duration.ofSeconds(2)))
                .start()
        
        private fun parseStockData(json: String): StockData? = 
            Processing.parseStockData(json)
            
        private fun parseEconomicIndicator(json: String): EconomicIndicator? = 
            Processing.parseEconomicIndicator(json)
            
        private fun processStockData(stockData: StockData): ProcessedStockData = 
            Processing.processStockData(stockData)
        
        fun cleanup() {
            try {
                if (::influxDBClient.isInitialized) influxDBClient.close()
                if (::cassandraSession.isInitialized) cassandraSession.close()
            } catch (e: Exception) {
                logger.warn("Error during cleanup", e)
            }
        }
    }
}