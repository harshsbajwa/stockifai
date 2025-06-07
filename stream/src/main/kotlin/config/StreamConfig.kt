package com.harshsbajwa.stockifai.stream.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.netty.channel.ChannelOption
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
@EnableScheduling
@EnableAsync
@EnableRetry
@Profile("!test")
class StreamConfig {
    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(StreamConfig::class.java)
    }

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    @Value("\${app.schema-registry-url:http://localhost:8081}")
    private lateinit var schemaRegistryUrl: String

    @Value("\${spring.kafka.producer.retries:3}")
    private val retries: Int = 3

    @Value("\${spring.kafka.producer.batch-size:16384}")
    private val batchSize: Int = 16384

    @Value("\${spring.kafka.producer.linger-ms:10}")
    private val lingerMs: Int = 10

    @Value("\${spring.kafka.producer.buffer-memory:33554432}")
    private val bufferMemory: Long = 33554432

    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            findAndRegisterModules()
        }

    @Bean
    fun avroProducerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
        configProps["schema.registry.url"] = schemaRegistryUrl
        configProps[ProducerConfig.RETRIES_CONFIG] = retries
        configProps[ProducerConfig.BATCH_SIZE_CONFIG] = batchSize
        configProps[ProducerConfig.LINGER_MS_CONFIG] = lingerMs
        configProps[ProducerConfig.BUFFER_MEMORY_CONFIG] = bufferMemory
        configProps[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "snappy"
        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configProps[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        configProps[ProducerConfig.RETRY_BACKOFF_MS_CONFIG] = 100
        configProps[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 30000
        configProps[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 120000
        configProps[ProducerConfig.ACKS_CONFIG] = "all"
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun avroKafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(avroProducerFactory())

    @Bean
    fun schemaRegistryHealthCheck(): WebClient =
        WebClient
            .builder()
            .baseUrl(schemaRegistryUrl)
            .build()

    @EventListener
    @Async
    fun onApplicationReady(event: ApplicationReadyEvent) {
        validateSchemaRegistry()
    }

    private fun validateSchemaRegistry() {
        try {
            schemaRegistryHealthCheck()
                .get()
                .uri("/subjects")
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess { logger.info("Schema Registry is accessible: {}", schemaRegistryUrl) }
                .doOnError { logger.error("Schema Registry is not accessible: {}", it.message) }
                .subscribe()
        } catch (e: Exception) {
            logger.error("Failed to validate Schema Registry connection", e)
        }
    }

    @Bean
    fun webClient(): WebClient {
        val connectionProvider =
            ConnectionProvider
                .builder("custom")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build()

        val httpClient =
            HttpClient
                .create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(10))

        return WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)
            }.build()
    }
}
