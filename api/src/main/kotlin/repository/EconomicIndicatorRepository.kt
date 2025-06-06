package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.EconomicIndicatorSummary
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EconomicIndicatorRepository : CassandraRepository<EconomicIndicatorSummary, String> {
    @Query("SELECT * FROM economic_indicator_summaries")
    fun findAllEconomicIndicators(): List<EconomicIndicatorSummary>
}
