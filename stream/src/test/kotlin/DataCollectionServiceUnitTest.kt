package com.harshsbajwa.stockifai.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataCollectionServiceUnitTest {

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        findAndRegisterModules()
    }

    @Test
    fun `data models should serialize correctly`() {
        val stockQuote = StockQuoteData(
            symbol = "AAPL",
            regularMarketPrice = 150.25,
            regularMarketVolume = 1000000L,
            regularMarketChangePercent = 1.69,
            regularMarketDayHigh = 152.0,
            regularMarketDayLow = 148.0,
            regularMarketOpen = 149.0,
            previousClosePrice = 147.75,
            timestamp = System.currentTimeMillis() / 1000
        )

        val json = objectMapper.writeValueAsString(stockQuote)
        assertNotNull(json)
        assertTrue(json.contains("AAPL"))
        assertTrue(json.contains("150.25"))

        val deserialized = objectMapper.readValue(json, StockQuoteData::class.java)
        assertNotNull(deserialized)
        assertTrue(deserialized.symbol == "AAPL")
        assertTrue(deserialized.regularMarketPrice == 150.25)
    }

    @Test
    fun `economic indicator should serialize correctly`() {
        val indicator = EconomicIndicator(
            indicator = "VIXCLS",
            value = 18.75,
            date = "2023-12-31"
        )

        val json = objectMapper.writeValueAsString(indicator)
        assertNotNull(json)
        assertTrue(json.contains("VIXCLS"))
        assertTrue(json.contains("18.75"))

        val deserialized = objectMapper.readValue(json, EconomicIndicator::class.java)
        assertNotNull(deserialized)
        assertTrue(deserialized.indicator == "VIXCLS")
        assertTrue(deserialized.value == 18.75)
    }

    @Test
    fun `stock message wrapper should serialize correctly`() {
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

        val json = objectMapper.writeValueAsString(message)
        assertNotNull(json)
        assertTrue(json.contains("stock_quote"))
        assertTrue(json.contains("TSLA"))

        val deserialized = objectMapper.readValue(json, StockMessage::class.java)
        assertNotNull(deserialized)
        assertTrue(deserialized.messageType == "stock_quote")
    }

    @Test
    fun `finnhub quote should deserialize correctly`() {
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

        val quote = objectMapper.readValue(json, FinnhubQuote::class.java)
        assertNotNull(quote)
        assertTrue(quote.currentPrice == 150.25)
        assertTrue(quote.highPriceOfDay == 152.0)
        assertTrue(quote.percentChange == 1.69)
    }

    @Test
    fun `fred response should deserialize correctly`() {
        val json = """
            {
                "observations": [
                    {
                        "date": "2023-12-31",
                        "value": "18.75"
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