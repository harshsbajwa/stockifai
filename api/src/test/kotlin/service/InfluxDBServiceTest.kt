package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.config.InfluxProperties
import com.influxdb.client.QueryApi
import com.influxdb.query.FluxRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import kotlin.test.*

@ExtendWith(MockitoExtension::class)
class InfluxDBServiceTest {

    @Mock
    private lateinit var queryApi: QueryApi

    @Mock
    private lateinit var influxProperties: InfluxProperties

    @InjectMocks
    private lateinit var influxDBService: InfluxDBService

    @Mock
    private lateinit var fluxRecord: FluxRecord

    @BeforeEach
    fun setUp() {
        whenever(influxProperties.bucket).thenReturn("testbucket")
        whenever(influxProperties.org).thenReturn("testorg")
    }

    @Test
    fun `getStockMetrics should return metrics when data exists`() {
        // Given
        val now = Instant.now()
        whenever(fluxRecord.time).thenReturn(now)
        whenever(fluxRecord.getValueByKey("price")).thenReturn(150.25)
        whenever(fluxRecord.getValueByKey("volume")).thenReturn(1000000.0)
        whenever(fluxRecord.getValueByKey("volatility")).thenReturn(0.25)
        whenever(fluxRecord.getValueByKey("risk_score")).thenReturn(35.5)

        val mockResults = listOf(fluxRecord)
        whenever(queryApi.query(any<String>(), eq("testorg"))).thenReturn(mockResults)

        // When
        val result = influxDBService.getStockMetrics("AAPL", 24, "5m")

        // Then
        assertNotNull(result)
        assertEquals("AAPL", result.symbol)
        assertEquals("5m", result.aggregation)
        assertEquals(1, result.metrics.size)
        
        val metric = result.metrics[0]
        assertEquals(150.25, metric.price)
        assertEquals(1000000L, metric.volume)
        assertEquals(0.25, metric.volatility)
        assertEquals(35.5, metric.riskScore)
        assertEquals(now, metric.timestamp)
        
        verify(queryApi).query(any<String>(), eq("testorg"))
    }

    @Test
    fun `getStockMetrics should return null when query fails`() {
        // Given
        whenever(queryApi.query(any<String>(), eq("testorg")))
            .thenThrow(RuntimeException("InfluxDB connection error"))

        // When
        val result = influxDBService.getStockMetrics("AAPL", 24, "5m")

        // Then
        assertNull(result)
        verify(queryApi).query(any<String>(), eq("testorg"))
    }

    @Test
    fun `getMarketVolatility should return volatility data`() {
        // Given
        val now = Instant.now()
        whenever(fluxRecord.time).thenReturn(now)
        whenever(fluxRecord.value).thenReturn(0.35)

        val mockResults = listOf(fluxRecord)
        whenever(queryApi.query(any<String>(), eq("testorg"))).thenReturn(mockResults)

        // When
        val result = influxDBService.getMarketVolatility(24)

        // Then
        assertEquals(1, result.size)
        assertEquals(now, result[0].timestamp)
        assertEquals(0.35, result[0].volatility)
        assertNull(result[0].price)
        assertNull(result[0].volume)
        assertNull(result[0].riskScore)
    }

    @Test
    fun `getTopPerformers should return top performing stocks`() {
        // Given
        whenever(fluxRecord.getValueByKey("symbol")).thenReturn("AAPL")
        whenever(fluxRecord.value).thenReturn(5.25)

        val mockRecord2 = mock<FluxRecord>()
        whenever(mockRecord2.getValueByKey("symbol")).thenReturn("GOOGL")
        whenever(mockRecord2.value).thenReturn(3.15)

        val mockResults = listOf(fluxRecord, mockRecord2)
        whenever(queryApi.query(any<String>(), eq("testorg"))).thenReturn(mockResults)

        // When
        val result = influxDBService.getTopPerformers(10)

        // Then
        assertEquals(2, result.size)
        assertEquals("AAPL" to 5.25, result[0])
        assertEquals("GOOGL" to 3.15, result[1])
    }

    @Test
    fun `getTopPerformers should handle null values gracefully`() {
        // Given
        whenever(fluxRecord.getValueByKey("symbol")).thenReturn(null)
        whenever(fluxRecord.value).thenReturn(5.25)

        val mockResults = listOf(fluxRecord)
        whenever(queryApi.query(any<String>(), eq("testorg"))).thenReturn(mockResults)

        // When
        val result = influxDBService.getTopPerformers(10)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTopPerformers should return empty list on exception`() {
        // Given
        whenever(queryApi.query(any<String>(), eq("testorg")))
            .thenThrow(RuntimeException("Query failed"))

        // When
        val result = influxDBService.getTopPerformers(10)

        // Then
        assertTrue(result.isEmpty())
    }
}