package com.harshsbajwa.stockifai.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.time.LocalDate

// Risk metrics DTOs
data class StockRiskResponse(
    val symbol: String,
    val date: LocalDate,
    val historicalVolatility: HistoricalVolatilityData?,
    val beta: BetaData?,
    val valueAtRisk: VaRData?,
    val conditionalValueAtRisk: CVaRData?,
    val metadata: InstrumentMetadata?
)

data class HistoricalVolatilityData(
    val period30d: Double?,
    val period60d: Double?,
    val period90d: Double?,
    val calculationDate: LocalDate
)

data class BetaData(
    val value: Double,
    val benchmark: String,
    val lookbackPeriod: String,
    val calculationDate: LocalDate
)

data class VaRData(
    val confidence99: Double?,
    val confidence95: Double?,
    val method: String = "Historical Simulation",
    val lookbackPeriod: String,
    val calculationDate: LocalDate
)

data class CVaRData(
    val confidence99: Double?,
    val confidence95: Double?,
    val method: String = "Historical Simulation",
    val lookbackPeriod: String,
    val calculationDate: LocalDate
)

data class VolatilityResponse(
    val symbol: String,
    val periods: Map<String, Double>, // e.g., "30d" -> 0.25
    val endDate: LocalDate,
    val metadata: InstrumentMetadata?
)

data class EconomicIndicatorResponse(
    val seriesId: String,
    val observations: List<EconomicObservation>,
    val metadata: EconomicIndicatorMetadata?
)

data class EconomicObservation(
    val date: LocalDate,
    val value: Double,
    val realTimeStart: LocalDate,
    val realTimeEnd: LocalDate
)

data class MarketIndexResponse(
    val indexSymbol: String,
    val riskMetrics: StockRiskResponse,
    val volatilityMetrics: VolatilityResponse
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val timestamp: Instant = Instant.now(),
    val errors: List<String>? = null
)

data class InstrumentMetadata(
    val symbol: String,
    val name: String?,
    val exchange: String?,
    val currency: String?,
    val sector: String?,
    val industry: String?,
    val description: String?
)

data class EconomicIndicatorMetadata(
    val seriesId: String,
    val title: String?,
    val frequency: String?,
    val units: String?,
    val notes: String?,
    val source: String?
)

// Validation DTOs for request parameters
data class RiskQueryParams(
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in YYYY-MM-DD format")
    val date: String? = null,
    
    @field:Pattern(regexp = "\\d+[dmyh]", message = "Lookback period must be in format like '252d', '1y'")
    val lookbackPeriod: String = "252d",
    
    @field:NotBlank(message = "Beta benchmark cannot be blank")
    val betaBenchmark: String = "SPY"
)

data class VolatilityQueryParams(
    val periods: String = "30d,60d,90d", // Comma-separated
    
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "End date must be in YYYY-MM-DD format")
    val endDate: String? = null
)

data class EconomicQueryParams(
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Start date must be in YYYY-MM-DD format")
    val startDate: String? = null,
    
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "End date must be in YYYY-MM-DD format")
    val endDate: String? = null,
    
    val limit: Int = 100
)