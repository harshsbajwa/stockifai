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
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    classes = [Application::class, AppConfig::class, DataCollectionService::class],
)
@ActiveProfiles("test")
class DataCollectionServiceTest {
    @MockBean
    private lateinit var scheduledAnnotationBeanPostProcessor: ScheduledAnnotationBeanPostProcessor

    @Autowired private lateinit var dataCollectionService: DataCollectionService

    @Suppress("unused")
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
            staticMockWebServer.start(0) // Start on a random available port
            logger.info("MockWebServer started on port: ${staticMockWebServer.port}")

            val adminClientConfig = Properties()
            adminClientConfig[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaContainer.bootstrapServers
            AdminClient.create(adminClientConfig).use { adminClient ->
                val topicsToCreate =
                    listOf(
                        NewTopic("stock_prices", 1, 1.toShort()),
                        NewTopic("economic_indicators", 1, 1.toShort()),
                        NewTopic("intraday_data", 1, 1.toShort()),
                    )
                try {
                    adminClient.createTopics(topicsToCreate).all().get(30, TimeUnit.SECONDS)
                    logger.info("Kafka topics created: stock_prices, economic_indicators, intraday_data")
                } catch (e: Exception) {
                    val existingTopics = adminClient.listTopics().names().get(10, TimeUnit.SECONDS)
                    if (!existingTopics.containsAll(topicsToCreate.map { it.name() })) {
                         logger.error("Failed to create Kafka topics, tests might fail.", e)
                    } else {
                        logger.warn("Topics likely already existed. Proceeding with tests.", e)
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
            logger.info("Configuring mock base URL for Finnhub & FRED: $mockUrl")
            registry.add("app.finnhub.baseUrl") { mockUrl }
            registry.add("app.fred.baseUrl") { mockUrl }


            registry.add("app.stocks") { "TESTFINN" } 
            registry.add("app.finnhub.api.key") { "TEST_FINNHUB_KEY" }
            registry.add("app.fred.api.key") { "TEST_FRED_KEY" }
            registry.add("spring.task.scheduling.enabled") { "false" }
        }
    }

    @BeforeEach
    fun setUp() {
        // Drain any pending requests
        while (staticMockWebServer.takeRequest(0, TimeUnit.MILLISECONDS) != null) { /*_*/ }

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
            }
        }
        if (records.size < expectedCount) {
            logger.warn(
                "Timeout or insufficient messages on $topic. Expected $expectedCount, got ${records.size}. Total requests to MockWebServer: ${staticMockWebServer.requestCount}",
            )
        }
        return records
    }

    @Test
    fun `collectStockData should fetch from Finnhub and send to Kafka`() {
        val mockFinnhubResponse =
            """
            {
                "c": 175.0,
                "h": 178.0,
                "l": 172.0,
                "o": 173.0,
                "pc": 170.0,
                "t": ${Instant.now().epochSecond},
                "dp": 2.94 
            }
            """.trimIndent()

        staticMockWebServer.enqueue(
            MockResponse()
                .setBody(mockFinnhubResponse)
                .addHeader("Content-Type", "application/json"),
        )
        logger.info("Mock Finnhub response enqueued for collectStockData (TESTFINN)")

        dataCollectionService.collectStockData()
        logger.info("collectStockData invoked for Finnhub")

        val messages = consumeMessages("stock_prices", 1, timeoutSeconds = 15)
        assertTrue(
            messages.isNotEmpty(),
            "No message received from stock_prices topic after 15s. MockWebServer requests: ${staticMockWebServer.requestCount}",
        )

        val request = staticMockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request, "MockWebServer did not receive a request for TESTFINN quote.")
        assertTrue(request.path!!.contains("/quote?symbol=TESTFINN&token=TEST_FINNHUB_KEY"))

