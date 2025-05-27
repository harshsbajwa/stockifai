package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.config.InfluxProperties
import com.influxdb.client.QueryApi
import com.influxdb.query.FluxRecord
import com.influxdb.query.FluxTable
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

    @BeforeEach
    fun setUp() {
        whenever(influxProperties.bucket).thenReturn("testbucket")
        whenever(influxProperties.org).thenReturn("testorg")
    }

    @Test
    fun `getStockMetrics should return metrics when data exists`() {
        // Given
        val now = Instant.now()
        val mockRecord = mock<FluxRecord>()
        whenever(mockRecord.time).thenReturn(now)
        
        // Mock the values map that FluxRecord uses
        val valuesMap = mapOf(
            "price" to 150.25,
            "volume" to 1000000.0,
            "volatility" to 0.25,
            "risk_score" to 35.5
        )
        whenever(mockRecord.values).thenReturn(valuesMap)

        // Mock a FluxTable and set its records
        val mockTable = mock<FluxTable>()
        whenever(mockTable.records).thenReturn(listOf(mockRecord))

        val mockResults = listOf(mockTable)
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
        val mockRecord = mock<FluxRecord>()
        whenever(mockRecord.time).thenReturn(now)
        whenever(mockRecord.value).thenReturn(0.35)

        // Mock a FluxTable and set its records
        val mockTable = mock<FluxTable>()
        whenever(mockTable.records).thenReturn(listOf(mockRecord))

        val mockResults = listOf(mockTable)
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
        val mockRecord1 = mock<FluxRecord>()
        val valuesMap1 = mapOf("symbol" to "AAPL")
        whenever(mockRecord1.values).thenReturn(valuesMap1)
        whenever(mockRecord1.value).thenReturn(5.25)

        val mockRecord2 = mock<FluxRecord>()
        val valuesMap2 = mapOf("symbol" to "GOOGL")
        whenever(mockRecord2.values).thenReturn(valuesMap2)
        whenever(mockRecord2.value).thenReturn(3.15)

        // Mock a FluxTable and set its records
        val mockTable = mock<FluxTable>()
        whenever(mockTable.records).thenReturn(listOf(mockRecord1, mockRecord2))

        val mockResults = listOf(mockTable)
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
        val mockRecord = mock<FluxRecord>()
        val valuesMap = mapOf("symbol" to null) // Null symbol
        whenever(mockRecord.values).thenReturn(valuesMap)
        whenever(mockRecord.value).thenReturn(5.25)

        // Mock a FluxTable and set its records
        val mockTable = mock<FluxTable>()
        whenever(mockTable.records).thenReturn(listOf(mockRecord))

        val mockResults = listOf(mockTable)
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