package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.StockDataService
import com.harshsbajwa.stockifai.api.service.InfluxDBService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/market")
@CrossOrigin(origins = ["*"])
class MarketController(
    private val stockDataService: StockDataService,
    private val influxDBService: InfluxDBService
) {

    @GetMapping("/overview")
    fun getMarketOverview(): ResponseEntity<ApiResponse<MarketOverviewResponse>> {
        val overview = stockDataService.getMarketOverview()
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = overview,
            message = "Market overview retrieved successfully"
        ))
    }

    @GetMapping("/volatility")
    fun getMarketVolatility(
        @RequestParam(defaultValue = "24") hours: Long
    ): ResponseEntity<ApiResponse<List<MetricPoint>>> {
        val volatility = influxDBService.getMarketVolatility(hours)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = volatility,
            message = "Market volatility retrieved successfully"
        ))
    }
}