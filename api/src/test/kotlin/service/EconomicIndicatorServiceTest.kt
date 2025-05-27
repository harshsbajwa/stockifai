package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.model.EconomicIndicatorEntity
import com.harshsbajwa.stockifai.api.model.IndicatorPrimaryKey
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorRepository
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
import kotlin.test.*

@ExtendWith(MockitoExtension::class)
class EconomicIndicatorServiceTest {

    @Mock
    private lateinit var indicatorRepository: EconomicIndicatorRepository

    @InjectMocks
    private lateinit var economicIndicatorService: EconomicIndicatorService

    private lateinit var sampleIndicator: EconomicIndicatorEntity

    @BeforeEach
    fun setUp() {
        sampleIndicator = EconomicIndicatorEntity(
            primaryKey = IndicatorPrimaryKey("VIXCLS", Instant.now().toEpochMilli()),
            value = 18.75,
            country = "US"
        )
    }

    @Test
    fun `getLatestIndicator should return indicator when found`() {
        // Given
        whenever(indicatorRepository.findLatestByIndicator("VIXCLS"))
            .thenReturn(sampleIndicator)

        // When
        val result = economicIndicatorService.getLatestIndicator("VIXCLS")

        // Then
        assertNotNull(result)
        assertEquals("VIXCLS", result.indicator)
        assertEquals(18.75, result.value)
        assertEquals("US", result.country)
        assertEquals("VIX Volatility Index", result.description)
        verify(indicatorRepository).findLatestByIndicator("VIXCLS")
    }

    @Test
    fun `getLatestIndicator should return null when not found`() {
        // Given
        whenever(indicatorRepository.findLatestByIndicator("INVALID"))
            .thenReturn(null)

        // When
        val result = economicIndicatorService.getLatestIndicator("INVALID")

        // Then
        assertNull(result)
        verify(indicatorRepository).findLatestByIndicator("INVALID")
    }

    @Test
    fun `getLatestIndicator should handle repository exception`() {
        // Given
        whenever(indicatorRepository.findLatestByIndicator("VIXCLS"))
            .thenThrow(RuntimeException("Database error"))

        // When
        val result = economicIndicatorService.getLatestIndicator("VIXCLS")

        // Then
        assertNull(result)
        verify(indicatorRepository).findLatestByIndicator("VIXCLS")
    }

    @Test
    fun `getAllLatestIndicators should return paginated results`() {
        // Given
        val indicators = listOf("VIXCLS", "FEDFUNDS", "DGS10")
        whenever(indicatorRepository.findAllDistinctIndicators()).thenReturn(indicators)
        
        // Only mock the indicators we actually query for
        whenever(indicatorRepository.findLatestByIndicator("VIXCLS"))
            .thenReturn(sampleIndicator.copy(
                primaryKey = IndicatorPrimaryKey("VIXCLS", Instant.now().toEpochMilli())
            ))
        whenever(indicatorRepository.findLatestByIndicator("FEDFUNDS"))
            .thenReturn(sampleIndicator.copy(
                primaryKey = IndicatorPrimaryKey("FEDFUNDS", Instant.now().toEpochMilli())
            ))

        // When
        val result = economicIndicatorService.getAllLatestIndicators(0, 2)

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
    fun `getIndicatorHistory should return paginated history`() {
        // Given
        val pageable = PageRequest.of(0, 100)
        val slice = SliceImpl(listOf(sampleIndicator), pageable, false)

        whenever(indicatorRepository.findByIndicatorOrderByTimestampDesc("VIXCLS", pageable))
            .thenReturn(slice)

        // When
        val result = economicIndicatorService.getIndicatorHistory("VIXCLS", 0, 100)

        // Then
        assertEquals(1, result.data.size)
        assertEquals(0, result.page)
        assertEquals(100, result.size)
        assertEquals(1L, result.totalElements)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
        
        val indicator = result.data[0]
        assertEquals("VIXCLS", indicator.indicator)
        assertEquals(18.75, indicator.value)
    }

    @Test
    fun `getIndicatorHistory should handle repository exception`() {
        // Given
        val pageable = PageRequest.of(0, 100)
        whenever(indicatorRepository.findByIndicatorOrderByTimestampDesc("VIXCLS", pageable))
            .thenThrow(RuntimeException("Database error"))

        // When
        val result = economicIndicatorService.getIndicatorHistory("VIXCLS", 0, 100)

        // Then
        assertTrue(result.data.isEmpty())
        assertEquals(0, result.totalElements)
    }
}