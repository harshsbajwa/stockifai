package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.ProcessedStock
import com.harshsbajwa.stockifai.api.model.StockPrimaryKey
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository

@Repository
interface StockRepository : CassandraRepository<ProcessedStock, StockPrimaryKey> {

    @Query("SELECT * FROM processed_stocks WHERE symbol = ?0 ORDER BY timestamp DESC")
    fun findBySymbolOrderByTimestampDesc(symbol: String, pageable: Pageable): Slice<ProcessedStock>

    @Query("SELECT * FROM processed_stocks WHERE symbol = ?0 AND timestamp >= ?1 AND timestamp <= ?2 ORDER BY timestamp DESC")
    fun findBySymbolAndTimestampBetween(
        symbol: String,
        startTimestamp: Long,
        endTimestamp: Long,
        pageable: Pageable
    ): Slice<ProcessedStock>

    @Query("SELECT DISTINCT symbol FROM processed_stocks")
    fun findAllDistinctSymbols(): List<String>

    @Query("SELECT * FROM processed_stocks WHERE symbol = ?0 ORDER BY timestamp DESC LIMIT 1")
    fun findLatestBySymbol(symbol: String): ProcessedStock?

    @Query("SELECT * FROM processed_stocks WHERE symbol IN ?0 ORDER BY timestamp DESC")
    fun findLatestBySymbols(symbols: List<String>, pageable: Pageable): Slice<ProcessedStock>
}