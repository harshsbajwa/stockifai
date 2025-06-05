package com.harshsbajwa.stockifai.stream.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.web.reactive.function.client.WebClient

@TestConfiguration
@Profile("test")
class TestStreamConfig {
    @Bean
    @Primary
    fun testObjectMapper(): ObjectMapper =
        ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            findAndRegisterModules()
        }

    @Bean
    @Primary
    fun testProducerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.ACKS_CONFIG] = "all"
        configProps[ProducerConfig.RETRIES_CONFIG] = 0
        configProps[ProducerConfig.BATCH_SIZE_CONFIG] = 16384
        configProps[ProducerConfig.LINGER_MS_CONFIG] = 1
        configProps[ProducerConfig.BUFFER_MEMORY_CONFIG] = 33554432
        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = false
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    @Primary
    fun testKafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory)

    @Bean
    @Primary
    fun testWebClient(): WebClient =
        WebClient
            .builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024)
            }.build()
}
