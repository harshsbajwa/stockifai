package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.model.EconomicIndicatorSummary
import com.harshsbajwa.stockifai.api.model.EconomicIndicatorMetadata as EconomicIndicatorMetadataEntity
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorRepository
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorMetadataRepository
import com.harshsbajwa.stockifai.api.dto.EconomicDataPoint
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import kotlin.test.*


@ExtendWith(MockitoExtension::class)
class EconomicIndicatorServiceTest {

    @Mock
    private lateinit var indicatorSummaryRepository: EconomicIndicatorRepository

    @Mock
    private lateinit var metadataRepository: EconomicIndicatorMetadataRepository

    @Mock
    private lateinit var influxDBService: InfluxDBService

    @InjectMocks
    private lateinit var economicIndicatorService: EconomicIndicatorService

    private lateinit var sampleIndicatorSummary: EconomicIndicatorSummary
    private lateinit var sampleMetadata: EconomicIndicatorMetadataEntity

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        sampleIndicatorSummary = EconomicIndicatorSummary(
            indicator = "VIXCLS",
            lastTimestamp = now.toEpochMilli(),
            latestValue = 18.75,
            observationDate = LocalDate.now().toString()
        )
        sampleMetadata = EconomicIndicatorMetadataEntity(
            seriesId = "VIXCLS",
            title = "CBOE Volatility Index",
            frequency = "Daily",
            units = "Index",
            source = "FRED"
        )
    }

    @Test
    fun `getLatestIndicatorSummary should return indicator when found`() {
        // Given
        whenever(indicatorSummaryRepository.findById("VIXCLS")).thenReturn(Optional.of(sampleIndicatorSummary))
        whenever(metadataRepository.findById("VIXCLS")).thenReturn(Optional.of(sampleMetadata))

        // When
        val result = economicIndicatorService.getLatestIndicatorSummary("VIXCLS")

        // Then
        assertNotNull(result)
        assertEquals("VIXCLS", result.seriesId)
        assertEquals(1, result.observations.size)
        assertEquals(18.75, result.observations[0].value)
        assertEquals("CBOE Volatility Index", result.metadata?.title)
        verify(indicatorSummaryRepository).findById("VIXCLS")
        verify(metadataRepository).findById("VIXCLS")
    }

    @Test
    fun `getLatestIndicatorSummary should return null when not found`() {
        // Given
        whenever(indicatorSummaryRepository.findById("INVALID")).thenReturn(Optional.empty())
        whenever(metadataRepository.findById("INVALID")).thenReturn(Optional.empty())

        // When
        val result = economicIndicatorService.getLatestIndicatorSummary("INVALID")

        // Then
        assertNull(result)
        verify(indicatorSummaryRepository).findById("INVALID")
    }
    
    @Test
    fun `getEconomicIndicatorTimeSeries should call InfluxDBService`() {
        // Given
        val seriesId = "VIXCLS"
        val days = 30L
        val mockResponse = listOf(EconomicDataPoint(seriesId, 20.0, Instant.now()))
        whenever(influxDBService.getEconomicTimeSeries(seriesId, days)).thenReturn(mockResponse)

        // When
        val result = economicIndicatorService.getEconomicIndicatorTimeSeries(seriesId, days)

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(seriesId, result[0].seriesId)
        verify(influxDBService).getEconomicTimeSeries(seriesId, days)
    }


    @Test
    fun `getAllLatestIndicatorSummaries should return paginated results`() {
        // Given
        val indicatorIds = listOf("VIXCLS", "FEDFUNDS")
        whenever(indicatorSummaryRepository.findAllDistinctIndicators()).thenReturn(indicatorIds)
        
        val vixSummary = sampleIndicatorSummary.copy(indicator = "VIXCLS")
        val fedfundsSummary = sampleIndicatorSummary.copy(indicator = "FEDFUNDS", latestValue = 0.05)
        
        whenever(indicatorSummaryRepository.findById("VIXCLS")).thenReturn(Optional.of(vixSummary))
        whenever(metadataRepository.findById("VIXCLS")).thenReturn(Optional.of(sampleMetadata.copy(seriesId = "VIXCLS")))
        
        whenever(indicatorSummaryRepository.findById("FEDFUNDS")).thenReturn(Optional.of(fedfundsSummary))
        whenever(metadataRepository.findById("FEDFUNDS")).thenReturn(Optional.of(sampleMetadata.copy(seriesId = "FEDFUNDS", title = "Federal Funds Rate")))

        // When
        val result = economicIndicatorService.getAllLatestIndicatorSummaries(0, 2)

        // Then
        assertEquals(2, result.data.size)
        assertEquals("VIXCLS", result.data[0].seriesId)
        assertEquals("FEDFUNDS", result.data[1].seriesId)
        assertEquals(0, result.page)
        assertEquals(2, result.size)
        assertEquals(2L, result.totalElements)
        assertEquals(1, result.totalPages)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
    }
}