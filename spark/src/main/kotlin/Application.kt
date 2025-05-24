package com.harshsbajwa.stockifai.spark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaRDD
import org.apache.spark.streaming.Durations
import org.apache.spark.streaming.api.java.JavaInputDStream
import org.apache.spark.streaming.api.java.JavaStreamingContext
import org.apache.spark.streaming.kafka010.*
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.math.pow
import kotlin.math.sqrt

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
    val volumeAverage: Double
)

data class EconomicIndicator(
    val indicator: String,
    val value: Double,
    val timestamp: Long,
    val country: String? = null
)

// Wrapper for incoming Kafka messages
data class StockMessage(
    val messageType: String,
    val data: Map<String, Any>,
    val timestamp: Long
)

object SparkProcessorApplication {
    private val logger = LoggerFactory.getLogger(SparkProcessorApplication::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    
    // In-memory storage for calculations (in production, use Redis or similar)
    private val priceHistory = mutableMapOf<String, MutableList<Double>>()
    private val volumeHistory = mutableMapOf<String, MutableList<Long>>()
    
    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Initializing Spark Processor Application...")

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
            .apply {
                // Set master to local if not set (for development)
                if (!contains("spark.master")) {
                    setMaster("local[*]")
                }
            }
    }
    
    private fun createStreamingContext(sparkConf: SparkConf): JavaStreamingContext {
        val batchIntervalSeconds = System.getenv("SPARK_BATCH_INTERVAL_SECONDS")?.toLongOrNull() ?: 5L
        val jssc = JavaStreamingContext(sparkConf, Durations.seconds(batchIntervalSeconds))
        
        val checkpointDir = System.getenv("SPARK_CHECKPOINT_DIR") ?: "./checkpoint_spark_processor"
        jssc.checkpoint(checkpointDir)
        logger.info("Spark checkpoint directory set to: $checkpointDir")
        
        return jssc
    }
    
