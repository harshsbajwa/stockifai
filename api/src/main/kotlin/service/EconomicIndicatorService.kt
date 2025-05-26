package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.EconomicIndicatorResponse
import com.harshsbajwa.stockifai.api.dto.PaginatedResponse
import com.harshsbajwa.stockifai.api.model.EconomicIndicatorEntity
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.ceil

@Service
class EconomicIndicatorService(
    private val indicatorRepository: EconomicIndicatorRepository
) {
    private val logger = LoggerFactory.getLogger(EconomicIndicatorService::class.java)

    private val indicatorDescriptions = mapOf(
        "FEDFUNDS" to "Federal Funds Rate",
        "VIXCLS" to "VIX Volatility Index",
        "DGS10" to "10-Year Treasury Rate",
        "UNRATE" to "Unemployment Rate",
        "CPIAUCSL" to "Consumer Price Index"
    )

    fun getLatestIndicator(indicator: String): EconomicIndicatorResponse? {
        return try {
            val entity = indicatorRepository.findLatestByIndicator(indicator.uppercase())
            entity?.let { convertToResponse(it) }
        } catch (e: Exception) {
            logger.error("Error fetching latest indicator $indicator", e)
            null
        }
    }

    fun getAllLatestIndicators(page: Int = 0, size: Int = 20): PaginatedResponse<EconomicIndicatorResponse> {
        return try {
            val indicators = indicatorRepository.findAllDistinctIndicators()
            val totalElements = indicators.size.toLong()
            val totalPages = ceil(totalElements.toDouble() / size).toInt()
            
            val startIndex = page * size
            val endIndex = minOf(startIndex + size, indicators.size)
            val pageIndicators = indicators.subList(startIndex, endIndex)
            
            val responses = pageIndicators.mapNotNull { indicator ->
                indicatorRepository.findLatestByIndicator(indicator)?.let { convertToResponse(it) }
            }

            PaginatedResponse(
                data = responses,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0
            )
        } catch (e: Exception) {
            logger.error("Error fetching all indicators", e)
            PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
        }
    }

    fun getIndicatorHistory(
        indicator: String,
        page: Int = 0,
        size: Int = 100
    ): PaginatedResponse<EconomicIndicatorResponse> {
        return try {
            val pageable = PageRequest.of(page, size)
            val slice = indicatorRepository.findByIndicatorOrderByTimestampDesc(
                indicator.uppercase(), pageable
            )

            val responses = slice.content.map { convertToResponse(it) }

            PaginatedResponse(
                data = responses,
                page = page,
                size = size,
                totalElements = slice.numberOfElements.toLong(),
                totalPages = if (slice.hasNext()) page + 2 else page + 1,
                hasNext = slice.hasNext(),
                hasPrevious = slice.hasPrevious()
            )
        } catch (e: Exception) {
            logger.error("Error fetching indicator history for $indicator", e)
            PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
        }
    }

    private fun convertToResponse(entity: EconomicIndicatorEntity): EconomicIndicatorResponse {
        return EconomicIndicatorResponse(
            indicator = entity.primaryKey.indicator,
            value = entity.value,
            country = entity.country,
            timestamp = Instant.ofEpochMilli(entity.primaryKey.timestamp),
            description = indicatorDescriptions[entity.primaryKey.indicator]
        )
    }
}