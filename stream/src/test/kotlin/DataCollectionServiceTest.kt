package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.stream.config.AppConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    classes = [Application::class, AppConfig::class, DataCollectionService::class],
    properties =
        [
            "app.stocks=TESTAAPL",
            "app.alphavantage.api.key=TEST_AV_KEY",
            "app.fred.api.key=TEST_FRED_KEY",
        ],
)
@ActiveProfiles("test")
class DataCollectionServiceTest {
    @Autowired private lateinit var dataCollectionService: DataCollectionService

    // KafkaTemplate will be autoconfigured by Spring Boot to use the Testcontainer's Kafka
    @Autowired private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired private lateinit var objectMapper: ObjectMapper

    private lateinit var mockWebServer: MockWebServer
    private lateinit var kafkaConsumer: KafkaConsumer<String, String>

    companion object {
        @Container
        val kafkaContainer =
            ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.9.1"),
            )

        @JvmStatic
        @DynamicPropertySource
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers)
        }
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val consumerProps = Properties()
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaContainer.bootstrapServers
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] =
            "test-data-collection-consumer-${UUID.randomUUID()}"
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
            StringDeserializer::class.java.name
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            StringDeserializer::class.java.name
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        kafkaConsumer = KafkaConsumer(consumerProps)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        kafkaConsumer.close()
    }

    private fun consumeMessages(
        topic: String,
        expectedCount: Int,
        timeoutSeconds: Long = 5,
    ): List<String> {
        kafkaConsumer.subscribe(listOf(topic))
        val records = mutableListOf<String>()
        val endTime = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis()
        while (System.currentTimeMillis() < endTime && records.size < expectedCount) {
            val consumedRecords = kafkaConsumer.poll(Duration.ofMillis(100))
            consumedRecords.forEach { records.add(it.value()) }
        }
        return records
    }

    @Test
    fun `collectStockData should fetch and send data to Kafka`() {
        val mockYahooResponse =
            """
            {
                "quoteResponse": {
                    "result": [
                        {
                            "symbol": "TESTAAPL",
                            "regularMarketPrice": 150.0,
                            "regularMarketVolume": 100000,
                            "regularMarketChangePercent": 0.5,
                            "regularMarketDayHigh": 152.0,
                            "regularMarketDayLow": 148.0
                        }
                    ]
                }
            }
            """.trimIndent()

        mockWebServer.enqueue(
            MockResponse().setBody(mockYahooResponse).addHeader("Content-Type", "application/json"),
        )

        dataCollectionService.collectStockData()

        val messages = consumeMessages("stock_prices", 1)
        assertTrue(messages.isNotEmpty(), "No message received from stock_prices topic")

        val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
        assertEquals("stock_quote", sentMessage.messageType)
        val quoteData = objectMapper.convertValue(sentMessage.data, YahooFinanceQuote::class.java)
        assertEquals("TESTAAPL", quoteData.symbol)
        assertEquals(150.0, quoteData.regularMarketPrice)
    }

    @Test
    fun `collectMarketVolatility should fetch VIX and send to Kafka`() {
        val mockVixResponse =
            """
            {
                "quoteResponse": {
                    "result": [
                        {
                            "symbol": "^VIX",
                            "regularMarketPrice": 20.5
                        }
                    ]
                }
            }
            """.trimIndent()

        mockWebServer.enqueue(
            MockResponse().setBody(mockVixResponse).addHeader("Content-Type", "application/json"),
        )

        dataCollectionService.collectMarketVolatility()

        val messages = consumeMessages("economic_indicators", 1)
        assertTrue(messages.isNotEmpty(), "No message received from economic_indicators topic for VIX")

        val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
        assertEquals("volatility_indicator", sentMessage.messageType)
        val indicatorData = objectMapper.convertValue(sentMessage.data, EconomicIndicator::class.java)
        assertEquals("VIX", indicatorData.indicator)
        assertEquals(20.5, indicatorData.value)
    }
}
