package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.EconomicIndicatorService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/v1/economic")
@CrossOrigin(origins = ["*"])
@Validated
class EconomicController(
    private val economicIndicatorService: EconomicIndicatorService
) {
    @GetMapping("/indicators")
    fun getAllLatestIndicatorSummaries(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<EconomicIndicatorResponse>>> {
        val indicatorsData = economicIndicatorService.getAllLatestIndicatorSummaries(page, size)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = indicatorsData,
            message = "Latest economic indicator summaries retrieved successfully."
        ))
    }

    @GetMapping("/indicators/{indicatorId}")
    fun getLatestIndicatorSummary(@PathVariable indicatorId: String): ResponseEntity<ApiResponse<EconomicIndicatorResponse>> {
        val indicatorData = economicIndicatorService.getLatestIndicatorSummary(indicatorId)
        
        return if (indicatorData != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = indicatorData,
                message = "Latest economic indicator summary retrieved successfully for $indicatorId."
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Latest economic indicator summary not found for: $indicatorId"
            ))
        }
    }

    @GetMapping("/indicators/{indicatorId}/timeseries")
    fun getIndicatorTimeSeries(
        @PathVariable indicatorId: String,
        @RequestParam(defaultValue = "30") days: Long
    ): ResponseEntity<ApiResponse<List<EconomicDataPoint>>> {
        val timeSeriesData = economicIndicatorService.getEconomicIndicatorTimeSeries(indicatorId, days)
        
        return if (timeSeriesData != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = timeSeriesData,
                message = "Economic indicator time-series data retrieved successfully for $indicatorId."
            ))
        } else {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
                success = false,
                message = "Error retrieving time-series data for economic indicator: $indicatorId"
            ))
        }
    }
}