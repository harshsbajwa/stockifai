package com.harshsbajwa.stockifai.api.model

import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import java.time.Instant

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

@org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
data class StockPrimaryKey(
    @org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn(
        name = "symbol",
        ordinal = 0,
        type = org.springframework.data.cassandra.core.mapping.PrimaryKeyType.PARTITIONED
    )
    val symbol: String = "",
    
    @org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn(
        name = "timestamp",
        ordinal = 1,
        type = org.springframework.data.cassandra.core.mapping.PrimaryKeyType.CLUSTERED
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

@org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
data class IndicatorPrimaryKey(
    @org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn(
        name = "indicator",
        ordinal = 0,
        type = org.springframework.data.cassandra.core.mapping.PrimaryKeyType.PARTITIONED
    )
    val indicator: String = "",
    
    @org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn(
        name = "timestamp", 
        ordinal = 1,
        type = org.springframework.data.cassandra.core.mapping.PrimaryKeyType.CLUSTERED
    )
    val timestamp: Long = 0L
)