package com.harshsbajwa.stockifai.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.EconomicIndicatorService
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
import java.time.ZoneId

@WebMvcTest(EconomicController::class)
@ActiveProfiles("test")
class EconomicControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var economicIndicatorService: EconomicIndicatorService

    private lateinit var sampleIndicatorResponse: EconomicIndicatorResponse

    @BeforeEach
    fun setUp() {
        sampleIndicatorResponse = EconomicIndicatorResponse(
            series_id = "VIXCLS",
            observations = listOf(
                EconomicObservation(
                    date = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate(),
                    value = 18.75,
                    realTimeStart = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate(),
                    realTimeEnd = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
                )
            ),
            metadata = EconomicIndicatorMetadata(
                series_id = "VIXCLS",
                title = "VIX Volatility Index",
                frequency = "Daily",
                units = "Index",
                notes = null,
                source = "Test Source"
            )
        )
    }


    @Test
    fun `getIndicator should return indicator data when found`() {
        // Given
        whenever(economicIndicatorService.getLatestIndicator("VIXCLS"))
            .thenReturn(sampleIndicatorResponse)

        // When & Then
        mockMvc.perform(get("/api/v1/economic/indicators/VIXCLS"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.series_id").value("VIXCLS"))
            .andExpect(jsonPath("$.data.observations[0].value").value(18.75))
            .andExpect(jsonPath("$.data.metadata.title").value("VIX Volatility Index"))

        verify(economicIndicatorService).getLatestIndicator("VIXCLS")
    }

    @Test
    fun `getIndicator should return 404 when indicator not found`() {
        // Given
        whenever(economicIndicatorService.getLatestIndicator("INVALID"))
            .thenReturn(null)

        // When & Then
        mockMvc.perform(get("/api/v1/economic/indicators/INVALID"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Economic indicator not found: INVALID"))

        verify(economicIndicatorService).getLatestIndicator("INVALID")
    }

    @Test
    fun `getIndicatorHistory should return paginated history`() {
        // Given
        val paginatedResponse = PaginatedResponse(
            data = listOf(sampleIndicatorResponse),
            page = 0,
            size = 100,
            totalElements = 1,
            totalPages = 1,
            hasNext = false,
            hasPrevious = false
        )
        whenever(economicIndicatorService.getIndicatorHistory("VIXCLS", 0, 100))
            .thenReturn(paginatedResponse)

        // When & Then
        mockMvc.perform(get("/api/v1/economic/indicators/VIXCLS/history"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.data").isArray)
            .andExpect(jsonPath("$.data.data[0].series_id").value("VIXCLS"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.totalElements").value(1))

        verify(economicIndicatorService).getIndicatorHistory("VIXCLS", 0, 100)
    }

    @Test
    fun `getAllIndicators should return all latest indicators`() {
        // Given
        val paginatedResponse = PaginatedResponse(
            data = listOf(sampleIndicatorResponse),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1,
            hasNext = false,
            hasPrevious = false
        )
        whenever(economicIndicatorService.getAllLatestIndicators(0, 20))
            .thenReturn(paginatedResponse)

        // When & Then
        mockMvc.perform(get("/api/v1/economic/indicators"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.data").isArray)
            .andExpect(jsonPath("$.data.totalElements").value(1))

        verify(economicIndicatorService).getAllLatestIndicators(0, 20)
    }
}