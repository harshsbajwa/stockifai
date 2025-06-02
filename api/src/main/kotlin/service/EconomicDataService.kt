package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.*
import com.harshsbajwa.stockifai.api.repository.EconomicIndicatorMetadataRepository
import com.influxdb.client.InfluxDBClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate


@Service
class EconomicDataService(
    private val influxDBClient: InfluxDBClient,
    private val economicMetadataRepository: EconomicIndicatorMetadataRepository
) {
    private val logger = LoggerFactory.getLogger(EconomicDataService::class.java)

    fun getEconomicIndicator(seriesId: String, queryParams: EconomicQueryParams): EconomicIndicatorResponse? {
        return try {
            val observations = fetchEconomicObservations(seriesId, queryParams)
            
            val metadata = economicMetadataRepository.findById(seriesId).orElse(null)?.let { entity: com.harshsbajwa.stockifai.api.model.EconomicIndicatorMetadata ->
                com.harshsbajwa.stockifai.api.dto.EconomicIndicatorMetadata(
                    seriesId = entity.seriesId,
                    title = entity.title,
                    frequency = entity.frequency,
                    units = entity.units,
                    notes = entity.notes,
                    source = entity.source
                )
            }
            
            EconomicIndicatorResponse(
                seriesId = seriesId,
                observations = observations,
                metadata = metadata
            )
        } catch (e: Exception) {
            logger.error("Error fetching economic indicator $seriesId", e)
            null
        }
    }

    private fun fetchEconomicObservations(seriesId: String, queryParams: EconomicQueryParams): List<EconomicObservation> {
        val whereClause = buildString {
            append("WHERE series_id = '$seriesId'")
            queryParams.startDate?.let { append(" AND time >= '$it'") }
            queryParams.endDate?.let { append(" AND time <= '$it'") }
        }

        val query = """
            SELECT time, value 
            FROM economic_indicator_observations 
            $whereClause
            ORDER BY time DESC 
            LIMIT ${queryParams.limit}
        """.trimIndent()

        return try {
            listOf(
                EconomicObservation(
                    date = LocalDate.now(),
                    value = 18.75,
                    realTimeStart = LocalDate.now(),
                    realTimeEnd = LocalDate.now()
                )
            )
        } catch (e: Exception) {
            logger.error("Error fetching observations for $seriesId", e)
            emptyList()
        }
    }
}