package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.stream.config.TestStreamConfig
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CompletableFuture
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [Application::class])
@Import(TestStreamConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.collection.enabled=true",
    "app.market.hours.only=false",
    "app.stocks=AAPL,SPY",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "app.finnhub.api.key=test-key",
    "app.fred.api.key=test-key"
])
class DataCollectionServiceTest {

    @Autowired
    private lateinit var dataCollectionService: DataCollectionService

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `service should initialize without errors`() {
        // Setup proper mock
        @Suppress("UNCHECKED_CAST")
        val mockSendResult = Mockito.mock(SendResult::class.java) as SendResult<String, String>
        val mockFuture = CompletableFuture.completedFuture(mockSendResult)
        
        Mockito.`when`(kafkaTemplate.send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn(mockFuture)

        // When/Then - Service should start successfully
        assertNotNull(dataCollectionService)
        assertNotNull(objectMapper)
        assertTrue(true) // If we get here, initialization was successful
    }

    @Test
    fun `application context loads successfully`() {
        // This test verifies the Spring application context can be loaded
        assertNotNull(dataCollectionService)
    }
}