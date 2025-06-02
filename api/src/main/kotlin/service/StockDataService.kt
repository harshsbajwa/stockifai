package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.model.StockSummary
import com.harshsbajwa.stockifai.api.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ceil


@Service
class StockDataService(
    private val stockRepository: StockRepository,
    private val influxDBService: InfluxDBService
) {
    private val logger = LoggerFactory.getLogger(StockDataService::class.java)

    fun getLatestStockData(symbol: String): StockDataResponse? {
        return try {
            val stockSummary: StockSummary? = stockRepository.findById(symbol.uppercase()).orElse(null)
            stockSummary?.let { convertSummaryToResponse(it) }
        } catch (e: Exception) {
            logger.error("Error fetching latest stock data for $symbol from Cassandra: ${e.message}", e)
            null
        }
    }

    fun getStockDetailedTimeSeries(
        symbol: String,
        hours: Long = 24,
        aggregation: String = "1h"
    ): StockMetricsResponse? {
        logger.debug("StockDataService: Delegating to InfluxDBService for time-series for {}", symbol)
        return influxDBService.getStockTimeSeries(symbol, hours, aggregation)
    }

    fun getAllStockSummaries(page: Int = 0, size: Int = 50): PaginatedResponse<StockDataResponse> {
        return try {
            val symbolProjections = stockRepository.findAllDistinctSymbols()
            val symbols = symbolProjections.map { it.symbol }
            
            if (symbols.isEmpty()) {
                logger.warn("No stock symbols found in stock_summaries table.")
                return PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
            }

            val totalElements = symbols.size.toLong()
            val totalPages = ceil(totalElements.toDouble() / size).toInt()
            
            val startIndex = page * size
            val endIndex = minOf(startIndex + size, symbols.size)
            val pageSymbols = symbols.subList(startIndex, endIndex)
            
            val stockResponses: List<StockDataResponse> = pageSymbols.mapNotNull { sym ->
                stockRepository.findById(sym.uppercase()).orElse(null)?.let { convertSummaryToResponse(it) }
            }

            PaginatedResponse(
                data = stockResponses,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0
            )
        } catch (e: Exception) {
            logger.error("Error fetching all stock summaries from Cassandra: ${e.message}", e)
            PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
        }
    }

    fun getMarketOverview(): MarketOverviewResponse {
        return try {
            val symbolProjections = stockRepository.findAllDistinctSymbols()
            val symbols = symbolProjections.map { it.symbol }
            
            if (symbols.isEmpty()) {
                logger.warn("No symbols available for market overview from stock_summaries.")
                return MarketOverviewResponse(0, 0, 0.0, emptyList(), 
                    TopMovers(emptyList(), emptyList(), emptyList()), "UNKNOWN", Instant.now())
            }

            val latestStockSummaries: List<StockSummary> = symbols.mapNotNull { symbol ->
                stockRepository.findById(symbol.uppercase()).orElse(null)
            }

            val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
            val activeStocks = latestStockSummaries.filter { 
                it.lastTimestamp?.let { ts -> Instant.ofEpochMilli(ts).isAfter(oneHourAgo) } ?: false
            }

            val averageRiskScore = if (activeStocks.isNotEmpty()) {
                activeStocks.mapNotNull { it.latestRiskScore }.filter { it.isFinite() }.average()
            } else 0.0

            val highRiskStocks = activeStocks
                .filter { (it.latestRiskScore ?: 0.0) > 70.0 }
                .map { it.symbol }

            val topMovers = calculateTopMoversFromSummaries(activeStocks)
            val marketSentiment = determineMarketSentimentFromSummaries(activeStocks)

            MarketOverviewResponse(
                totalStocks = symbols.size,
                activeStocks = activeStocks.size,
                averageRiskScore = if(averageRiskScore.isNaN()) 0.0 else averageRiskScore,
                highRiskStocks = highRiskStocks,
                topMovers = topMovers,
                marketSentiment = marketSentiment,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            logger.error("Error generating market overview from Cassandra summaries: ${e.message}", e)
            MarketOverviewResponse(0, 0, 0.0, emptyList(), 
                TopMovers(emptyList(), emptyList(), emptyList()), "UNKNOWN", Instant.now())
        }
    }

    private fun calculateTopMoversFromSummaries(summaries: List<StockSummary>): TopMovers {
        val gainers = summaries
            .filter { (it.priceChangePercentToday ?: 0.0) > 0 }
            .sortedByDescending { it.priceChangePercentToday }
            .take(5)
            .map { convertSummaryToMover(it) }

        val losers = summaries
            .filter { (it.priceChangePercentToday ?: 0.0) < 0 }
            .sortedBy { it.priceChangePercentToday }
            .take(5)
            .map { convertSummaryToMover(it) }

        val mostVolatile = summaries
            .filter { it.latestVolatility != null && it.latestVolatility!!.isFinite() }
            .sortedByDescending { it.latestVolatility }
            .take(5)
            .map { convertSummaryToMover(it) }

        return TopMovers(gainers, losers, mostVolatile)
    }

    private fun determineMarketSentimentFromSummaries(summaries: List<StockSummary>): String {
        if (summaries.isEmpty()) return "UNKNOWN"

        val bullishCount = summaries.count { it.latestTrend == "BULLISH" }
        val bearishCount = summaries.count { it.latestTrend == "BEARISH" }
        val total = summaries.size.toDouble()
        if (total == 0.0) return "UNKNOWN"

        return when {
            bullishCount / total > 0.6 -> "BULLISH"
            bearishCount / total > 0.6 -> "BEARISH"
            else -> "NEUTRAL"
        }
    }

    private fun convertSummaryToResponse(summary: StockSummary): StockDataResponse {
        return StockDataResponse(
            symbol = summary.symbol,
            currentPrice = summary.currentPrice,
            volume = summary.latestVolume,
            volatility = summary.latestVolatility,
            priceChange = summary.priceChangeToday,
            priceChangePercent = summary.priceChangePercentToday,
            volumeAverage = null,
            riskScore = summary.latestRiskScore,
            trend = summary.latestTrend,
            support = null,
            resistance = null,
            timestamp = summary.lastTimestamp?.let { Instant.ofEpochMilli(it) } ?: Instant.EPOCH
        )
    }

    private fun convertSummaryToMover(summary: StockSummary): StockMover {
        return StockMover(
            symbol = summary.symbol,
            currentPrice = summary.currentPrice,
            change = summary.priceChangeToday,
            changePercent = summary.priceChangePercentToday,
            volume = summary.latestVolume
        )
    }
}