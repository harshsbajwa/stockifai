package com.harshsbajwa.stockifai.spark

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.Durations
import org.apache.spark.streaming.api.java.JavaInputDStream
import org.apache.spark.streaming.api.java.JavaStreamingContext
import org.apache.spark.streaming.kafka010.*

data class StockData(
    val symbol: String,
    val price: Double,
    val volume: Long,
    val timestamp: Long
)

object SparkProcessorApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Initializing Spark Processor Application...")

        // 1. Configure Spark
        val sparkConf = SparkConf()
            .setAppName("SparkProcessor")

        // 2. Initialize StreamingContext
        val batchIntervalSeconds = System.getenv("SPARK_BATCH_INTERVAL_SECONDS")?.toLongOrNull() ?: 5L
        val jssc = JavaStreamingContext(sparkConf, Durations.seconds(batchIntervalSeconds))

        val checkpointDir = System.getenv("SPARK_CHECKPOINT_DIR") ?: "./checkpoint_spark_processor"
        jssc.checkpoint(checkpointDir)
        println("Spark checkpoint directory set to: $checkpointDir")

        // 3. Kafka Configuration
        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "kafka:9093"
        val kafkaGroupId = System.getenv("KAFKA_CONSUMER_GROUP_ID") ?: "stock-spark-processor-group"
        val kafkaAutoOffsetReset = System.getenv("KAFKA_AUTO_OFFSET_RESET") ?: "latest"

        val kafkaParams = mapOf<String, Any>(
            "bootstrap.servers" to kafkaBootstrapServers,
            "key.deserializer" to StringDeserializer::class.java,
            "value.deserializer" to StringDeserializer::class.java,
            "group.id" to kafkaGroupId,
            "auto.offset.reset" to kafkaAutoOffsetReset,
            "enable.auto.commit" to false
        )
        println("Kafka consumer configured with servers: $kafkaBootstrapServers, group ID: $kafkaGroupId")

        // Define topics
        val topicsEnv = System.getenv("KAFKA_TOPICS") ?: "stock_prices,economic_indicators"
        val topics = topicsEnv.split(",").map { it.trim() }.toSet()
        println("Subscribing to Kafka topics: $topics")

        // 4. Create Direct Kafka Stream
        val stream: JavaInputDStream<ConsumerRecord<String, String>> = KafkaUtils.createDirectStream(
            jssc,
            LocationStrategies.PreferConsistent(),
            ConsumerStrategies.Subscribe<String, String>(topics, kafkaParams)
        )
        println("Kafka DStream created.")

        // 5. Process the stream
        stream.foreachRDD { rdd, time ->
            if (!rdd.isEmpty) {
                println("--- Processing Batch for time: $time, Messages: ${rdd.count()} ---")

                // Get offset ranges for this RDD
                val offsetRanges = (rdd.rdd() as HasOffsetRanges).offsetRanges()

                val processedData = rdd.map { record ->
                    println("  Raw message from topic ${record.topic()}: Key=${record.key()}, Value=${record.value()}")

                    // TODO: Implement parsing
                    // TODO: Perform calculations

                    // For now, just pass through the value
                    "Processed: ${record.value()}"
                }

                // TODO: Store results in InfluxDB
                // TODO: Store results/raw data in Cassandra

                // Manually commit offsets to Kafka after successful processing of the RDD
                (stream.inputDStream() as CanCommitOffsets).commitAsync(offsetRanges)
                println("--- Offsets committed for batch $time ---")
            } else {
                println("--- No messages in batch for time: $time ---")
            }
        }

        // 6. Start the streaming context and await termination
        println("Starting Spark Streaming context...")
        jssc.start()
        println("Spark Streaming context started. Waiting for termination...")
        try {
            jssc.awaitTermination()
        } catch (e: InterruptedException) {
            println("Spark Streaming context interrupted: ${e.message}")
            Thread.currentThread().interrupt()
        } finally {
            println("Stopping Spark Streaming context...")
            jssc.stop(true, true)
            println("Spark Streaming context stopped.")
        }
    }

    // TODOs
    // fun calculateVolatility(data: StockData): Double { ... }
    // fun writeToInfluxDB(dataStream: JavaRDD<ProcessedType>) { ... }
    // fun writeToCassandra(dataStream: JavaRDD<ConsumerRecord<String, String>>) { ... }
}