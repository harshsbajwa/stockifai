package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.config.InfluxProperties
import com.harshsbajwa.stockifai.api.dto.*
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.exceptions.InfluxException
import com.influxdb.query.FluxRecord
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class InfluxDBService(
    private val influxProperties: InfluxProperties,
    private val client: InfluxDBClientKotlin = createClient(influxProperties),
) {
    private val logger = LoggerFactory.getLogger(InfluxDBService::class.java)

    init {
        runBlocking {
            try {
                val ping = client.ping()
                logger.info("InfluxDB Kotlin Client Initialized. Ping: Success=$ping")
            } catch (e: InfluxException) {
                logger.error("Failed to connect to InfluxDB with Kotlin Client during init: ${e.message}", e)
            }
        }
    }

    companion object {
        fun createClient(influxProperties: InfluxProperties): InfluxDBClientKotlin {
            val logger = LoggerFactory.getLogger(InfluxDBService::class.java)
            val influxToken = System.getenv("INFLUXDB_TOKEN") ?: "INFLUXDB_CLOUD_TOKEN_FALLBACK"
            if (influxToken == "INFLUXDB_CLOUD_TOKEN_FALLBACK") {
                logger.warn("INFLUXDB_TOKEN is not set, using fallback.")
            }
            logger.info(
                "Initializing InfluxDB Kotlin Client for API Service: URL={}, Org={}, DefaultBucket={}",
                influxProperties.url,
                influxProperties.org,
                influxProperties.bucket,
            )
            return InfluxDBClientKotlinFactory.create(
                influxProperties.url,
                influxToken.toCharArray(),
                influxProperties.org,
            )
        }
    }

    @PreDestroy
    fun onShutdown() {
        try {
            client.close()
            logger.info("InfluxDB Kotlin Client closed successfully.")
        } catch (e: Exception) {
            logger.error("Error closing InfluxDB Kotlin Client: ${e.message}", e)
        }
    }

    private fun getStockTimeSeriesBucket(): String =
        System.getenv("INFLUXDB_STOCK_TIMESERIES_BUCKET") ?: influxProperties.bucket

    private fun getEconomicTimeSeriesBucket(): String =
        System.getenv("INFLUXDB_ECONOMIC_TIMESERIES_BUCKET") ?: influxProperties.bucket

    fun getStockTimeSeries(
        symbol: String,
        hours: Long = 24,
        aggregationWindow: String = "5m",
    ): StockMetricsResponse? =
        runBlocking {
            val endTime = Instant.now()
            val startTime = endTime.minus(hours, ChronoUnit.HOURS)
            val bucket = getStockTimeSeriesBucket()
            val org = influxProperties.org

            val ohlcvQuery =
                """
                from(bucket: "$bucket")
                  |> range(start: time(v: ${startTime.epochSecond}), stop: time(v: ${endTime.epochSecond}))
                  |> filter(fn: (r) => r._measurement == "ohlcv")
                  |> filter(fn: (r) => r.symbol == "$symbol")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.trimIndent()
            logger.debug("Executing InfluxDB Flux query for OHLCV [{}]: {}", symbol, ohlcvQuery)

            val calculatedMetricsQuery =
                """
                from(bucket: "$bucket")
                  |> range(start: time(v: ${startTime.epochSecond}), stop: time(v: ${endTime.epochSecond}))
                  |> filter(fn: (r) => r._measurement == "calculated_metrics")
                  |> filter(fn: (r) => r.symbol == "$symbol")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.trimIndent()
            logger.debug(
                "Executing InfluxDB Flux query for Calculated Metrics [{}]: {}",
                symbol,
                calculatedMetricsQuery,
            )

            try {
                val queryApi = client.getQueryKotlinApi()
                val ohlcvRecords = mutableListOf<FluxRecord>()
                val calculatedMetricsRecords = mutableListOf<FluxRecord>()
                try {
                    val ohlcvChannel = queryApi.query(ohlcvQuery, org)
                    for (record in ohlcvChannel) {
                        ohlcvRecords.add(record)
                    }
                } catch (e: InfluxException) {
                    logger.error("Failed to fetch OHLCV: ${e.message}", e)
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to fetch OHLCV: ${e.message}")
                }

                try {
                    val calculatedMetricsChannel = queryApi.query(calculatedMetricsQuery, org)
                    for (record in calculatedMetricsChannel) {
                        calculatedMetricsRecords.add(record)
                    }
                } catch (e: InfluxException) {
                    logger.error("Failed to fetch calculated metrics: ${e.message}", e)
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to fetch calculated metrics: ${e.message}")
                }

                val mergedMetrics = mutableMapOf<Instant, MutableMap<String, Any?>>()

                ohlcvRecords.forEach { record ->
                    record.time?.let { time ->
                        val pointMap = mergedMetrics.getOrPut(time) { mutableMapOf() }
                        pointMap["open"] = record.values["open"] as? Double
                        pointMap["high"] = record.values["high"] as? Double
                        pointMap["low"] = record.values["low"] as? Double
                        pointMap["close"] = record.values["close"] as? Double
                        pointMap["volume"] = (record.values["volume"] as? Number)?.toLong()
                    }
                }

                calculatedMetricsRecords.forEach { record ->
                    record.time?.let { time ->
                        val pointMap = mergedMetrics.getOrPut(time) { mutableMapOf() }
                        pointMap["volatility"] = record.values["volatility"] as? Double
                        pointMap["risk_score"] = record.values["risk_score"] as? Double
                        pointMap["price_change"] = record.values["price_change"] as? Double
                        pointMap["price_change_percent"] = record.values["price_change_percent"] as? Double
                        pointMap["trend_numeric"] = record.values["trend_numeric"] as? Double
                    }
                }

                val metricPoints =
                    mergedMetrics.entries
                        .map { entry ->
                            val time = entry.key
                            val values = entry.value
                            MetricPoint(
                                timestamp = time,
                                price = values["close"] as? Double,
                                open = values["open"] as? Double,
                                high = values["high"] as? Double,
                                low = values["low"] as? Double,
                                volume = values["volume"] as? Long,
                                volatility = values["volatility"] as? Double,
                                riskScore = values["risk_score"] as? Double,
                            )
                        }.sortedBy { it.timestamp }

                if (metricPoints.isEmpty()) {
                    logger.warn(
                        "No combined stock time-series data found for symbol {} in the last {} hours.",
                        symbol,
                        hours,
                    )
                    return@runBlocking StockMetricsResponse(
                        symbol,
                        emptyList(),
                        TimeRange(startTime, endTime),
                        aggregationWindow,
                    )
                }

                StockMetricsResponse(symbol, metricPoints, TimeRange(startTime, endTime), aggregationWindow)
            } catch (e: Exception) {
                logger.error("Error querying InfluxDB for $symbol time-series: ${e.message}", e)
                null
            }
        }

    fun getMarketVolatility(hours: Long = 24): List<MetricPoint> =
        runBlocking {
            val endTime = Instant.now()
            val startTime = endTime.minus(hours, ChronoUnit.HOURS)
            val bucket = getStockTimeSeriesBucket()
            val org = influxProperties.org

            val fluxQuery =
                """
                from(bucket: "$bucket")
                |> range(start: time(v: ${startTime.epochSecond}), stop: time(v: ${endTime.epochSecond}))
                |> filter(fn: (r) => r._measurement == "calculated_metrics")
                |> filter(fn: (r) => r._field == "volatility")
                |> aggregateWindow(every: 15m, fn: mean, createEmpty: false)
                |> group()
                |> mean(column: "_value")
                |> yield(name: "avg_market_volatility_per_window")
                """.trimIndent()

            logger.debug("Executing InfluxDB Flux query for getMarketVolatility: {}", fluxQuery)

            try {
                val queryApi = client.getQueryKotlinApi()
                val records = mutableListOf<FluxRecord>()
                val channel = queryApi.query(fluxQuery, org)
                for (record in channel) {
                    records.add(record)
                }

                val volatilityPoints =
                    records.mapNotNull { record ->
                        MetricPoint(
                            timestamp = record.time ?: Instant.EPOCH,
                            price = null,
                            open = null,
                            high = null,
                            low = null,
                            volume = null,
                            volatility = record.value as? Double,
                            riskScore = null,
                        )
                    }

                if (volatilityPoints.isEmpty()) {
                    logger.warn("No market volatility data found in the last {} hours.", hours)
                }

                volatilityPoints.sortedBy { it.timestamp }
            } catch (e: Exception) {
                logger.error("Error querying InfluxDB for market volatility: ${e.message}", e)
                emptyList()
            }
        }

    fun getTopPerformingSymbols(limit: Int = 10): List<Pair<String, Double>> =
        runBlocking {
            val endTime = Instant.now()
            val startTime = endTime.minus(1, ChronoUnit.HOURS)
            val bucket = getStockTimeSeriesBucket()
            val org = influxProperties.org

            val fluxQuery =
                """
                from(bucket: "$bucket")
                |> range(start: time(v: ${startTime.epochSecond}), stop: time(v: ${endTime.epochSecond}))
                |> filter(fn: (r) => r._measurement == "calculated_metrics")
                |> filter(fn: (r) => r._field == "price_change_percent")
                |> group(columns: ["symbol"])
                |> last()
                |> group()
                |> filter(fn: (r) => r._value != nil and not isNaN(v: r._value) and not isInf(v: r._value))
                |> sort(columns: ["_value"], desc: true)
                |> limit(n: $limit)
                |> yield(name: "top_performers_by_change_percent")
                """.trimIndent()

            logger.debug("Executing InfluxDB Flux query for getTopPerformingSymbols: {}", fluxQuery)

            try {
                val queryApi = client.getQueryKotlinApi()
                val records = mutableListOf<FluxRecord>()
                val channel = queryApi.query(fluxQuery, org)
                for (record in channel) {
                    records.add(record)
                }

                val performers =
                    records.mapNotNull { record ->
                        val symbol = record.values["symbol"] as? String
                        val changePercent = record.value as? Double
                        if (symbol != null && changePercent != null) Pair(symbol, changePercent) else null
                    }

                if (performers.isEmpty()) {
                    logger.warn("No top performers data found based on price change percentage in the last hour.")
                }

                performers
            } catch (e: Exception) {
                logger.error("Error querying InfluxDB for top performers: ${e.message}", e)
                emptyList()
            }
        }

    fun getEconomicTimeSeries(
        series_id: String,
        days: Long = 30,
    ): List<EconomicDataPoint>? =
        runBlocking {
            val endTime = Instant.now()
            val startTime = endTime.minus(days, ChronoUnit.DAYS)
            val bucket = getEconomicTimeSeriesBucket()
            val org = influxProperties.org

            val fluxQuery =
                """
                from(bucket: "$bucket")
                |> range(start: time(v: ${startTime.epochSecond}), stop: time(v: ${endTime.epochSecond}))
                |> filter(fn: (r) => r._measurement == "fred_observations")
                |> filter(fn: (r) => r.series_id == "$series_id")
                |> filter(fn: (r) => r._field == "value")
                |> sort(columns: ["_time"])
                |> yield(name: "economic_data")
                """.trimIndent()

            logger.debug("Executing InfluxDB Flux for economic series [{}]: {}", series_id, fluxQuery)

            try {
                val queryApi = client.getQueryKotlinApi()
                val records = mutableListOf<FluxRecord>()
                val channel = queryApi.query(fluxQuery, org)
                for (record in channel) {
                    records.add(record)
                }

                val dataPoints =
                    records.mapNotNull { record ->
                        record.time?.let { time ->
                            (record.value as? Double)?.let { value ->
                                EconomicDataPoint(series_id, value, time)
                            }
                        }
                    }

                if (dataPoints.isEmpty()) {
                    logger.warn(
                        "No time-series data found for economic series_id {} in the last {} days.",
                        series_id,
                        days,
                    )
                }

                dataPoints
            } catch (e: Exception) {
                logger.error("Error querying InfluxDB for economic series $series_id: ${e.message}", e)
                null
            }
        }
}
