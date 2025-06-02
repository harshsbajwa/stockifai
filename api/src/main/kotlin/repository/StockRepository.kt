package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.StockSummary
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository


interface SymbolProjection {
    val symbol: String
}


@Repository
interface StockRepository : CassandraRepository<StockSummary, String> {
    @Query("SELECT DISTINCT symbol FROM stock_summaries")
    fun findAllDistinctSymbols(): List<SymbolProjection> 
}