        val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
        assertEquals("stock_quote", sentMessage.messageType)
        val quoteData = objectMapper.convertValue(sentMessage.data, StockQuoteData::class.java)
        assertEquals("TESTFINN", quoteData.symbol)
        assertEquals(175.0, quoteData.regularMarketPrice)
        assertEquals(null, quoteData.regularMarketVolume) // Volume is null from Finnhub /quote
        assertEquals(2.94, quoteData.regularMarketChangePercent)
        assertEquals(178.0, quoteData.regularMarketDayHigh)
        assertEquals(173.0, quoteData.regularMarketOpen)
        assertEquals(170.0, quoteData.previousClosePrice)
        logger.info("Successfully validated message on stock_prices from Finnhub")
    }

    @Test
    fun `collectMarketVolatility from FRED should fetch VIXCLS and send to Kafka`() {
        val testDate = "2025-05-20"
        val testVixValue = 15.75
        val mockFredVixclsResponse =
            """
            {
              "realtime_start": "2025-05-23",
              "realtime_end": "2025-05-23",
              "observation_start": "1990-01-02",
              "observation_end": "9999-12-31",
              "units": "lin",
              "output_type": 1,
              "file_type": "json",
              "order_by": "observation_date",
              "sort_order": "desc",
              "count": 1,
              "offset": 0,
              "limit": 1,
              "observations": [
                {
                  "realtime_start": "2025-05-23",
                  "realtime_end": "2025-05-23",
                  "date": "$testDate",
                  "value": "$testVixValue"
                }
              ]
            }
            """.trimIndent()

        staticMockWebServer.enqueue(
            MockResponse()
                .setBody(mockFredVixclsResponse)
                .addHeader("Content-Type", "application/json"),
        )
        logger.info("Mock FRED response enqueued for VIXCLS")

        dataCollectionService.collectMarketVolatility()
        logger.info("collectMarketVolatility invoked for FRED VIXCLS")

        val messages = consumeMessages("economic_indicators", 1, timeoutSeconds = 15)
        assertTrue(
            messages.isNotEmpty(),
            "No message received from economic_indicators for VIXCLS. MockWebServer requests: ${staticMockWebServer.requestCount}",
        )

        val request = staticMockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request, "MockWebServer did not receive a request for FRED VIXCLS.")
        val requestUrl = request.requestUrl
        assertNotNull(requestUrl, "Request URL from MockWebServer is null")

        assertTrue(
            requestUrl.encodedPath.endsWith("/fred/series/observations"),
            "Request path segment should be /fred/series/observations. Actual: ${requestUrl.encodedPath}"
        )
        assertEquals(
            "VIXCLS",
            requestUrl.queryParameter("series_id"),
            "Query parameter 'series_id' should be 'VIXCLS'. Actual: ${requestUrl.queryParameter("series_id")}"
        )
        assertEquals(
            "TEST_FRED_KEY",
            requestUrl.queryParameter("api_key"),
            "Query parameter 'api_key' is not matching."
        )
        assertEquals("json", requestUrl.queryParameter("file_type"))
        assertEquals("1", requestUrl.queryParameter("limit"))
        assertEquals("desc", requestUrl.queryParameter("sort_order"))


        val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
        assertEquals("volatility_indicator", sentMessage.messageType)
        val indicatorData =
            objectMapper.convertValue(sentMessage.data, EconomicIndicator::class.java)
        assertEquals("VIXCLS", indicatorData.indicator)
        assertEquals(testVixValue, indicatorData.value)
        assertEquals(testDate, indicatorData.date)
        assertNotNull(indicatorData.timestamp)
        logger.info("Successfully validated VIXCLS message on economic_indicators from FRED")
    }
    
    @Test
    fun `collectFinnhubIntradayCandleData should fetch SPY candles from Finnhub`() {
        val mockCandleResponse = """
            {
                "c": [200.5, 201.0],
                "h": [201.5, 201.2],
                "l": [200.0, 200.8],
                "o": [200.2, 200.5],
                "s": "ok",
                "t": [${Instant.now().minusSeconds(300).epochSecond}, ${Instant.now().epochSecond}],
                "v": [1000, 1200]
            }
        """.trimIndent()

        staticMockWebServer.enqueue(
            MockResponse().setBody(mockCandleResponse).addHeader("Content-Type", "application/json")
        )
        logger.info("Mock Finnhub candle response for SPY enqueued.")

        dataCollectionService.collectFinnhubIntradayCandleData()
        logger.info("collectFinnhubIntradayCandleData invoked.")

        val messages = consumeMessages("intraday_data", 1, timeoutSeconds = 15)
        assertTrue(messages.isNotEmpty(), "No message received for intraday_data (SPY candles). MockWebServer requests: ${staticMockWebServer.requestCount}")

        val request = staticMockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request, "MockWebServer did not receive a request for SPY candles.")
        assertTrue(request.path!!.contains("/stock/candle?symbol=SPY&resolution=5"))
        assertTrue(request.path!!.contains("&token=TEST_FINNHUB_KEY"))

        val sentMessage = objectMapper.readValue(messages[0], StockMessage::class.java)
        assertEquals("intraday_data", sentMessage.messageType)
        @Suppress("UNCHECKED_CAST")
        val dataMap = sentMessage.data as Map<String, Any>
        assertEquals("SPY", dataMap["symbol"])
        assertEquals(mockCandleResponse, dataMap["data"]) // Should be the raw JSON string
        logger.info("Successfully validated SPY candle data message from Finnhub.")
    }

     @Test
    fun `collectStockData handles empty or invalid Finnhub response gracefully`() {
        // Simulate Finnhub returning an empty JSON object, which it does for some errors/invalid symbols
        val mockEmptyResponse = "{}"
        staticMockWebServer.enqueue(
            MockResponse().setBody(mockEmptyResponse).addHeader("Content-Type", "application/json")
        )
        logger.info("Mock empty Finnhub response enqueued for collectStockData (TESTFINN_EMPTY)")

        dataCollectionService.collectStockData()
        logger.info("collectStockData invoked for Finnhub with empty response scenario")

        val request = staticMockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request, "MockWebServer did not receive a request for TESTFINN quote (empty response test).")
        assertTrue(request.path!!.contains("/quote?symbol=TESTFINN&token=TEST_FINNHUB_KEY"))

        // Check that no message was sent to Kafka for this invalid response
        val messages = consumeMessages("stock_prices", 0, timeoutSeconds = 2) // Expect 0 messages quickly
        assertTrue(messages.isEmpty(), "A message was unexpectedly sent to stock_prices for an empty Finnhub response.")
        logger.info("Validated that empty/invalid Finnhub response does not lead to Kafka message.")
    }
}