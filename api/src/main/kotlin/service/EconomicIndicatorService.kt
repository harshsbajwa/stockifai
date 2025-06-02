package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.model.EconomicIndicatorSummary
import com.harshsbajwa.stockifai.api.model.EconomicIndicatorMetadata as EconomicIndicatorMetadataEntity
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorRepository
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorMetadataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil


@Service
class EconomicIndicatorService(
    private val indicatorSummaryRepository: EconomicIndicatorRepository,
    private val metadataRepository: EconomicIndicatorMetadataRepository, 
    private val influxDBService: InfluxDBService
) {
    private val logger = LoggerFactory.getLogger(EconomicIndicatorService::class.java)

    fun getLatestIndicatorSummary(indicatorId: String): EconomicIndicatorResponse? {
        return try {
            val summary: EconomicIndicatorSummary? = indicatorSummaryRepository.findById(indicatorId.uppercase()).orElse(null)
            val metadataEntity: EconomicIndicatorMetadataEntity? = metadataRepository.findById(indicatorId.uppercase()).orElse(null)
            
            summary?.let { convertSummaryToResponse(it, metadataEntity) }
        } catch (e: Exception) {
            logger.error("Error fetching latest economic indicator summary for $indicatorId from Cassandra: ${e.message}", e)
            null
        }
    }

    fun getAllLatestIndicatorSummaries(page: Int = 0, size: Int = 20): PaginatedResponse<EconomicIndicatorResponse> {
        return try {
            val indicatorIds = indicatorSummaryRepository.findAllDistinctIndicators()
            
            if (indicatorIds.isEmpty()) {
                logger.warn("No economic indicators found in economic_indicator_summaries table.")
                return PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
            }

            val totalElements = indicatorIds.size.toLong()
            val totalPages = ceil(totalElements.toDouble() / size).toInt()
            
            val startIndex = page * size
            val endIndex = minOf(startIndex + size, indicatorIds.size)
            val pageIndicatorIds = indicatorIds.subList(startIndex, endIndex)
            
            val responses: List<EconomicIndicatorResponse> = pageIndicatorIds.mapNotNull { id ->
                val summary = indicatorSummaryRepository.findById(id.uppercase()).orElse(null)
                val metadataEntity = metadataRepository.findById(id.uppercase()).orElse(null)
                summary?.let { convertSummaryToResponse(it, metadataEntity) }
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
            logger.error("Error fetching all latest economic indicator summaries from Cassandra: ${e.message}", e)
            PaginatedResponse(emptyList(), page, size, 0, 0, false, false)
        }
    }

    fun getEconomicIndicatorTimeSeries(
        seriesId: String,
        days: Long = 30
    ): List<EconomicDataPoint>? {
        logger.debug("EconomicIndicatorService: Delegating to InfluxDBService for time-series for {}", seriesId)
        return influxDBService.getEconomicTimeSeries(seriesId, days)
    }

    private fun convertSummaryToResponse(
        summary: EconomicIndicatorSummary,
        metadataEntity: EconomicIndicatorMetadataEntity?
    ): EconomicIndicatorResponse {
        val observationLocalDate = summary.observationDate?.let { 
            try { LocalDate.parse(it) } catch (e: Exception) { 
                logger.warn("Could not parse observationDate string '{}' for indicator {}", it, summary.indicator); null 
            } 
        } ?: summary.lastTimestamp?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
           ?: LocalDate.now()

        val observation = EconomicObservation(
            date = observationLocalDate,
            value = summary.latestValue,
            realTimeStart = observationLocalDate,
            realTimeEnd = observationLocalDate
        )
        
        val metadataDto = metadataEntity?.let {
            com.harshsbajwa.stockifai.api.dto.EconomicIndicatorMetadata(
                seriesId = it.seriesId,
                title = it.title,
                frequency = it.frequency,
                units = it.units,
                notes = it.notes,
                source = it.source
            )
        }

        return EconomicIndicatorResponse(
            seriesId = summary.indicator,
            observations = listOf(observation),
            metadata = metadataDto
        )
    }
}