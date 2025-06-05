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
        objectMapper =
            ObjectMapper().apply {
                registerModule(KotlinModule.Builder().build())
                findAndRegisterModules()
            }
    }

    @Test
    fun `FinnhubQuote serialization and deserialization works correctly`() {
        // Given
        val finnhubQuote =
            FinnhubQuote(
                currentPrice = 150.25,
                highPriceOfDay = 152.0,
                lowPriceOfDay = 148.0,
                openPriceOfDay = 149.0,
                previousClosePrice = 147.75,
                timestamp = 1640995200L,
                change = 2.5,
                percentChange = 1.69,
            )

        // When
        val json = objectMapper.writeValueAsString(finnhubQuote)
        val deserialized = objectMapper.readValue(json, FinnhubQuote::class.java)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("150.25"))
        assertTrue(json.contains("1.69"))

        assertEquals(finnhubQuote.currentPrice, deserialized.currentPrice)
        assertEquals(finnhubQuote.highPriceOfDay, deserialized.highPriceOfDay)
        assertEquals(finnhubQuote.percentChange, deserialized.percentChange)
        assertEquals(finnhubQuote.timestamp, deserialized.timestamp)
    }

    @Test
    fun `FredObservation serialization works correctly`() {
        // Given
        val observation =
            FredObservation(
                date = "2023-12-31",
                value = "18.75",
                realtimeStart = "2023-12-31",
                realtimeEnd = "2023-12-31",
            )

        // When
        val json = objectMapper.writeValueAsString(observation)
        val deserialized = objectMapper.readValue(json, FredObservation::class.java)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("2023-12-31"))
        assertTrue(json.contains("18.75"))

        assertEquals(observation.date, deserialized.date)
        assertEquals(observation.value, deserialized.value)
        assertEquals(observation.realtimeStart, deserialized.realtimeStart)
        assertEquals(observation.realtimeEnd, deserialized.realtimeEnd)
    }

    @Test
    fun `FinnhubQuote deserialization handles all fields correctly`() {
        // Given
        val json =
            """
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
        val json =
            """
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
    fun `FinnhubNews deserialization works correctly`() {
        // Given
        val json =
            """
            {
                "category": "general",
                "datetime": 1640995200000,
                "headline": "Test News Headline",
                "id": 12345,
                "image": "https://example.com/image.jpg",
                "related": "AAPL",
                "source": "Reuters",
                "summary": "Test news summary",
                "url": "https://example.com/news"
            }
            """.trimIndent()

        // When
        val news = objectMapper.readValue(json, FinnhubNews::class.java)

        // Then
        assertEquals("general", news.category)
        assertEquals(1640995200000L, news.datetime)
        assertEquals("Test News Headline", news.headline)
        assertEquals(12345L, news.id)
        assertEquals("AAPL", news.related)
        assertEquals("Reuters", news.source)
    }

    @Test
    fun `FredSeriesResponse deserialization works correctly`() {
        // Given
        val json =
            """
            {
                "observations": [
                    {
                        "date": "2023-12-31",
                        "value": "18.75",
                        "realtime_start": "2023-12-31",
                        "realtime_end": "2023-12-31"
                    },
                    {
                        "date": "2023-12-30",
                        "value": "19.25",
                        "realtime_start": "2023-12-30",
                        "realtime_end": "2023-12-30"
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
