package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.repository.InstrumentMetadataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import com.harshsbajwa.stockifai.api.model.InstrumentMetadata as InstrumentMetadataEntity

@Service
class VolatilityAnalysisService(
    private val instrumentRepository: InstrumentMetadataRepository,
    private val influxDBService: InfluxDBService,
) {
    private val logger = LoggerFactory.getLogger(VolatilityAnalysisService::class.java)

    fun getVolatilityMetrics(
        symbol: String,
        queryParams: VolatilityQueryParams,
    ): VolatilityResponse? =
        try {
            val endDate = queryParams.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val requestedPeriods = queryParams.periods.split(",").map { it.trim().lowercase() }

            val volatilityData = mutableMapOf<String, Double?>()

            val maxLookbackDays =
                requestedPeriods
                    .mapNotNull { periodStr ->
                        when {
                            periodStr.endsWith("d") -> periodStr.removeSuffix("d").toLongOrNull()
                            periodStr.endsWith("m") -> (periodStr.removeSuffix("m").toLongOrNull() ?: 1) * 30
                            periodStr.endsWith("y") -> (periodStr.removeSuffix("y").toLongOrNull() ?: 1) * 365
                            else -> null
                        }
                    }.maxOrNull() ?: 30

            val volatilityTimeSeries = influxDBService.getStockTimeSeries(symbol, maxLookbackDays * 24, "1h")

            if (volatilityTimeSeries?.metrics?.isNotEmpty() == true) {
                requestedPeriods.forEach { periodKey ->
                    volatilityData[periodKey] = volatilityTimeSeries.metrics.lastOrNull()?.volatility
                }
            } else {
                logger.warn("No volatility time-series data found for {} to fulfill period requests.", symbol)
                requestedPeriods.forEach { periodKey -> volatilityData[periodKey] = null }
            }

            val metadataEntity: InstrumentMetadataEntity? =
                instrumentRepository
                    .findById(
                        symbol.uppercase(),
                    ).orElse(null)
            val metadataDto =
                metadataEntity?.let { entity ->
                    com.harshsbajwa.stockifai.api.dto.InstrumentMetadata(
                        symbol = entity.symbol,
                        name = entity.name,
                        exchange = entity.exchange,
                        currency = entity.currency,
                        sector = entity.sector,
                        industry = entity.industry,
                        description = entity.description,
                    )
                }

            VolatilityResponse(
                symbol = symbol,
                periods = volatilityData,
                endDate = endDate,
                metadata = metadataDto,
            )
        } catch (e: Exception) {
            logger.error("Error fetching volatility metrics for $symbol: ${e.message}", e)
            null
        }
}
