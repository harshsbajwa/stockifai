package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataCollectionServiceUnitTest {
    private val objectMapper =
        ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            findAndRegisterModules()
        }

    @Test
    fun `finnhub quote should serialize correctly`() {
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

        val json = objectMapper.writeValueAsString(finnhubQuote)
        assertNotNull(json)
        assertTrue(json.contains("150.25"))
        assertTrue(json.contains("1.69"))

        val deserialized = objectMapper.readValue(json, FinnhubQuote::class.java)
        assertNotNull(deserialized)
        assertTrue(deserialized.currentPrice == 150.25)
        assertTrue(deserialized.percentChange == 1.69)
    }

    @Test
    fun `fred observation should serialize correctly`() {
        val fredObservation =
            FredObservation(
                date = "2023-12-31",
                value = "18.75",
                realtimeStart = "2023-12-31",
                realtimeEnd = "2023-12-31",
            )

        val json = objectMapper.writeValueAsString(fredObservation)
        assertNotNull(json)
        assertTrue(json.contains("2023-12-31"))
        assertTrue(json.contains("18.75"))

        val deserialized = objectMapper.readValue(json, FredObservation::class.java)
        assertNotNull(deserialized)
        assertTrue(deserialized.date == "2023-12-31")
        assertTrue(deserialized.value == "18.75")
    }

    @Test
    fun `finnhub quote should deserialize correctly from json`() {
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

        val quote = objectMapper.readValue(json, FinnhubQuote::class.java)
        assertNotNull(quote)
        assertTrue(quote.currentPrice == 150.25)
        assertTrue(quote.highPriceOfDay == 152.0)
        assertTrue(quote.percentChange == 1.69)
    }

    @Test
    fun `fred response should deserialize correctly`() {
        val json =
            """
            {
                "observations": [
                    {
                        "date": "2023-12-31",
                        "value": "18.75",
                        "realtime_start": "2023-12-31",
                        "realtime_end": "2023-12-31"
                    }
                ]
            }
            """.trimIndent()

        val response = objectMapper.readValue(json, FredSeriesResponse::class.java)
        assertNotNull(response)
        assertTrue(response.observations?.isNotEmpty() == true)
        assertTrue(response.observations?.first()?.value == "18.75")
    }
}
