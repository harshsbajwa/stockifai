package com.harshsbajwa.stockifai.api.model

import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Table

@Table("processed_stocks")
data class ProcessedStock(
    @PrimaryKey
    val primaryKey: StockPrimaryKey,
    
    @Column("current_price")
    val currentPrice: Double,
    
    @Column("volume")
    val volume: Long,
    
    @Column("volatility")
    val volatility: Double,
    
    @Column("price_change")
    val priceChange: Double,
    
    @Column("price_change_percent")
    val priceChangePercent: Double,
    
    @Column("volume_average")
    val volumeAverage: Double,
    
    @Column("risk_score")
    val riskScore: Double,
    
    @Column("trend")
    val trend: String,
    
    @Column("support")
    val support: Double?,
    
    @Column("resistance")
    val resistance: Double?
) {
    constructor() : this(
        StockPrimaryKey(), 0.0, 0L, 0.0, 0.0, 0.0, 0.0, 0.0, "", null, null
    )
}

@PrimaryKeyClass
data class StockPrimaryKey(
    @PrimaryKeyColumn(
        name = "symbol",
        ordinal = 0,
        type = PrimaryKeyType.PARTITIONED
    )
    val symbol: String = "",
    
    @PrimaryKeyColumn(
        name = "timestamp",
        ordinal = 1,
        type = PrimaryKeyType.CLUSTERED
    )
    val timestamp: Long = 0L
)

@Table("economic_indicators")
data class EconomicIndicatorEntity(
    @PrimaryKey
    val primaryKey: IndicatorPrimaryKey,
    
    @Column("value")
    val value: Double,
    
    @Column("country")
    val country: String?
) {
    constructor() : this(IndicatorPrimaryKey(), 0.0, null)
}

@PrimaryKeyClass
data class IndicatorPrimaryKey(
    @PrimaryKeyColumn(
        name = "indicator",
        ordinal = 0,
        type = PrimaryKeyType.PARTITIONED
    )
    val indicator: String = "",
    
    @PrimaryKeyColumn(
        name = "timestamp", 
        ordinal = 1,
        type = PrimaryKeyType.CLUSTERED
    )
    val timestamp: Long = 0L
)