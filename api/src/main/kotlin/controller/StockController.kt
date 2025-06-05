package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.InfluxDBService
import com.harshsbajwa.stockifai.api.service.RiskAnalysisService
import com.harshsbajwa.stockifai.api.service.StockDataService
import com.harshsbajwa.stockifai.api.service.VolatilityAnalysisService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
@Validated
class StockController(
    private val stockDataService: StockDataService,
    private val influxDBService: InfluxDBService,
    private val riskAnalysisService: RiskAnalysisService,
    private val volatilityAnalysisService: VolatilityAnalysisService,
) {
    @GetMapping("/stocks/{symbol}")
    fun getStockData(
        @PathVariable symbol: String,
    ): ResponseEntity<ApiResponse<StockDataResponse>> {
        val stockData = stockDataService.getLatestStockData(symbol)

        return if (stockData != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = stockData,
                    message = "Stock data retrieved successfully",
                ),
            )
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse(
                    success = false,
                    message = "Stock data not found for symbol: $symbol",
                ),
            )
        }
    }

    @GetMapping("/stocks/{symbol}/timeseries")
    fun getStockDetailedTimeSeries(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "24") hours: Long,
        @RequestParam(defaultValue = "5m") aggregation: String,
    ): ResponseEntity<ApiResponse<StockMetricsResponse>> {
        val timeSeriesData = stockDataService.getStockDetailedTimeSeries(symbol, hours, aggregation)

        return if (timeSeriesData != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = timeSeriesData,
                    message = "Stock time-series data retrieved successfully",
                ),
            )
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse(
                    success = false,
                    message = "Stock time-series data not found for symbol: $symbol",
                ),
            )
        }
    }

    @GetMapping("/stocks")
    fun getAllStocks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<PaginatedResponse<StockDataResponse>>> {
        val stocksData = stockDataService.getAllStockSummaries(page, size)

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = stocksData,
                message = "All stocks retrieved successfully",
            ),
        )
    }

    @GetMapping("/stocks/top-performers")
    fun getTopPerformers(
        @RequestParam(defaultValue = "10") limit: Int,
    ): ResponseEntity<ApiResponse<List<Pair<String, Double>>>> {
        val topPerformers = influxDBService.getTopPerformingSymbols(limit)

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = topPerformers,
                message = "Top performers retrieved successfully",
            ),
        )
    }

    @GetMapping("/risk/stock/{symbol}")
    @PreAuthorize("isAuthenticated()")
    fun getStockRiskMetrics(
        @PathVariable symbol: String,
        @Valid @ModelAttribute queryParams: RiskQueryParams,
    ): ResponseEntity<ApiResponse<StockRiskResponse>> {
        val riskMetrics = riskAnalysisService.getStockRiskMetrics(symbol, queryParams)

        return if (riskMetrics != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = riskMetrics,
                    message = "Risk metrics retrieved successfully",
                ),
            )
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse(
                    success = false,
                    message = "Risk metrics not found for symbol: $symbol",
                ),
            )
        }
    }

    @GetMapping("/volatility/stock/{symbol}")
    @PreAuthorize("isAuthenticated()")
    fun getStockVolatilityMetrics(
        @PathVariable symbol: String,
        @Valid @ModelAttribute queryParams: VolatilityQueryParams,
    ): ResponseEntity<ApiResponse<VolatilityResponse>> {
        val volatilityMetrics = volatilityAnalysisService.getVolatilityMetrics(symbol, queryParams)

        return if (volatilityMetrics != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = volatilityMetrics,
                    message = "Volatility metrics retrieved successfully",
                ),
            )
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse(
                    success = false,
                    message = "Volatility metrics not found for symbol: $symbol",
                ),
            )
        }
    }
}
