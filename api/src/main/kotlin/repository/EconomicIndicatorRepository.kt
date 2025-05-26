package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.EconomicIndicatorEntity
import com.harshsbajwa.stockifai.api.model.IndicatorPrimaryKey
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository

@Repository
interface EconomicIndicatorRepository : CassandraRepository<EconomicIndicatorEntity, IndicatorPrimaryKey> {

    @Query("SELECT * FROM economic_indicators WHERE indicator = ?0 ORDER BY timestamp DESC")
    fun findByIndicatorOrderByTimestampDesc(indicator: String, pageable: Pageable): Slice<EconomicIndicatorEntity>

    @Query("SELECT * FROM economic_indicators WHERE indicator = ?0 ORDER BY timestamp DESC LIMIT 1")
    fun findLatestByIndicator(indicator: String): EconomicIndicatorEntity?

    @Query("SELECT DISTINCT indicator FROM economic_indicators")
    fun findAllDistinctIndicators(): List<String>

    @Query("SELECT * FROM economic_indicators WHERE indicator = ?0 AND timestamp >= ?1 AND timestamp <= ?2 ORDER BY timestamp DESC")
    fun findByIndicatorAndTimestampBetween(
        indicator: String,
        startTimestamp: Long,
        endTimestamp: Long,
        pageable: Pageable
    ): Slice<EconomicIndicatorEntity>
}