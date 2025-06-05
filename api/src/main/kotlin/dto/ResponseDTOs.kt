package com.harshsbajwa.stockifai.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.time.LocalDate

data class StockDataResponse(
    val symbol: String,
    val currentPrice: Double,
    val volume: Long?,
    val volatility: Double?,
    val priceChange: Double?,
    val priceChangePercent: Double?,
    val volumeAverage: Double?,
    val riskScore: Double?,
    val trend: String?,
    val support: Double?,
    val resistance: Double?,
    val timestamp: Instant,
)

data class StockHistoryResponse(
    val symbol: String,
    val data: List<StockDataPoint>,
    val count: Int,
    val timeRange: TimeRange,
)

data class StockDataPoint(
    val timestamp: Instant,
    val price: Double?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volume: Long?,
    val volatility: Double?,
    val riskScore: Double?,
    val trend: String?,
)

data class TimeRange(
    val start: Instant,
    val end: Instant,
)

data class MetricPoint(
    val timestamp: Instant,
    val price: Double?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volume: Long?,
    val volatility: Double?,
    val riskScore: Double?,
)

data class StockMetricsResponse(
    val symbol: String,
    val metrics: List<MetricPoint>,
    val timeRange: TimeRange,
    val aggregation: String,
)

data class MarketOverviewResponse(
    val totalStocks: Int,
    val activeStocks: Int,
    val averageRiskScore: Double,
    val highRiskStocks: List<String>,
    val topMovers: TopMovers,
    val marketSentiment: String,
    val lastUpdated: Instant,
)

data class TopMovers(
    val gainers: List<StockMover>,
    val losers: List<StockMover>,
    val mostVolatile: List<StockMover>,
)

data class StockMover(
    val symbol: String,
    val currentPrice: Double?,
    val change: Double?,
    val changePercent: Double?,
    val volume: Long?,
)

data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

data class StockRiskResponse(
    val symbol: String,
    val date: LocalDate,
    val historicalVolatility: HistoricalVolatilityData?,
    val beta: BetaData?,
    val valueAtRisk: VaRData?,
    val conditionalValueAtRisk: CVaRData?,
    val metadata: InstrumentMetadata?,
)

data class HistoricalVolatilityData(
    val period30d: Double?,
    val period60d: Double?,
    val period90d: Double?,
    val calculationDate: LocalDate,
)

data class BetaData(
    val value: Double?,
    val benchmark: String?,
    val lookbackPeriod: String?,
    val calculationDate: LocalDate?,
)

data class VaRData(
    val confidence99: Double?,
    val confidence95: Double?,
    val method: String = "Historical Simulation",
    val lookbackPeriod: String?,
    val calculationDate: LocalDate?,
)

data class CVaRData(
    val confidence99: Double?,
    val confidence95: Double?,
    val method: String = "Historical Simulation",
    val lookbackPeriod: String?,
    val calculationDate: LocalDate?,
)

data class VolatilityResponse(
    val symbol: String,
    val periods: Map<String, Double?>,
    val endDate: LocalDate?,
    val metadata: InstrumentMetadata?,
)

data class EconomicIndicatorResponse(
    val series_id: String,
    val observations: List<EconomicObservation>,
    val metadata: EconomicIndicatorMetadata?,
)

data class EconomicObservation(
    val date: LocalDate?,
    val value: Double?,
    val realTimeStart: LocalDate?,
    val realTimeEnd: LocalDate?,
)

data class MarketIndexResponse(
    val indexSymbol: String,
    val riskMetrics: StockRiskResponse?,
    val volatilityMetrics: VolatilityResponse?,
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val timestamp: Instant = Instant.now(),
    val errors: List<String>? = null,
)

data class InstrumentMetadata(
    val symbol: String,
    val name: String?,
    val exchange: String?,
    val currency: String?,
    val sector: String?,
    val industry: String?,
    val description: String?,
)

data class EconomicIndicatorMetadata(
    val series_id: String,
    val title: String?,
    val frequency: String?,
    val units: String?,
    val notes: String?,
    val source: String?,
)

data class RiskQueryParams(
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in YYYY-MM-DD format")
    val date: String? = null,
    @field:Pattern(regexp = "\\d+[dmyh]", message = "Lookback period must be in format like '252d', '1y'")
    val lookbackPeriod: String = "252d",
    @field:NotBlank(message = "Beta benchmark cannot be blank")
    val betaBenchmark: String = "SPY",
)

data class VolatilityQueryParams(
    val periods: String = "30d,60d,90d",
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "End date must be in YYYY-MM-DD format")
    val endDate: String? = null,
)

data class EconomicQueryParams(
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Start date must be in YYYY-MM-DD format")
    val startDate: String? = null,
    @field:Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "End date must be in YYYY-MM-DD format")
    val endDate: String? = null,
    val limit: Int = 100,
)

data class NewsItem(
    val id: String,
    val headline: String,
    val summary: String,
    val sentiment: String,
    val timestamp: Instant,
    val source: String?,
    val relatedSymbol: String?,
)

data class EconomicDataPoint(
    val series_id: String,
    val value: Double,
    val timestamp: Instant,
)
