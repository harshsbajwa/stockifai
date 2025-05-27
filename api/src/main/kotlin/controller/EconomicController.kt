package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.service.EconomicDataService
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
    private val economicDataService: EconomicDataService
) {

    @GetMapping("/indicator/{seriesId}")
    @PreAuthorize("isAuthenticated()")
    fun getEconomicIndicator(
        @PathVariable seriesId: String,
        @Valid @ModelAttribute queryParams: EconomicQueryParams
    ): ResponseEntity<ApiResponse<EconomicIndicatorResponse>> {
        val indicatorData = economicDataService.getEconomicIndicator(seriesId, queryParams)
        
        return if (indicatorData != null) {
            ResponseEntity.ok(ApiResponse(
                success = true,
                data = indicatorData,
                message = "Economic indicator retrieved successfully"
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
                success = false,
                message = "Economic indicator not found: $seriesId"
            ))
        }
    }
}