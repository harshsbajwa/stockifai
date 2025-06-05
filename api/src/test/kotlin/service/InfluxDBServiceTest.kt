package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.config.InfluxProperties
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.QueryKotlinApi
import com.influxdb.exceptions.InfluxException
import com.influxdb.query.FluxRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

@ExtendWith(MockitoExtension::class)
class InfluxDBServiceTest {
    @Mock
    private lateinit var mockInfluxDBClientKotlin: InfluxDBClientKotlin

    @Mock
    private lateinit var mockQueryKotlinApi: QueryKotlinApi

    @Mock
    private lateinit var influxProperties: InfluxProperties

    private lateinit var influxDBService: InfluxDBService

    private val testOrg = "test-org"
    private val stockBucket = "stock_ts_bucket_env"
    private val econBucket = "econ_ts_bucket_env"
    private val testToken = "test-token-env"
    private val testTokenChars = testToken.toCharArray()

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(influxProperties.url).thenReturn("http://mock-influx:8086")
        Mockito.lenient().`when`(influxProperties.org).thenReturn(testOrg)
        Mockito.lenient().`when`(influxProperties.bucket).thenReturn("default_bucket_props")

        System.setProperty("INFLUXDB_STOCK_TIMESERIES_BUCKET", stockBucket)
        System.setProperty("INFLUXDB_ECONOMIC_TIMESERIES_BUCKET", econBucket)
        System.setProperty("INFLUXDB_TOKEN", testToken)

        Mockito.lenient().`when`(mockInfluxDBClientKotlin.getQueryKotlinApi()).thenReturn(mockQueryKotlinApi)
        runBlocking {
            Mockito.lenient().`when`(mockInfluxDBClientKotlin.ping()).thenReturn(true)
        }

