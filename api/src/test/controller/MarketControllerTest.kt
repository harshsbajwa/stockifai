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

@WebMvcTest(MarketController::class)
@ActiveProfiles("test")
class MarketControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var stockDataService: StockDataService

    @MockBean
    private lateinit var influxDBService: InfluxDBService

    private lateinit var sampleMarketOverview: MarketOverviewResponse

    @BeforeEach
    fun setUp() {
        sampleMarketOverview = MarketOverviewResponse(
            totalStocks = 10,
            activeStocks = 8,
            averageRiskScore = 45.5,
            highRiskStocks = listOf("TSLA", "GME"),
            topMovers = TopMovers(
                gainers = listOf(
                    StockMover("AAPL", 150.0, 5.0, 3.45, 1000000L)
                ),
                losers = listOf(
                    StockMover("META", 300.0, -10.0, -3.23, 800000L)
                ),
                mostVolatile = listOf(
                    StockMover("TSLA", 800.0, 25.0, 3.23, 2000000L)
                )
            ),
            marketSentiment = "BULLISH",
            lastUpdated = Instant.now()
        )
    }

    @Test
    fun `getMarketOverview should return market overview data`() {
        // Given
        whenever(stockDataService.getMarketOverview()).thenReturn(sampleMarketOverview)

        // When & Then
        mockMvc.perform(get("/api/v1/market/overview"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalStocks").value(10))
            .andExpect(jsonPath("$.data.activeStocks").value(8))
            .andExpect(jsonPath("$.data.averageRiskScore").value(45.5))
            .andExpect(jsonPath("$.data.marketSentiment").value("BULLISH"))
            .andExpect(jsonPath("$.data.highRiskStocks").isArray)
            .andExpect(jsonPath("$.data.highRiskStocks[0]").value("TSLA"))
            .andExpect(jsonPath("$.data.topMovers.gainers").isArray)
            .andExpect(jsonPath("$.data.topMovers.gainers[0].symbol").value("AAPL"))

        verify(stockDataService).getMarketOverview()
    }

    @Test
    fun `getMarketVolatility should return volatility data`() {
        // Given
        val volatilityData = listOf(
            MetricPoint(Instant.now(), null, null, 0.25, null),
            MetricPoint(Instant.now().plusSeconds(300), null, null, 0.28, null)
        )
        whenever(influxDBService.getMarketVolatility(24)).thenReturn(volatilityData)

        // When & Then
        mockMvc.perform(get("/api/v1/market/volatility"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[0].volatility").value(0.25))
            .andExpect(jsonPath("$.data[1].volatility").value(0.28))

        verify(influxDBService).getMarketVolatility(24)
    }

    @Test
    fun `getMarketVolatility should use custom hours parameter`() {
        // Given
        whenever(influxDBService.getMarketVolatility(48)).thenReturn(emptyList())

        // When & Then
        mockMvc.perform(
            get("/api/v1/market/volatility")
                .param("hours", "48")
        )
            .andExpect(status().isOk)

        verify(influxDBService).getMarketVolatility(48)
    }
}