    private fun createKafkaStream(jssc: JavaStreamingContext): JavaInputDStream<ConsumerRecord<String, String>> {
        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
        val kafkaGroupId = System.getenv("KAFKA_CONSUMER_GROUP_ID") ?: "stock-spark-processor-group"
        val kafkaAutoOffsetReset = System.getenv("KAFKA_AUTO_OFFSET_RESET") ?: "latest"
        
        val kafkaParams = mapOf<String, Any>(
            "bootstrap.servers" to kafkaBootstrapServers,
            "key.deserializer" to StringDeserializer::class.java,
            "value.deserializer" to StringDeserializer::class.java,
            "group.id" to kafkaGroupId,
            "auto.offset.reset" to kafkaAutoOffsetReset,
            "enable.auto.commit" to false,
            "session.timeout.ms" to 30000,
            "heartbeat.interval.ms" to 10000,
            "max.poll.records" to 1000
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
                    val offsetRanges = (rdd.rdd() as HasOffsetRanges).offsetRanges()
                    
                    // Separate processing by topic
                    val stockPriceRecords = rdd.filter { it.topic() == "stock_prices" }
                    val economicIndicatorRecords = rdd.filter { it.topic() == "economic_indicators" }
                    
                    if (!stockPriceRecords.isEmpty) {
                        processStockPriceData(stockPriceRecords)
                    }
                    
                    if (!economicIndicatorRecords.isEmpty) {
                        processEconomicIndicatorData(economicIndicatorRecords)
                    }
                    
                    // Commit offsets after successful processing
                    (stream.inputDStream() as CanCommitOffsets).commitAsync(offsetRanges)
                    logger.info("Offsets committed for batch $time")
                    
                } catch (e: Exception) {
                    logger.error("Error processing batch for time: $time", e)
                }
            } else {
                logger.debug("No messages in batch for time: $time")
            }
        }
    }
    
    private fun processStockPriceData(rdd: JavaRDD<ConsumerRecord<String, String>>) {
        val processedData = rdd.mapPartitions { partition ->
            val results = mutableListOf<ProcessedStockData>()
            
            partition.forEach { record ->
                try {
                    val stockData = parseStockData(record.value())
                    if (stockData != null) {
                        val processedStock = processStockData(stockData)
                        results.add(processedStock)
                        logger.debug("Processed stock data for ${stockData.symbol}: $processedStock")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to process stock record: ${record.value()}", e)
                }
            }
            
            results.iterator()
        }
        
        // Store results (implement based on your storage requirements)
        storeProcessedStockData(processedData)
    }
    
    private fun processEconomicIndicatorData(rdd: JavaRDD<ConsumerRecord<String, String>>) {
        val processedData = rdd.mapPartitions { partition ->
            val results = mutableListOf<EconomicIndicator>()
            
            partition.forEach { record ->
                try {
                    val indicator = parseEconomicIndicator(record.value())
                    if (indicator != null) {
                        results.add(indicator)
                        logger.debug("Processed economic indicator: ${indicator.indicator} = ${indicator.value}")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to process economic indicator record: ${record.value()}", e)
                }
            }
            
            results.iterator()
        }
        
        // Store results
        storeEconomicIndicatorData(processedData)
    }
    
    private fun parseStockData(json: String): StockData? {
        return try {
            // Parse the StockMessage wrapper first
            val message = objectMapper.readValue<StockMessage>(json)
            val data = message.data
            
            // Extract stock data from the nested structure
            StockData(
                symbol = data["symbol"] as String,
                price = (data["regularMarketPrice"] as Number).toDouble(),
                volume = (data["regularMarketVolume"] as Number).toLong(),
                timestamp = message.timestamp,
                high = (data["regularMarketDayHigh"] as? Number)?.toDouble(),
                low = (data["regularMarketDayLow"] as? Number)?.toDouble()
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse stock data JSON: $json", e)
            null
        }
    }
    
    private fun parseEconomicIndicator(json: String): EconomicIndicator? {
        return try {
            // Parse the StockMessage wrapper first
            val message = objectMapper.readValue<StockMessage>(json)
            val data = message.data
            
            EconomicIndicator(
                indicator = data["indicator"] as String,
                value = (data["value"] as Number).toDouble(),
                timestamp = message.timestamp
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse economic indicator JSON: $json", e)
            null
        }
    }
    
    private fun processStockData(stockData: StockData): ProcessedStockData {
        val symbol = stockData.symbol
        
        // Update price history
        val prices = priceHistory.getOrPut(symbol) { mutableListOf() }
        prices.add(stockData.price)
        if (prices.size > 100) prices.removeAt(0) // Keep only last 100 prices
        
        // Update volume history
        val volumes = volumeHistory.getOrPut(symbol) { mutableListOf() }
        volumes.add(stockData.volume)
        if (volumes.size > 100) volumes.removeAt(0) // Keep only last 100 volumes
        
        // Calculate metrics
        val volatility = calculateVolatility(prices)
        val priceChange = if (prices.size > 1) {
            stockData.price - prices[prices.size - 2]
        } else 0.0
        val volumeAverage = volumes.average()
        
        return ProcessedStockData(
            symbol = symbol,
            currentPrice = stockData.price,
            volume = stockData.volume,
            timestamp = stockData.timestamp,
            volatility = volatility,
            priceChange = priceChange,
            volumeAverage = volumeAverage
        )
    }
    
    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val mean = prices.average()
        val variance = prices.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
    
    private fun storeProcessedStockData(data: JavaRDD<ProcessedStockData>) {
        // TODO: Implement InfluxDB storage
        data.collect().forEach { processedStock ->
            logger.info("Storing processed stock data: $processedStock")
            // writeToInfluxDB(processedStock)
        }
    }
    
    private fun storeEconomicIndicatorData(data: JavaRDD<EconomicIndicator>) {
        // TODO: Implement Cassandra storage
        data.collect().forEach { indicator ->
            logger.info("Storing economic indicator: $indicator")
            // writeToCassandra(indicator)
        }
    }
    
    private fun startStreamingContext(jssc: JavaStreamingContext) {
        logger.info("Starting Spark Streaming context...")
        
        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown hook triggered, stopping Spark Streaming context...")
            jssc.stop(true, true)
            logger.info("Spark Streaming context stopped.")
        })
        
        jssc.start()
        logger.info("Spark Streaming context started. Waiting for termination...")
        
        try {
            jssc.awaitTermination()
        } catch (e: InterruptedException) {
            logger.warn("Spark Streaming context interrupted", e)
            Thread.currentThread().interrupt()
        }
    }
    
    // TODO: Implement these methods based on your storage requirements
    private fun writeToInfluxDB(data: ProcessedStockData) {
        // Implementation for InfluxDB storage
    }
    
    private fun writeToCassandra(data: EconomicIndicator) {
        // Implementation for Cassandra storage
    }
}