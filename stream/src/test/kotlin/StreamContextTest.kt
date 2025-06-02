package com.harshsbajwa.stockifai.stream

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [Application::class])
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.collection.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "app.finnhub.api.key=test-key",
    "app.fred.api.key=test-key"
])
class StreamContextTest {

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Test
    fun contextLoads() {
        println("Stream application context loaded successfully.")
    }
}