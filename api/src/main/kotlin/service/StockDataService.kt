package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.model.ProcessedStock
import com.harshsbajwa.stockifai.api.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
class StockDataService(
    private val stockRepository: StockRepository
) {
    private val logger = LoggerFactory.getLogger(StockDataService::class.java)

    fun getLatestStockData(symbol: String): StockDataResponse? {
        return try {
            val stock = stockRepository.findLatestBySymbol(symbol.uppercase())
            stock?.let { convertToResponse(it) }
        } catch (e: Exception) {
            logger.error("Error fetching latest stock data for $symbol", e)
            null
        }
    }

    fun getStockHistory(
        symbol: String,
        hours: Long = 24,
        page: Int = 0,
        size: Int = 100
    ): StockHistoryResponse? {
        return try {
            val endTime = Instant.now()
            val startTime = endTime.minus(hours, ChronoUnit.HOURS)
            
            val pageable = PageRequest.of(page, size)
            val stocks = stockRepository.findBySymbolAndTimestampBetween(
                symbol.uppercase(),
                startTime.toEpochMilli(),
                endTime.toEpochMilli(),
                pageable
            )

            StockHistoryResponse(
                symbol = symbol.uppercase(),
                data = stocks.content.map { convertToDataPoint(it) },
                count = stocks.numberOfElements,
                timeRange = TimeRange(startTime, endTime)
            )
        } catch (e: Exception) {
            logger.error("Error fetching stock history for $symbol", e)
            null
        }
    }

    fun getAllActiveStocks(page: Int = 0, size: Int = 50): PaginatedResponse<StockDataResponse> {
        return try {
            val symbols = stockRepository.findAllDistinctSymbols()
            val totalElements = symbols.size.toLong()
            val totalPages = ceil(totalElements.toDouble() / size).toInt()
            
            val startIndex = page * size
            val endIndex = minOf(startIndex + size, symbols.size)
            val pageSymbols = symbols.subList(startIndex, endIndex)
            
            val stocks = pageSymbols.mapNotNull { symbol ->
                stockRepository.findLatestBySymbol(symbol)?.let { convertToResponse(it) }
            }

            PaginatedResponse(
                data = stocks,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0
            )
        } catch (e: Exception) {
            logger.error("Error fetching all active stocks", e)
            PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
        }
    }

    fun getMarketOverview(): MarketOverviewResponse {
        return try {
            val symbols = stockRepository.findAllDistinctSymbols()
            val latestStocks = symbols.mapNotNull { symbol ->
                stockRepository.findLatestBySymbol(symbol)
            }

            val activeStocks = latestStocks.filter { 
                Instant.ofEpochMilli(it.primaryKey.timestamp)
                    .isAfter(Instant.now().minus(1, ChronoUnit.HOURS))
            }

            val averageRiskScore = if (activeStocks.isNotEmpty()) {
                activeStocks.map { it.riskScore }.average()
            } else 0.0

            val highRiskStocks = activeStocks
                .filter { it.riskScore > 70.0 }
                .map { it.primaryKey.symbol }

            val topMovers = calculateTopMovers(activeStocks)
            val marketSentiment = determineMarketSentiment(activeStocks)

            MarketOverviewResponse(
                totalStocks = symbols.size,
                activeStocks = activeStocks.size,
                averageRiskScore = averageRiskScore,
                highRiskStocks = highRiskStocks,
                topMovers = topMovers,
                marketSentiment = marketSentiment,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            logger.error("Error generating market overview", e)
            MarketOverviewResponse(0, 0, 0.0, emptyList(), 
                TopMovers(emptyList(), emptyList(), emptyList()), "UNKNOWN", Instant.now())
        }
    }

    private fun calculateTopMovers(stocks: List<ProcessedStock>): TopMovers {
        val gainers = stocks
            .filter { it.priceChangePercent > 0 }
            .sortedByDescending { it.priceChangePercent }
            .take(5)
            .map { convertToMover(it) }

        val losers = stocks
            .filter { it.priceChangePercent < 0 }
            .sortedBy { it.priceChangePercent }
            .take(5)
            .map { convertToMover(it) }

        val mostVolatile = stocks
            .sortedByDescending { it.volatility }
            .take(5)
            .map { convertToMover(it) }

        return TopMovers(gainers, losers, mostVolatile)
    }

    private fun determineMarketSentiment(stocks: List<ProcessedStock>): String {
        if (stocks.isEmpty()) return "UNKNOWN"

        val bullishCount = stocks.count { it.trend == "BULLISH" }
        val bearishCount = stocks.count { it.trend == "BEARISH" }
        val total = stocks.size

        return when {
            bullishCount > total * 0.6 -> "BULLISH"
            bearishCount > total * 0.6 -> "BEARISH"
            else -> "NEUTRAL"
        }
    }

    private fun convertToResponse(stock: ProcessedStock): StockDataResponse {
        return StockDataResponse(
            symbol = stock.primaryKey.symbol,
            currentPrice = stock.currentPrice,
            volume = stock.volume,
            volatility = stock.volatility,
            priceChange = stock.priceChange,
            priceChangePercent = stock.priceChangePercent,
            volumeAverage = stock.volumeAverage,
            riskScore = stock.riskScore,
            trend = stock.trend,
            support = stock.support,
            resistance = stock.resistance,
            timestamp = Instant.ofEpochMilli(stock.primaryKey.timestamp)
        )
    }

    private fun convertToDataPoint(stock: ProcessedStock): StockDataPoint {
        return StockDataPoint(
            timestamp = Instant.ofEpochMilli(stock.primaryKey.timestamp),
            price = stock.currentPrice,
            volume = stock.volume,
            volatility = stock.volatility,
            riskScore = stock.riskScore,
            trend = stock.trend
        )
    }

    private fun convertToMover(stock: ProcessedStock): StockMover {
        return StockMover(
            symbol = stock.primaryKey.symbol,
            currentPrice = stock.currentPrice,
            change = stock.priceChange,
            changePercent = stock.priceChangePercent,
            volume = stock.volume
        )
    }
}