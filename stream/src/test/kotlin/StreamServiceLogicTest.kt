package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamServiceLogicTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            findAndRegisterModules()
        }
    }

    @Test
    fun `StockQuoteData serialization and deserialization works correctly`() {
        // Given
        val stockQuote = StockQuoteData(
            symbol = "AAPL",
            regularMarketPrice = 150.25,
            regularMarketVolume = 1000000L,
            regularMarketChangePercent = 1.69,
            regularMarketDayHigh = 152.0,
            regularMarketDayLow = 148.0,
            regularMarketOpen = 149.0,
            previousClosePrice = 147.75,
            timestamp = 1640995200L,
            marketState = "REGULAR"
        )

        // When
        val json = objectMapper.writeValueAsString(stockQuote)
        val deserialized = objectMapper.readValue(json, StockQuoteData::class.java)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("AAPL"))
        assertTrue(json.contains("150.25"))
        
        assertEquals(stockQuote.symbol, deserialized.symbol)
        assertEquals(stockQuote.regularMarketPrice, deserialized.regularMarketPrice)
        assertEquals(stockQuote.regularMarketVolume, deserialized.regularMarketVolume)
        assertEquals(stockQuote.timestamp, deserialized.timestamp)
    }

    @Test
    fun `EconomicIndicator serialization works correctly`() {
        // Given
        val indicator = EconomicIndicator(
            indicator = "VIXCLS",
            value = 18.75,
            date = "2023-12-31",
            source = "FRED"
        )

        // When
        val json = objectMapper.writeValueAsString(indicator)
        val deserialized = objectMapper.readValue(json, EconomicIndicator::class.java)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("VIXCLS"))
        assertTrue(json.contains("18.75"))
        assertTrue(json.contains("FRED"))
        
        assertEquals(indicator.indicator, deserialized.indicator)
        assertEquals(indicator.value, deserialized.value)
        assertEquals(indicator.date, deserialized.date)
        assertEquals(indicator.source, deserialized.source)
    }

    @Test
    fun `FinnhubQuote deserialization handles all fields correctly`() {
        // Given
        val json = """
            {
                "c": 150.25,
                "h": 152.0,
                "l": 148.0,
                "o": 149.0,
                "pc": 147.75,
                "t": 1640995200,
                "d": 2.5,
                "dp": 1.69
            }
        """.trimIndent()

        // When
        val quote = objectMapper.readValue(json, FinnhubQuote::class.java)

        // Then
        assertEquals(150.25, quote.currentPrice)
        assertEquals(152.0, quote.highPriceOfDay)
        assertEquals(148.0, quote.lowPriceOfDay)
        assertEquals(149.0, quote.openPriceOfDay)
        assertEquals(147.75, quote.previousClosePrice)
        assertEquals(1640995200L, quote.timestamp)
        assertEquals(2.5, quote.change)
        assertEquals(1.69, quote.percentChange)
    }

    @Test
    fun `FinnhubQuote handles null values gracefully`() {
        // Given
        val json = """
            {
                "c": null,
                "h": null,
                "l": null,
                "o": null,
                "pc": null,
                "t": null,
                "d": null,
                "dp": null
            }
        """.trimIndent()

        // When
        val quote = objectMapper.readValue(json, FinnhubQuote::class.java)

        // Then
        assertEquals(null, quote.currentPrice)
        assertEquals(null, quote.highPriceOfDay)
        assertEquals(null, quote.lowPriceOfDay)
        assertEquals(null, quote.openPriceOfDay)
        assertEquals(null, quote.previousClosePrice)
        assertEquals(null, quote.timestamp)
        assertEquals(null, quote.change)
        assertEquals(null, quote.percentChange)
    }

    @Test
    fun `StockMessage wrapper serialization works correctly`() {
        // Given
        val stockQuote = StockQuoteData(
            symbol = "TSLA",
            regularMarketPrice = 250.50,
            regularMarketVolume = 2000000L,
            regularMarketChangePercent = 2.5,
            regularMarketDayHigh = 255.0,
            regularMarketDayLow = 245.0,
            regularMarketOpen = 248.0,
            previousClosePrice = 244.5,
            timestamp = System.currentTimeMillis() / 1000
        )

        val message = StockMessage("stock_quote", stockQuote)

        // When
        val json = objectMapper.writeValueAsString(message)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("stock_quote"))
        assertTrue(json.contains("TSLA"))
        assertTrue(json.contains("stockifai-stream"))
        assertTrue(json.contains("1.0")) // version
    }

    @Test
    fun `FredSeriesResponse deserialization works correctly`() {
        // Given
        val json = """
            {
                "observations": [
                    {
                        "date": "2023-12-31",
                        "value": "18.75"
                    },
                    {
                        "date": "2023-12-30",
                        "value": "19.25"
                    }
                ]
            }
        """.trimIndent()

        // When
        val response = objectMapper.readValue(json, FredSeriesResponse::class.java)

        // Then
        assertNotNull(response.observations)
        assertEquals(2, response.observations.size)
        assertEquals("2023-12-31", response.observations.first().date)
        assertEquals("18.75", response.observations.first().value)
    }

    @Test
    fun `ServiceMetrics tracking works correctly`() {
        // Given
        val metrics = ServiceMetrics()

        // When
        metrics.successfulRequests.incrementAndGet()
        metrics.successfulRequests.incrementAndGet()
        metrics.failedRequests.incrementAndGet()

        // Then
        assertEquals(2L, metrics.successfulRequests.get())
        assertEquals(1L, metrics.failedRequests.get())
        
        val total = metrics.successfulRequests.get() + metrics.failedRequests.get()
        val successRate = (metrics.successfulRequests.get() * 100.0) / total
        assertEquals(66.67, successRate, 0.01)
    }
}