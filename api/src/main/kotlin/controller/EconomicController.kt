package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.EconomicIndicatorService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/economic")
@CrossOrigin(origins = ["*"])
class EconomicController(
    private val economicIndicatorService: EconomicIndicatorService
) {

    @GetMapping("/indicators/{indicator}")
    fun getIndicator(@PathVariable indicator: String): ResponseEntity<ApiResponse<EconomicIndicatorResponse>> {
        val indicatorData = economicIndicatorService.getLatestIndicator(indicator)
        
        return if (indicatorData != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = indicatorData,
                message = "Economic indicator retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Economic indicator not found: $indicator"
            ))
        }
    }

    @GetMapping("/indicators/{indicator}/history")
    fun getIndicatorHistory(
        @PathVariable indicator: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<EconomicIndicatorResponse>>> {
        val history = economicIndicatorService.getIndicatorHistory(indicator, page, size)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = history,
            message = "Economic indicator history retrieved successfully"
        ))
    }

    @GetMapping("/indicators")
    fun getAllIndicators(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<EconomicIndicatorResponse>>> {
        val indicators = economicIndicatorService.getAllLatestIndicators(page, size)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = indicators,
            message = "Economic indicators retrieved successfully"
        ))
    }
}