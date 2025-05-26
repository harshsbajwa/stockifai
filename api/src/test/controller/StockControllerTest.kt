package com.harshsbajwa.stockifai.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.InfluxDBService
import com.harshsbajwa.stockifai.api.service.StockDataService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest(StockController::class)
@ActiveProfiles("test")
class StockControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var stockDataService: StockDataService

    @MockBean
    private lateinit var influxDBService: InfluxDBService

    private lateinit var sampleStockResponse: StockDataResponse
    private lateinit var sampleHistoryResponse: StockHistoryResponse
    private lateinit var sampleMetricsResponse: StockMetricsResponse

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        
        sampleStockResponse = StockDataResponse(
            symbol = "AAPL",
            currentPrice = 150.25,
            volume = 1000000L,
            volatility = 0.25,
            priceChange = 2.50,
            priceChangePercent = 1.69,
            volumeAverage = 950000.0,
            riskScore = 35.5,
            trend = "BULLISH",
            support = 148.0,
            resistance = 152.0,
            timestamp = now
        )

        sampleHistoryResponse = StockHistoryResponse(
            symbol = "AAPL",
            data = listOf(
                StockDataPoint(now, 150.25, 1000000L, 0.25, 35.5, "BULLISH")
            ),
            count = 1,
            timeRange = TimeRange(now.minusSeconds(3600), now)
        )

        sampleMetricsResponse = StockMetricsResponse(
            symbol = "AAPL",
            metrics = listOf(
                MetricPoint(now, 150.25, 1000000L, 0.25, 35.5)
            ),
            timeRange = TimeRange(now.minusSeconds(3600), now),
            aggregation = "5m"
        )
    }

    @Test
    fun `getStockData should return stock data when found`() {
        // Given
        whenever(stockDataService.getLatestStockData("AAPL")).thenReturn(sampleStockResponse)

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/AAPL"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.currentPrice").value(150.25))
            .andExpect(jsonPath("$.data.volume").value(1000000))
            .andExpect(jsonPath("$.data.trend").value("BULLISH"))
            .andExpect(jsonPath("$.message").value("Stock data retrieved successfully"))

        verify(stockDataService).getLatestStockData("AAPL")
    }

    @Test
    fun `getStockData should return 404 when stock not found`() {
        // Given
        whenever(stockDataService.getLatestStockData("INVALID")).thenReturn(null)

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/INVALID"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.message").value("Stock data not found for symbol: INVALID"))

        verify(stockDataService).getLatestStockData("INVALID")
    }

    @Test
    fun `getStockHistory should return history data`() {
        // Given
        whenever(stockDataService.getStockHistory("AAPL", 24, 0, 100))
            .thenReturn(sampleHistoryResponse)

        // When & Then
        mockMvc.perform(
            get("/api/v1/stocks/AAPL/history")
                .param("hours", "24")
                .param("page", "0")
                .param("size", "100")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.count").value(1))
            .andExpect(jsonPath("$.data.data").isArray)
            .andExpect(jsonPath("$.data.data[0].price").value(150.25))

        verify(stockDataService).getStockHistory("AAPL", 24, 0, 100)
    }

    @Test
    fun `getStockHistory should use default parameters`() {
        // Given
        whenever(stockDataService.getStockHistory("AAPL", 24, 0, 100))
            .thenReturn(sampleHistoryResponse)

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/AAPL/history"))
            .andExpect(status().isOk)

        verify(stockDataService).getStockHistory("AAPL", 24, 0, 100)
    }

    @Test
    fun `getStockMetrics should return metrics data`() {
        // Given
        whenever(influxDBService.getStockMetrics("AAPL", 24, "5m"))
            .thenReturn(sampleMetricsResponse)

        // When & Then
        mockMvc.perform(
            get("/api/v1/stocks/AAPL/metrics")
                .param("hours", "24")
                .param("aggregation", "5m")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.aggregation").value("5m"))
            .andExpect(jsonPath("$.data.metrics").isArray)

        verify(influxDBService).getStockMetrics("AAPL", 24, "5m")
    }

    @Test
    fun `getAllStocks should return paginated stock list`() {
        // Given
        val paginatedResponse = PaginatedResponse(
            data = listOf(sampleStockResponse),
            page = 0,
            size = 50,
            totalElements = 1,
            totalPages = 1,
            hasNext = false,
            hasPrevious = false
        )
        whenever(stockDataService.getAllActiveStocks(0, 50)).thenReturn(paginatedResponse)

        // When & Then
        mockMvc.perform(get("/api/v1/stocks"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.data").isArray)
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(50))
            .andExpect(jsonPath("$.data.totalElements").value(1))

        verify(stockDataService).getAllActiveStocks(0, 50)
    }

    @Test
    fun `getTopPerformers should return top performing stocks`() {
        // Given
        val topPerformers = listOf(
            "AAPL" to 5.25,
            "GOOGL" to 3.15
        )
        whenever(influxDBService.getTopPerformers(10)).thenReturn(topPerformers)

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/top-performers"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[0].first").value("AAPL"))
            .andExpect(jsonPath("$.data[0].second").value(5.25))

        verify(influxDBService).getTopPerformers(10)
    }
}