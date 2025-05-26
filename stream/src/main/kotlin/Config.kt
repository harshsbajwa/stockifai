package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Configuration
@EnableScheduling
@EnableAsync
@EnableRetry
class StreamConfig {
    @ConfigurationProperties(prefix = "app")
    data class AppProperties(
        val stocks: String = "AAPL,GOOGL,MSFT",
        val finnhub: FinnhubProperties = FinnhubProperties(),
        val fred: FredProperties = FredProperties(),
        val collection: CollectionProperties = CollectionProperties(),
        val market: MarketProperties = MarketProperties()
    )

    data class FinnhubProperties(
        val api: ApiProperties = ApiProperties(),
        val baseUrl: String = "https://finnhub.io/api/v1"
    )

    data class FredProperties(
        val api: ApiProperties = ApiProperties(),
        val baseUrl: String = "https://api.stlouisfed.org"
    )

    data class ApiProperties(
        val key: String? = null
    )

    data class CollectionProperties(
        val enabled: Boolean = true
    )

    data class MarketProperties(
        val hours: MarketHoursProperties = MarketHoursProperties()
    )

    data class MarketHoursProperties(
        val only: Boolean = false
    )

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.producer.retries:3}")
    private val retries: Int = 3

    @Value("\${spring.kafka.producer.batch-size:16384}")
    private val batchSize: Int = 16384

    @Value("\${spring.kafka.producer.linger-ms:10}")
    private val lingerMs: Int = 10

    @Value("\${spring.kafka.producer.buffer-memory:33554432}")
    private val bufferMemory: Long = 33554432

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            findAndRegisterModules()
        }
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val configProps = mutableMapOf<String, Any>()
        
        // Basic Kafka Producer Configuration
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        
        // Performance and Reliability Configuration
        configProps[ProducerConfig.RETRIES_CONFIG] = retries
        configProps[ProducerConfig.BATCH_SIZE_CONFIG] = batchSize
        configProps[ProducerConfig.LINGER_MS_CONFIG] = lingerMs
        configProps[ProducerConfig.BUFFER_MEMORY_CONFIG] = bufferMemory
        configProps[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "snappy"
        
        // Idempotence and Ordering
        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configProps[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        configProps[ProducerConfig.RETRY_BACKOFF_MS_CONFIG] = 100
        
        // Timeouts
        configProps[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 30000
        configProps[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 120000
        
        // Acknowledgments
        configProps[ProducerConfig.ACKS_CONFIG] = "all" // Wait for all replicas
        
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        val template = KafkaTemplate(producerFactory())
        template.defaultTopic = "default-topic"
        return template
    }

    @Bean
    fun webClient(): WebClient {
        val connectionProvider = ConnectionProvider.builder("custom")
            .maxConnections(50)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build()

        val httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(10))

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { configurer -> 
                configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) 
            }
            .build()
    }
}

@Configuration
class KafkaTopicConfig {
    
    @Bean
    fun kafkaTopics(): Map<String, TopicConfiguration> {
        return mapOf(
            "stock_prices" to TopicConfiguration(
                partitions = 6,
                replicationFactor = 1,
                retentionMs = 86400000L, // 24 hours
                description = "Real-time stock price data"
            ),
            "economic_indicators" to TopicConfiguration(
                partitions = 3,
                replicationFactor = 1,
                retentionMs = 604800000L, // 7 days
                description = "Economic indicators from FRED"
            ),
            "intraday_data" to TopicConfiguration(
                partitions = 3,
                replicationFactor = 1,
                retentionMs = 259200000L, // 3 days
                description = "Intraday candlestick data"
            ),
            "volatility_indicators" to TopicConfiguration(
                partitions = 2,
                replicationFactor = 1,
                retentionMs = 86400000L, // 24 hours
                description = "Market volatility indicators"
            )
        )
    }
}

data class TopicConfiguration(
    val partitions: Int,
    val replicationFactor: Short,
    val retentionMs: Long,
    val description: String
)
