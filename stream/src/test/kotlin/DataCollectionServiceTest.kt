package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.stream.config.AppConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    classes = [Application::class, AppConfig::class, DataCollectionService::class]
)
@ActiveProfiles("test")
class DataCollectionServiceTest {
    @MockBean
    private lateinit var scheduledAnnotationBeanPostProcessor: ScheduledAnnotationBeanPostProcessor

    @Autowired private lateinit var dataCollectionService: DataCollectionService
    @Autowired private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @Autowired private lateinit var objectMapper: ObjectMapper

    private lateinit var kafkaConsumer: KafkaConsumer<String, String>

    companion object {
        private val logger = LoggerFactory.getLogger(DataCollectionServiceTest::class.java)

        @Container
        val kafkaContainer =
            ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.9.1"),
            )

        private lateinit var staticMockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun beforeAllTests() {
            staticMockWebServer = MockWebServer()
            staticMockWebServer.start()
            logger.info("MockWebServer started on port: ${staticMockWebServer.port}")

            // Explicitly create topics
            val adminClientConfig = Properties()
            adminClientConfig[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaContainer.bootstrapServers
            AdminClient.create(adminClientConfig).use { adminClient ->
                val topicsToCreate = listOf(
                    NewTopic("stock_prices", 1, 1.toShort()),
                    NewTopic("economic_indicators", 1, 1.toShort()),
                    NewTopic("intraday_data", 1, 1.toShort())
                )
                try {
                    adminClient.createTopics(topicsToCreate).all().get(30, TimeUnit.SECONDS)
                    logger.info("Kafka topics created: stock_prices, economic_indicators, intraday_data")
                } catch (e: Exception) {
                    logger.error("Failed to create Kafka topics, tests might fail.", e)
                    // It's possible topics exist from a previous partial run, check if they exist
                    val existingTopics = adminClient.listTopics().names().get(10, TimeUnit.SECONDS)
                    logger.info("Existing topics: $existingTopics")
                    if (!existingTopics.containsAll(topicsToCreate.map { it.name() } )) {
                        // throw IllegalStateException("Could not ensure Kafka topics are created", e)
                    }
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun afterAllTests() {
            staticMockWebServer.shutdown()
            logger.info("MockWebServer shut down.")
        }

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers)

            val mockUrl = "http://localhost:${staticMockWebServer.port}"
            logger.info("Configuring mock base URL: $mockUrl")
            registry.add("app.yahoo.baseUrl") { mockUrl }
            registry.add("app.fred.baseUrl") { mockUrl }
            registry.add("app.alphavantage.baseUrl") { mockUrl }

            registry.add("app.stocks") { "TESTAAPL" }
            registry.add("app.alphavantage.api.key") { "TEST_AV_KEY" }
            registry.add("app.fred.api.key") { "TEST_FRED_KEY" }
            registry.add("spring.task.scheduling.enabled") { "false" } 
        }
    }

    @BeforeEach
    fun setUp() {
        // Drain any pending requests from mockWebServer from previous tests if any
        while(staticMockWebServer.takeRequest(0, TimeUnit.SECONDS) != null) {
            // draining
        }

        val consumerProps = Properties()
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaContainer.bootstrapServers
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = "test-dcs-consumer-${UUID.randomUUID()}"
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        kafkaConsumer = KafkaConsumer(consumerProps)
    }

    @AfterEach
    fun tearDown() {
        kafkaConsumer.close()
    }

    private fun consumeMessages(
        topic: String,
        expectedCount: Int,
        timeoutSeconds: Long = 10,
    ): List<String> {
        kafkaConsumer.subscribe(listOf(topic))
        val records = mutableListOf<String>()
        val endTime = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis()
        var polls = 0
        while (System.currentTimeMillis() < endTime && records.size < expectedCount) {
            val consumedRecords = kafkaConsumer.poll(Duration.ofMillis(200))
            if (!consumedRecords.isEmpty) {
                logger.debug("Consumed ${consumedRecords.count()} records from $topic on poll ${++polls}")
                consumedRecords.forEach { records.add(it.value()) }
            } else if (System.currentTimeMillis() > endTime && records.size < expectedCount) {
                logger.warn("Timeout waiting for messages on $topic. Expected $expectedCount, got ${records.size}")
            }
        }
        if (records.size < expectedCount) {
             logger.warn("Failed to consume $expectedCount messages from $topic within $timeoutSeconds seconds. Got ${records.size}.")
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

        staticMockWebServer.enqueue(
            MockResponse()
                .setBody(mockYahooResponse)
                .addHeader("Content-Type", "application/json"),
        )
        logger.info("Mock response enqueued for collectStockData")

        dataCollectionService.collectStockData()
        logger.info("collectStockData invoked")

        val messages = consumeMessages("stock_prices", 1, timeoutSeconds = 15)
        assertTrue(messages.isNotEmpty(), "No message received from stock_prices topic after 15s. MockWebServer requests: ${staticMockWebServer.requestCount}")
        if (messages.isNotEmpty()) {
            val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
            assertEquals("stock_quote", sentMessage.messageType)
            val quoteData = objectMapper.convertValue(sentMessage.data, com.harshsbajwa.stockifai.stream.YahooFinanceQuote::class.java)
            assertEquals("TESTAAPL", quoteData.symbol)
            assertEquals(150.0, quoteData.regularMarketPrice)
            logger.info("Successfully validated message on stock_prices")
        } else {
            // Check if request was made
            val request = staticMockWebServer.takeRequest(1, TimeUnit.SECONDS)
            if (request == null) {
                logger.error("MockWebServer did not receive a request for collectStockData.")
            } else {
                logger.info("MockWebServer received request: ${request.path}")
            }
        }
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

        staticMockWebServer.enqueue(
            MockResponse()
                .setBody(mockVixResponse)
                .addHeader("Content-Type", "application/json"),
        )
        logger.info("Mock response enqueued for collectMarketVolatility")

        dataCollectionService.collectMarketVolatility()
        logger.info("collectMarketVolatility invoked")

        val messages = consumeMessages("economic_indicators", 1, timeoutSeconds = 15)
        assertTrue(
            messages.isNotEmpty(),
            "No message received from economic_indicators topic for VIX after 15s. MockWebServer requests: ${staticMockWebServer.requestCount}",
        )

        if (messages.isNotEmpty()) {
            val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
            assertEquals("volatility_indicator", sentMessage.messageType)
            val indicatorData =
                objectMapper.convertValue(sentMessage.data, com.harshsbajwa.stockifai.stream.EconomicIndicator::class.java)
            assertEquals("VIX", indicatorData.indicator)
            assertEquals(20.5, indicatorData.value)
            logger.info("Successfully validated message on economic_indicators for VIX")
        } else {
            // Check if request was made
            val request = staticMockWebServer.takeRequest(1, TimeUnit.SECONDS) 
            if (request == null) {
                logger.error("MockWebServer did not receive a request for collectMarketVolatility.")
            } else {
                logger.info("MockWebServer received request: ${request.path}")
            }
        }
    }
}