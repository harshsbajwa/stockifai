package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.model.ProcessedStock
import com.harshsbajwa.stockifai.api.model.StockPrimaryKey
import com.harshsbajwa.stockifai.api.repository.StockRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

@ExtendWith(MockitoExtension::class)
class StockDataServiceTest {

    @Mock
    private lateinit var stockRepository: StockRepository

    @InjectMocks
    private lateinit var stockDataService: StockDataService

    private lateinit var sampleStock: ProcessedStock
    private lateinit var sampleStocks: List<ProcessedStock>

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        sampleStock = ProcessedStock(
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
        )

        sampleStocks = listOf(
            sampleStock,
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
            ),
            ProcessedStock(
                primaryKey = StockPrimaryKey("TSLA", now.toEpochMilli()),
                currentPrice = 800.0,
                volume = 2000000L,
                volatility = 0.45,
                priceChange = 25.0,
                priceChangePercent = 3.23,
                volumeAverage = 1800000.0,
                riskScore = 75.0,
                trend = "BULLISH",
                support = 780.0,
                resistance = 820.0
            )
        )
    }

    @Test
    fun `getLatestStockData should return stock data when found`() {
        // Given
        whenever(stockRepository.findLatestBySymbol("AAPL")).thenReturn(sampleStock)

        // When
        val result = stockDataService.getLatestStockData("AAPL")

        // Then
        assertNotNull(result)
        assertEquals("AAPL", result.symbol)
        assertEquals(150.25, result.currentPrice)
        assertEquals(1000000L, result.volume)
        assertEquals(0.25, result.volatility)
        assertEquals("BULLISH", result.trend)
        verify(stockRepository).findLatestBySymbol("AAPL")
    }

    @Test
    fun `getLatestStockData should return null when stock not found`() {
        // Given
        whenever(stockRepository.findLatestBySymbol("INVALID")).thenReturn(null)

        // When
        val result = stockDataService.getLatestStockData("INVALID")

        // Then
        assertNull(result)
        verify(stockRepository).findLatestBySymbol("INVALID")
    }

    @Test
    fun `getLatestStockData should handle repository exception`() {
        // Given
        whenever(stockRepository.findLatestBySymbol("AAPL"))
            .thenThrow(RuntimeException("Database error"))

        // When
        val result = stockDataService.getLatestStockData("AAPL")

        // Then
        assertNull(result)
        verify(stockRepository).findLatestBySymbol("AAPL")
    }

    @Test
    fun `getStockHistory should return history when data exists`() {
        // Given
        val now = Instant.now()
        val startTime = now.minus(24, ChronoUnit.HOURS)
        val pageable = PageRequest.of(0, 100)
        val slice = SliceImpl(listOf(sampleStock), pageable, false)

        whenever(stockRepository.findBySymbolAndTimestampBetween(
            eq("AAPL"), 
            eq(startTime.toEpochMilli()), 
            any(), 
            eq(pageable)
        )).thenReturn(slice)

        // When
        val result = stockDataService.getStockHistory("AAPL", 24, 0, 100)

        // Then
        assertNotNull(result)
        assertEquals("AAPL", result.symbol)
        assertEquals(1, result.data.size)
        assertEquals(1, result.count)
        assertEquals(150.25, result.data[0].price)
    }

    @Test
    fun `getAllActiveStocks should return paginated results`() {
        // Given
        val symbols = listOf("AAPL", "GOOGL", "TSLA")
        whenever(stockRepository.findAllDistinctSymbols()).thenReturn(symbols)
        
        symbols.forEachIndexed { index, symbol ->
            whenever(stockRepository.findLatestBySymbol(symbol))
                .thenReturn(sampleStocks[index])
        }

        // When
        val result = stockDataService.getAllActiveStocks(0, 2)

        // Then
        assertEquals(2, result.data.size)
        assertEquals(0, result.page)
        assertEquals(2, result.size)
        assertEquals(3L, result.totalElements)
        assertEquals(2, result.totalPages)
        assertTrue(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `getMarketOverview should calculate correct metrics`() {
        // Given
        val symbols = listOf("AAPL", "GOOGL", "TSLA")
        whenever(stockRepository.findAllDistinctSymbols()).thenReturn(symbols)
        
        symbols.forEachIndexed { index, symbol ->
            whenever(stockRepository.findLatestBySymbol(symbol))
                .thenReturn(sampleStocks[index])
        }

        // When
        val result = stockDataService.getMarketOverview()

        // Then
        assertEquals(3, result.totalStocks)
        assertEquals(3, result.activeStocks)
        assertEquals(51.83, result.averageRiskScore, 0.1) // (35.5 + 45.0 + 75.0) / 3
        assertEquals(1, result.highRiskStocks.size) // Only TSLA > 70
        assertEquals("TSLA", result.highRiskStocks[0])
        assertEquals("BULLISH", result.marketSentiment) // 2 bullish vs 1 bearish
        
        // Top movers
        assertEquals(2, result.topMovers.gainers.size)
        assertEquals(1, result.topMovers.losers.size)
        assertEquals(3, result.topMovers.mostVolatile.size)
    }

    @Test
    fun `getMarketOverview should handle empty data gracefully`() {
        // Given
        whenever(stockRepository.findAllDistinctSymbols()).thenReturn(emptyList())

        // When
        val result = stockDataService.getMarketOverview()

        // Then
        assertEquals(0, result.totalStocks)
        assertEquals(0, result.activeStocks)
        assertEquals(0.0, result.averageRiskScore)
        assertTrue(result.highRiskStocks.isEmpty())
        assertEquals("UNKNOWN", result.marketSentiment)
    }

    @Test
    fun `getMarketOverview should handle repository exception`() {
        // Given
        whenever(stockRepository.findAllDistinctSymbols())
            .thenThrow(RuntimeException("Database error"))

        // When
        val result = stockDataService.getMarketOverview()

        // Then
        assertEquals(0, result.totalStocks)
        assertEquals(0, result.activeStocks)
        assertEquals("UNKNOWN", result.marketSentiment)
    }
}