package com.harshsbajwa.stockifai.api.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.api.AnalysisApiApplication
import com.harshsbajwa.stockifai.api.dto.ApiResponse
import com.harshsbajwa.stockifai.api.dto.StockDataResponse
import com.harshsbajwa.stockifai.api.model.ProcessedStock
import com.harshsbajwa.stockifai.api.model.StockPrimaryKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.cassandra.core.CassandraOperations
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import kotlin.test.*

@Testcontainers
@SpringBootTest(
    classes = [AnalysisApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class StockApiIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var cassandraOperations: CassandraOperations

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        @Container
        val kafkaContainer = ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.1")
        )

        @Container
        val cassandraContainer = CassandraContainer(
            DockerImageName.parse("cassandra:4.1.9")
        ).withExposedPorts(9042)

        @Container
        val influxDBContainer = GenericContainer(
            DockerImageName.parse("influxdb:2.7")
        ).withExposedPorts(8086)
            .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
            .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "testuser")
            .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "testpassword")
            .withEnv("DOCKER_INFLUXDB_INIT_ORG", "testorg")
            .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "testbucket")
            .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "testtoken123!")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers)
            
            registry.add("spring.data.cassandra.contact-points") { cassandraContainer.host }
            registry.add("spring.data.cassandra.port") { cassandraContainer.getMappedPort(9042) }
            registry.add("spring.data.cassandra.local-datacenter") { cassandraContainer.localDatacenter }
            registry.add("spring.data.cassandra.keyspace-name") { "test_keyspace" }
            
            registry.add("influxdb.url") {
                "http://${influxDBContainer.host}:${influxDBContainer.getMappedPort(8086)}"
            }
            registry.add("influxdb.token") { "testtoken123!" }
            registry.add("influxdb.database") { "testbucket" }
        }
    }

    @BeforeEach
    fun setUp() {
        // Create test data
        val testStock = ProcessedStock(
            primaryKey = StockPrimaryKey("AAPL", Instant.now().toEpochMilli()),
            currentPrice = 150.25,
            volume = 1000000L,
            volatility = 0.25,
            priceChange = 2.50,
            priceChangePercent = 1.69,
            volumeAverage = 950000.0,
            riskScore = 35.5,
            trend = "BULLISH",
            support = 148.0,
            resistance = 152.0
        )

        cassandraOperations.insert(testStock)
    }

    @Test
    fun `should get stock data successfully`() {
        // When
        val response = restTemplate.exchange(
            "http://localhost:$port/api/v1/stocks/AAPL",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<StockDataResponse>>() {}
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.success)
        assertEquals("AAPL", response.body!!.data?.symbol)
        assertEquals(150.25, response.body!!.data?.currentPrice)
        assertEquals("BULLISH", response.body!!.data?.trend)
    }

    @Test
    fun `should return 404 for non-existent stock`() {
        // When
        val response = restTemplate.exchange(
            "http://localhost:$port/api/v1/stocks/INVALID",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<StockDataResponse>>() {}
        )

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(response.body)
        assertFalse(response.body!!.success)
        assertNull(response.body!!.data)
    }

    @Test
    fun `should get market overview successfully`() {
        // When
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/market/overview",
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val apiResponse = objectMapper.readTree(response.body)
        assertTrue(apiResponse["success"].asBoolean())
        assertTrue(apiResponse["data"]["totalStocks"].asInt() >= 0)
    }

    @Test
    fun `should get all stocks with pagination`() {
        // When
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/stocks?page=0&size=10",
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val apiResponse = objectMapper.readTree(response.body)
        assertTrue(apiResponse["success"].asBoolean())
        assertTrue(apiResponse["data"]["data"].isArray)
        assertEquals(0, apiResponse["data"]["page"].asInt())
        assertEquals(10, apiResponse["data"]["size"].asInt())
    }

    @Test
    fun `health endpoint should return OK`() {
        // When
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/health",
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        
        val healthResponse = objectMapper.readTree(response.body)
        assertEquals("UP", healthResponse["status"].asText())
        assertEquals("stockifai-api", healthResponse["service"].asText())
    }
}