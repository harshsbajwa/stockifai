package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.RiskAnalysisService
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
    private val riskAnalysisService: RiskAnalysisService,
    private val volatilityAnalysisService: VolatilityAnalysisService
) {

    @GetMapping("/risk/stock/{symbol}")
    @PreAuthorize("isAuthenticated()")
    fun getStockRiskMetrics(
        @PathVariable symbol: String,
        @Valid @ModelAttribute queryParams: RiskQueryParams
    ): ResponseEntity<ApiResponse<StockRiskResponse>> {
        val riskMetrics = riskAnalysisService.getStockRiskMetrics(symbol, queryParams)
        
        return if (riskMetrics != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = riskMetrics,
                message = "Risk metrics retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Risk metrics not found for symbol: $symbol"
            ))
        }
    }

    @GetMapping("/volatility/stock/{symbol}")
    @PreAuthorize("isAuthenticated()")
    fun getStockVolatilityMetrics(
        @PathVariable symbol: String,
        @Valid @ModelAttribute queryParams: VolatilityQueryParams
    ): ResponseEntity<ApiResponse<VolatilityResponse>> {
        val volatilityMetrics = volatilityAnalysisService.getVolatilityMetrics(symbol, queryParams)
        
        return if (volatilityMetrics != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = volatilityMetrics,
                message = "Volatility metrics retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Volatility metrics not found for symbol: $symbol"
            ))
        }
    }
}