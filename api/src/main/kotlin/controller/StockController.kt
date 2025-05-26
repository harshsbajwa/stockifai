package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.StockDataService
import com.harshsbajwa.stockifai.api.service.InfluxDBService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1/stocks")
@CrossOrigin(origins = ["*"])
class StockController(
    private val stockDataService: StockDataService,
    private val influxDBService: InfluxDBService
) {

    @GetMapping("/{symbol}")
    fun getStockData(@PathVariable symbol: String): ResponseEntity<ApiResponse<StockDataResponse>> {
        val stockData = stockDataService.getLatestStockData(symbol)
        
        return if (stockData != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = stockData,
                message = "Stock data retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Stock data not found for symbol: $symbol"
            ))
        }
    }

    @GetMapping("/{symbol}/history")
    fun getStockHistory(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "24") hours: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int
    ): ResponseEntity<ApiResponse<StockHistoryResponse>> {
        val history = stockDataService.getStockHistory(symbol, hours, page, size)
        
        return if (history != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = history,
                message = "Stock history retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Stock history not found for symbol: $symbol"
            ))
        }
    }

    @GetMapping("/{symbol}/metrics")
    fun getStockMetrics(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "24") hours: Long,
        @RequestParam(defaultValue = "5m") aggregation: String
    ): ResponseEntity<ApiResponse<StockMetricsResponse>> {
        val metrics = influxDBService.getStockMetrics(symbol, hours, aggregation)
        
        return if (metrics != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = metrics,
                message = "Stock metrics retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Stock metrics not found for symbol: $symbol"
            ))
        }
    }

    @GetMapping
    fun getAllStocks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<StockDataResponse>>> {
        val stocks = stockDataService.getAllActiveStocks(page, size)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = stocks,
            message = "Stocks retrieved successfully"
        ))
    }

    @GetMapping("/top-performers")
    fun getTopPerformers(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<List<Pair<String, Double>>>> {
        val performers = influxDBService.getTopPerformers(limit)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = performers,
            message = "Top performers retrieved successfully"
        ))
    }
}