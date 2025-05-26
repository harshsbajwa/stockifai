package com.harshsbajwa.stockifai.api.dto

import java.time.Instant

data class StockDataResponse(
    val symbol: String,
    val currentPrice: Double,
    val volume: Long,
    val volatility: Double,
    val priceChange: Double,
    val priceChangePercent: Double,
    val volumeAverage: Double,
    val riskScore: Double,
    val trend: String,
    val support: Double?,
    val resistance: Double?,
    val timestamp: Instant,
    val lastUpdated: Instant = Instant.now()
)

data class StockHistoryResponse(
    val symbol: String,
    val data: List<StockDataPoint>,
    val count: Int,
    val timeRange: TimeRange
)

data class StockDataPoint(
    val timestamp: Instant,
    val price: Double,
    val volume: Long,
    val volatility: Double,
    val riskScore: Double,
    val trend: String
)

data class TimeRange(
    val start: Instant,
    val end: Instant
)

data class EconomicIndicatorResponse(
    val indicator: String,
    val value: Double,
    val country: String?,
    val timestamp: Instant,
    val description: String? = null
)

data class MarketOverviewResponse(
    val totalStocks: Int,
    val activeStocks: Int,
    val averageRiskScore: Double,
    val highRiskStocks: List<String>,
    val topMovers: TopMovers,
    val marketSentiment: String,
    val lastUpdated: Instant
)

data class TopMovers(
    val gainers: List<StockMover>,
    val losers: List<StockMover>,
    val mostVolatile: List<StockMover>
)

data class StockMover(
    val symbol: String,
    val currentPrice: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long
)

data class StockMetricsResponse(
    val symbol: String,
    val metrics: List<MetricPoint>,
    val timeRange: TimeRange,
    val aggregation: String
)

data class MetricPoint(
    val timestamp: Instant,
    val price: Double?,
    val volume: Long?,
    val volatility: Double?,
    val riskScore: Double?
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val timestamp: Instant = Instant.now(),
    val errors: List<String>? = null
)

data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)