package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.repository.InstrumentMetadataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import com.harshsbajwa.stockifai.api.model.InstrumentMetadata as InstrumentMetadataEntity

@Service
class RiskAnalysisService(
    private val stockDataService: StockDataService,
    private val instrumentRepository: InstrumentMetadataRepository,
    private val influxDBService: InfluxDBService,
) {
    private val logger = LoggerFactory.getLogger(RiskAnalysisService::class.java)

    fun getStockRiskMetrics(
        symbol: String,
        queryParams: RiskQueryParams,
    ): StockRiskResponse? =
        try {
            val targetDate = queryParams.date?.let { LocalDate.parse(it) } ?: LocalDate.now()

            val latestStockSummary = stockDataService.getLatestStockData(symbol)

            val historicalVolatilityData =
                latestStockSummary?.volatility?.let {
                    HistoricalVolatilityData(
                        period30d = it,
                        period60d = it,
                        period90d = it,
                        calculationDate = targetDate,
                    )
                }

            val betaData =
                BetaData(
                    value = null,
                    benchmark = queryParams.betaBenchmark,
                    lookbackPeriod = queryParams.lookbackPeriod,
                    calculationDate = targetDate,
                )
            val vaRData =
                VaRData(
                    confidence99 = null,
                    confidence95 = null,
                    method = "Historical Simulation (Placeholder)",
                    lookbackPeriod = queryParams.lookbackPeriod,
                    calculationDate = targetDate,
                )
            val cVaRData =
                CVaRData(
                    confidence99 = null,
                    confidence95 = null,
                    method = "Historical Simulation (Placeholder)",
                    lookbackPeriod = queryParams.lookbackPeriod,
                    calculationDate = targetDate,
                )

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

            StockRiskResponse(
                symbol = symbol,
                date = targetDate,
                historicalVolatility = historicalVolatilityData,
                beta = betaData,
                valueAtRisk = vaRData,
                conditionalValueAtRisk = cVaRData,
                metadata = metadataDto,
            )
        } catch (e: Exception) {
            logger.error("Error fetching risk metrics for $symbol: ${e.message}", e)
            null
        }
}