        influxDBService = InfluxDBService(influxProperties, mockInfluxDBClientKotlin)
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty("INFLUXDB_STOCK_TIMESERIES_BUCKET")
        System.clearProperty("INFLUXDB_ECONOMIC_TIMESERIES_BUCKET")
        System.clearProperty("INFLUXDB_TOKEN")
    }

    private fun createMockFluxRecord(
        time: Instant,
        values: Map<String, Any?>,
    ): FluxRecord {
        val record = mock<FluxRecord>()
        Mockito.lenient().`when`(record.time).thenReturn(time)
        Mockito.lenient().`when`(record.values).thenReturn(HashMap(values))
        values.forEach { (key, value) ->
            Mockito.lenient().`when`(record.getValueByKey(key)).thenReturn(value)
            if (key == "_value" && value != null) {
                Mockito.lenient().`when`(record.value).thenReturn(value)
            }
        }
        return record
    }

    private fun <T> createEmptyChannel(): Channel<T> = Channel<T>(0).also { it.close() }

    private fun <T> createSingletonChannel(item: T): Channel<T> =
        Channel<T>(1).apply {
            trySend(item)
            close()
        }

    private fun <T> createListChannel(items: List<T>): Channel<T> =
        Channel<T>(items.size).apply {
            items.forEach { trySend(it) }
            close()
        }

    @Test
    fun `getStockTimeSeries should return merged data when both ohlcv and metrics present`() =
        runBlocking {
            val symbol = "AAPL"
            val now = Instant.now()

            val ohlcvRecord =
                createMockFluxRecord(
                    now,
                    mapOf(
                        "open" to 150.0,
                        "high" to 152.0,
                        "low" to 149.0,
                        "close" to 151.0,
                        "volume" to 1_000_000.0,
                    ),
                )
            val metricsRecord = createMockFluxRecord(now, mapOf("volatility" to 0.015, "risk_score" to 30.0))

            val ohlcvChannel = createSingletonChannel(ohlcvRecord)
            val metricsChannel = createSingletonChannel(metricsRecord)

            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenReturn(ohlcvChannel)
                .thenReturn(metricsChannel)

            val result = influxDBService.getStockTimeSeries(symbol, 24, "5m")

            assertNotNull(result)
            assertEquals(symbol, result.symbol)
            assertEquals(1, result.metrics.size)
            val point = result.metrics[0]
            assertEquals(now, point.timestamp)
            assertEquals(151.0, point.price, "Price (close) mismatch")
            assertEquals(150.0, point.open, "Open mismatch")
            assertEquals(152.0, point.high, "High mismatch")
            assertEquals(149.0, point.low, "Low mismatch")
            assertEquals(1_000_000L, point.volume, "Volume mismatch")
            assertEquals(0.015, point.volatility, "Volatility mismatch")
            assertEquals(30.0, point.riskScore, "Risk score mismatch")

            verify(mockQueryKotlinApi, times(2)).query(any<String>(), eq(testOrg))
        }

    @Test
    fun `getStockTimeSeries should return only ohlcv when metrics absent`() =
        runBlocking {
            val symbol = "MSFT"
            val now = Instant.now()
            val ohlcvRecord = createMockFluxRecord(now, mapOf("close" to 200.0, "volume" to 500000.0))
            val ohlcvChannel = createSingletonChannel(ohlcvRecord)
            val emptyMetricsChannel = createEmptyChannel<FluxRecord>()

            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenReturn(ohlcvChannel)
                .thenReturn(emptyMetricsChannel)

            val result = influxDBService.getStockTimeSeries(symbol, 1)
            assertNotNull(result)
            assertEquals(1, result.metrics.size)
            val point = result.metrics[0]
            assertEquals(200.0, point.price)
            assertEquals(500000L, point.volume)
            assertNull(point.volatility)
            assertNull(point.riskScore)
        }

    @Test
    fun `getStockTimeSeries should return only metrics when ohlcv absent`() =
        runBlocking {
            val symbol = "GOOG"
            val now = Instant.now()
            val metricsRecord = createMockFluxRecord(now, mapOf("volatility" to 0.02, "risk_score" to 40.0))
            val emptyOhlcvChannel = createEmptyChannel<FluxRecord>()
            val metricsChannel = createSingletonChannel(metricsRecord)

            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenReturn(emptyOhlcvChannel)
                .thenReturn(metricsChannel)

            val result = influxDBService.getStockTimeSeries(symbol, 1)
            assertNotNull(result)
            assertEquals(1, result.metrics.size)
            val point = result.metrics[0]
            assertNull(point.price)
            assertNull(point.volume)
            assertEquals(0.02, point.volatility)
            assertEquals(40.0, point.riskScore)
        }

    @Test
    fun `getStockTimeSeries should handle empty results gracefully`() =
        runBlocking {
            val symbol = "EMPTY"
            val emptyChannel = createEmptyChannel<FluxRecord>()

            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg))).thenReturn(emptyChannel)

            val result = influxDBService.getStockTimeSeries(symbol, 1)
            assertNotNull(result)
            assertTrue(result.metrics.isEmpty(), "Metrics list should be empty for no data")
            assertEquals(symbol, result.symbol)
            assertTrue(result.timeRange.start < result.timeRange.end)
        }

    @Test
    fun `getStockTimeSeries should return null on InfluxException`() =
        runBlocking {
            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenThrow(InfluxException("Connection failed"))

            val result = influxDBService.getStockTimeSeries("FAIL", 1)
            assertNull(result)
        }

    @Test
    fun `getMarketVolatility should return aggregated volatility data`() =
        runBlocking {
            val now = Instant.now()
            val record1Time = now.minus(10, ChronoUnit.MINUTES)
            val record2Time = now
            val record1 = createMockFluxRecord(record1Time, mapOf("_value" to 0.025))
            val record2 = createMockFluxRecord(record2Time, mapOf("_value" to 0.030))

            val volatilityChannel = createListChannel(listOf(record1, record2))
            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg))).thenReturn(volatilityChannel)

            val result = influxDBService.getMarketVolatility(24)

            assertNotNull(result)
            assertEquals(2, result.size)
            assertEquals(0.025, result[0].volatility)
            assertEquals(record1Time, result[0].timestamp)
            assertEquals(0.030, result[1].volatility)
            assertEquals(record2Time, result[1].timestamp)
        }

    @Test
    fun `getMarketVolatility should return empty list on InfluxException`() =
        runBlocking {
            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenThrow(InfluxException("Connection failed"))
            val result = influxDBService.getMarketVolatility(1)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getTopPerformingSymbols should return sorted symbols`() =
        runBlocking {
            val now = Instant.now()
            val recordAAPL = createMockFluxRecord(now, mapOf("symbol" to "AAPL", "_value" to 5.5))
            val recordMSFT = createMockFluxRecord(now, mapOf("symbol" to "MSFT", "_value" to 3.2))
            val topPerformersChannel = createListChannel(listOf(recordAAPL, recordMSFT))

            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg))).thenReturn(topPerformersChannel)

            val result = influxDBService.getTopPerformingSymbols(2)

            assertNotNull(result)
            assertEquals(2, result.size)
            assertEquals("AAPL", result[0].first)
            assertEquals(5.5, result[0].second)
            assertEquals("MSFT", result[1].first)
            assertEquals(3.2, result[1].second)
        }

    @Test
    fun `getTopPerformingSymbols should return empty list on InfluxException`() =
        runBlocking {
            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenThrow(InfluxException("Connection failed"))
            val result = influxDBService.getTopPerformingSymbols(5)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getEconomicTimeSeries should return data points`() =
        runBlocking {
            val series_id = "DGS10"
            val now = Instant.now()
            val recordTime = now.minus(1, ChronoUnit.DAYS)
            val record = createMockFluxRecord(recordTime, mapOf("_value" to 1.5, "series_id" to series_id))
            val econChannel = createSingletonChannel(record)

            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg))).thenReturn(econChannel)

            val result = influxDBService.getEconomicTimeSeries(series_id, 30)

            assertNotNull(result)
            assertEquals(1, result.size)
            assertEquals(series_id, result[0].series_id)
            assertEquals(1.5, result[0].value)
            assertEquals(recordTime, result[0].timestamp)
        }

    @Test
    fun `getEconomicTimeSeries should return null on InfluxException`() =
        runBlocking {
            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg)))
                .thenThrow(InfluxException("Connection failed"))
            val result = influxDBService.getEconomicTimeSeries("FAIL", 7)
            assertNull(result)
        }

    @Test
    fun `getEconomicTimeSeries should return empty list if no data points found`() =
        runBlocking {
            val series_id = "NODATA"
            val emptyChannel = createEmptyChannel<FluxRecord>()
            whenever(mockQueryKotlinApi.query(any<String>(), eq(testOrg))).thenReturn(emptyChannel)

            val result = influxDBService.getEconomicTimeSeries(series_id, 30)
            assertNotNull(result)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `onShutdown should close client`() {
        influxDBService.onShutdown()
        verify(mockInfluxDBClientKotlin).close()
    }

    @Test
    fun `init should ping InfluxDB`() {
        runBlocking {
            verify(mockInfluxDBClientKotlin).ping()
        }
    }
}
