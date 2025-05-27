package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.config.InfluxProperties
import com.harshsbajwa.stockifai.api.dto.MetricPoint
import com.harshsbajwa.stockifai.api.dto.StockMetricsResponse
import com.harshsbajwa.stockifai.api.dto.TimeRange
import com.influxdb.client.QueryApi
import com.influxdb.query.FluxRecord
import com.influxdb.query.FluxTable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class InfluxDBService(
    private val queryApi: QueryApi,
    private val influxProperties: InfluxProperties
) {
    private val logger = LoggerFactory.getLogger(InfluxDBService::class.java)

    fun getStockMetrics(
        symbol: String,
        hours: Long = 24,
        aggregation: String = "5m"
    ): StockMetricsResponse? {
        return try {
            val endTime = Instant.now()
            val startTime = endTime.minus(hours, ChronoUnit.HOURS)
            
            val fluxQuery = buildMetricsQuery(symbol, startTime, endTime, aggregation)
            val result = queryApi.query(fluxQuery, influxProperties.org)
            
            val metrics = parseMetricsResults(result.flatMap { it.records })
            
            StockMetricsResponse(
                symbol = symbol,
                metrics = metrics,
                timeRange = TimeRange(startTime, endTime),
                aggregation = aggregation
            )
        } catch (e: Exception) {
            logger.error("Error fetching InfluxDB metrics for $symbol", e)
            null
        }
    }

    fun getMarketVolatility(hours: Long = 24): List<MetricPoint> {
        return try {
            val endTime = Instant.now()
            val startTime = endTime.minus(hours, ChronoUnit.HOURS)
            
            val fluxQuery = """
                from(bucket: "${influxProperties.bucket}")
                  |> range(start: ${startTime.epochSecond}s, stop: ${endTime.epochSecond}s)
                  |> filter(fn: (r) => r._measurement == "stock_metrics")
                  |> filter(fn: (r) => r._field == "volatility")
                  |> aggregateWindow(every: 15m, fn: mean, createEmpty: false)
                  |> group()
                  |> mean()
                  |> yield(name: "market_volatility")
            """.trimIndent()
            
            val result = queryApi.query(fluxQuery, influxProperties.org)
            parseVolatilityResults(result.flatMap { it.records })
        } catch (e: Exception) {
            logger.error("Error fetching market volatility from InfluxDB", e)
            emptyList()
        }
    }

    fun getTopPerformers(limit: Int = 10): List<Pair<String, Double>> {
        return try {
            val fluxQuery = """
                from(bucket: "${influxProperties.bucket}")
                  |> range(start: -24h)
                  |> filter(fn: (r) => r._measurement == "stock_metrics")
                  |> filter(fn: (r) => r._field == "price_change_percent")
                  |> last()
                  |> sort(columns: ["_value"], desc: true)
                  |> limit(n: $limit)
            """.trimIndent()
            
            val result = queryApi.query(fluxQuery, influxProperties.org)
            result.flatMap { it.records }.mapNotNull { record ->
                val symbol = record.values["symbol"] as? String
                val changePercent = record.value as? Double
                if (symbol != null && changePercent != null) {
                    symbol to changePercent
                } else null
            }
        } catch (e: Exception) {
            logger.error("Error fetching top performers from InfluxDB", e)
            emptyList()
        }
    }

    private fun buildMetricsQuery(
        symbol: String,
        startTime: Instant,
        endTime: Instant,
        aggregation: String
    ): String {
        return """
            from(bucket: "${influxProperties.bucket}")
              |> range(start: ${startTime.epochSecond}s, stop: ${endTime.epochSecond}s)
              |> filter(fn: (r) => r._measurement == "stock_metrics")
              |> filter(fn: (r) => r.symbol == "$symbol")
              |> filter(fn: (r) => r._field == "price" or r._field == "volume" or r._field == "volatility" or r._field == "risk_score")
              |> aggregateWindow(every: $aggregation, fn: mean, createEmpty: false)
              |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> yield(name: "stock_metrics")
        """.trimIndent()
    }

    private fun parseMetricsResults(results: List<FluxRecord>): List<MetricPoint> {
        return results.map { record ->
            MetricPoint(
                timestamp = record.time ?: Instant.now(),
                price = record.values["price"] as? Double,
                volume = (record.values["volume"] as? Double)?.toLong(),
                volatility = record.values["volatility"] as? Double,
                riskScore = record.values["risk_score"] as? Double
            )
        }.sortedBy { it.timestamp }
    }

    private fun parseVolatilityResults(results: List<FluxRecord>): List<MetricPoint> {
        return results.map { record ->
            MetricPoint(
                timestamp = record.time ?: Instant.now(),
                price = null,
                volume = null,
                volatility = record.value as? Double,
                riskScore = null
            )
        }.sortedBy { it.timestamp }
    }
}