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
@RequestMapping("/api/v1/market")
@CrossOrigin(origins = ["*"])
@Validated
class MarketController(
    private val stockDataService: StockDataService,
    private val influxDBService: InfluxDBService,
    private val riskAnalysisService: RiskAnalysisService,
    private val volatilityAnalysisService: VolatilityAnalysisService,
) {
    @GetMapping("/overview")
    fun getMarketOverview(): ResponseEntity<ApiResponse<MarketOverviewResponse>> {
        val marketOverview = stockDataService.getMarketOverview()

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = marketOverview,
                message = "Market overview retrieved successfully",
            ),
        )
    }

    @GetMapping("/volatility")
    fun getMarketVolatility(
        @RequestParam(defaultValue = "24") hours: Long,
    ): ResponseEntity<ApiResponse<List<MetricPoint>>> {
        val volatilityData = influxDBService.getMarketVolatility(hours)

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = volatilityData,
                message = "Market volatility retrieved successfully",
            ),
        )
    }

    @GetMapping("/index/{indexSymbol}")
    @PreAuthorize("isAuthenticated()")
    fun getMarketIndexMetrics(
        @PathVariable indexSymbol: String,
        @Valid @ModelAttribute riskParams: RiskQueryParams,
        @Valid @ModelAttribute volatilityParams: VolatilityQueryParams,
    ): ResponseEntity<ApiResponse<MarketIndexResponse>> {
        val riskMetrics = riskAnalysisService.getStockRiskMetrics(indexSymbol, riskParams)
        val volatilityMetrics = volatilityAnalysisService.getVolatilityMetrics(indexSymbol, volatilityParams)

        return if (riskMetrics != null && volatilityMetrics != null) {
            val marketIndexResponse =
                MarketIndexResponse(
                    indexSymbol = indexSymbol,
                    riskMetrics = riskMetrics,
                    volatilityMetrics = volatilityMetrics,
                )

            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = marketIndexResponse,
                    message = "Market index metrics retrieved successfully",
                ),
            )
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse(
                    success = false,
                    message = "Market index metrics not found: $indexSymbol",
                ),
            )
        }
    }
}
