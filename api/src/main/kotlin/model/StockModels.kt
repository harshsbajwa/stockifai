package com.harshsbajwa.stockifai.api.model

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.util.UUID


@Table("stock_summaries")
data class StockSummary(
    @PrimaryKey
    val symbol: String,

    @Column("last_timestamp")
    val lastTimestamp: Long?,

    @Column("current_price")
    val currentPrice: Double,

    @Column("latest_volume")
    val latestVolume: Long?,

    @Column("latest_volatility")
    val latestVolatility: Double?,

    @Column("latest_risk_score")
    val latestRiskScore: Double?,

    @Column("latest_trend")
    val latestTrend: String?,

    @Column("calculation_date")
    val calculationDate: String?,

    @Column("price_change_today")
    val priceChangeToday: Double?,

    @Column("price_change_percent_today")
    val priceChangePercentToday: Double?
) {
    constructor() : this(
        symbol = "",
        lastTimestamp = 0L,
        currentPrice = 0.0,
        latestVolume = 0L,
        latestVolatility = 0.0,
        latestRiskScore = 0.0,
        latestTrend = "NEUTRAL",
        calculationDate = null,
        priceChangeToday = 0.0,
        priceChangePercentToday = 0.0
    )
}

@Table("economic_indicator_summaries")
data class EconomicIndicatorSummary(
    @PrimaryKey
    val indicator: String,

    @Column("last_timestamp")
    val lastTimestamp: Long?,

    @Column("latest_value")
    val latestValue: Double,

    @Column("observation_date")
    val observationDate: String?
) {
    constructor() : this(
        indicator = "",
        lastTimestamp = 0L,
        latestValue = 0.0,
        observationDate = null
    )
}
