package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.ProcessedStock
import com.harshsbajwa.stockifai.api.model.StockPrimaryKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

@Testcontainers
@DataCassandraTest
@ActiveProfiles("test")
class StockRepositoryTest {

    @Autowired
    private lateinit var stockRepository: StockRepository

    companion object {
        @Container
        val cassandraContainer = CassandraContainer(
            DockerImageName.parse("cassandra:4.1.9")
        )

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.cassandra.contact-points") { cassandraContainer.host }
            registry.add("spring.data.cassandra.port") { cassandraContainer.getMappedPort(9042) }
            registry.add("spring.data.cassandra.local-datacenter") { cassandraContainer.localDatacenter }
            registry.add("spring.data.cassandra.keyspace-name") { "test_keyspace" }
        }
    }

    private lateinit var testStocks: List<ProcessedStock>

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        
        testStocks = listOf(
            ProcessedStock(
                primaryKey = StockPrimaryKey("AAPL", now.toEpochMilli()),
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
            ),
            ProcessedStock(
                primaryKey = StockPrimaryKey("AAPL", now.minus(1, ChronoUnit.HOURS).toEpochMilli()),
                currentPrice = 147.75,
                volume = 800000L,
                volatility = 0.22,
                priceChange = -1.25,
                priceChangePercent = -0.84,
                volumeAverage = 920000.0,
                riskScore = 32.0,
                trend = "BEARISH",
                support = 145.0,
                resistance = 150.0
            ),
            ProcessedStock(
                primaryKey = StockPrimaryKey("GOOGL", now.toEpochMilli()),
                currentPrice = 2800.0,
                volume = 500000L,
                volatility = 0.30,
                priceChange = -15.0,
                priceChangePercent = -0.53,
                volumeAverage = 480000.0,
                riskScore = 45.0,
                trend = "BEARISH",
                support = 2750.0,
                resistance = 2850.0
            )
        )

        stockRepository.saveAll(testStocks)
    }

    @Test
    fun `findBySymbolOrderByTimestampDesc should return stocks for symbol ordered by timestamp`() {
        // When
        val result = stockRepository.findBySymbolOrderByTimestampDesc(
            "AAPL", 
            PageRequest.of(0, 10)
        )

        // Then
        assertEquals(2, result.content.size)
        // Should be ordered by timestamp DESC
        assertTrue(result.content[0].primaryKey.timestamp > result.content[1].primaryKey.timestamp)
        result.content.forEach { stock ->
            assertEquals("AAPL", stock.primaryKey.symbol)
        }
    }

    @Test
    fun `findBySymbolAndTimestampBetween should return stocks within time range`() {
        // Given
        val now = Instant.now()
        val startTime = now.minus(2, ChronoUnit.HOURS).toEpochMilli()
        val endTime = now.plus(1, ChronoUnit.HOURS).toEpochMilli()

        // When
        val result = stockRepository.findBySymbolAndTimestampBetween(
            "AAPL",
            startTime,
            endTime,
            PageRequest.of(0, 10)
        )

        // Then
        assertEquals(2, result.content.size)
        result.content.forEach { stock ->
            assertEquals("AAPL", stock.primaryKey.symbol)
            assertTrue(stock.primaryKey.timestamp >= startTime)
            assertTrue(stock.primaryKey.timestamp <= endTime)
        }
    }

    @Test
    fun `findAllDistinctSymbols should return unique symbols`() {
        // When
        val result = stockRepository.findAllDistinctSymbols()

        // Then
        assertTrue(result.contains("AAPL"))
        assertTrue(result.contains("GOOGL"))
        // Ensure uniqueness
        assertEquals(result.size, result.toSet().size)
    }

    @Test
    fun `findLatestBySymbol should return most recent stock data`() {
        // When
        val result = stockRepository.findLatestBySymbol("AAPL")

        // Then
        assertNotNull(result)
        assertEquals("AAPL", result.primaryKey.symbol)
        assertEquals(150.25, result.currentPrice) // Latest entry
    }

    @Test
    fun `findLatestBySymbol should return null for non-existent symbol`() {
        // When
        val result = stockRepository.findLatestBySymbol("INVALID")

        // Then
        assertNull(result)
    }

    @Test
    fun `should save and retrieve stock successfully`() {
        // Given
        val newStock = ProcessedStock(
            primaryKey = StockPrimaryKey("MSFT", Instant.now().toEpochMilli()),
            currentPrice = 300.0,
            volume = 700000L,
            volatility = 0.20,
            priceChange = 5.0,
            priceChangePercent = 1.69,
            volumeAverage = 650000.0,
            riskScore = 25.0,
            trend = "BULLISH",
            support = 295.0,
            resistance = 305.0
        )

        // When
        stockRepository.save(newStock)
        val retrieved = stockRepository.findLatestBySymbol("MSFT")

        // Then
        assertNotNull(retrieved)
        assertEquals("MSFT", retrieved.primaryKey.symbol)
        assertEquals(300.0, retrieved.currentPrice)
        assertEquals("BULLISH", retrieved.trend)
    }
}