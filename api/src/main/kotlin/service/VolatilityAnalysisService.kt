package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.repository.InstrumentMetadataRepository
import com.influxdb.v3.client.InfluxDBClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class VolatilityAnalysisService(
    private val influxDBClient: InfluxDBClient,
    private val instrumentRepository: InstrumentMetadataRepository
) {
    private val logger = LoggerFactory.getLogger(VolatilityAnalysisService::class.java)

    fun getVolatilityMetrics(symbol: String, queryParams: VolatilityQueryParams): VolatilityResponse? {
        return try {
            val endDate = queryParams.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val periods = queryParams.periods.split(",").map { it.trim() }
            
            val volatilityData = mutableMapOf<String, Double>()
            
            periods.forEach { period ->
                val volatility = fetchVolatilityForPeriod(symbol, period, endDate)
                volatility?.let { volatilityData[period] = it }
            }
            
            // Fetch metadata from Cassandra
            val metadata = instrumentRepository.findById(symbol)?.let {
                InstrumentMetadata(
                    symbol = it.symbol,
                    name = it.name,
                    exchange = it.exchange,
                    currency = it.currency,
                    sector = it.sector,
                    industry = it.industry,
                    description = it.description
                )
            }
            
            VolatilityResponse(
                symbol = symbol,
                periods = volatilityData,
                endDate = endDate,
                metadata = metadata
            )
        } catch (e: Exception) {
            logger.error("Error fetching volatility metrics for $symbol", e)
            null
        }
    }

    private fun fetchVolatilityForPeriod(symbol: String, period: String, endDate: LocalDate): Double? {
        val metricName = "HV_${period.uppercase()}"
        val query = """
            SELECT time, value 
            FROM calculated_risk_metrics 
            WHERE symbol = '$symbol' 
              AND metric_name = '$metricName' 
              AND time <= '$endDate' 
            ORDER BY time DESC 
            LIMIT 1
        """.trimIndent()

        return try {
            val result = influxDBClient.query(query)
            // Process result and extract volatility value
            // This is a simplified implementation
            0.25 // Extract actual value from query result
        } catch (e: Exception) {
            logger.error("Error fetching volatility for $symbol, period $period", e)
            null
        }
    }
}