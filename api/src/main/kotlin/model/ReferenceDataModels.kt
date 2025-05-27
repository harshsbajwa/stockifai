package com.harshsbajwa.stockifai.api.model

import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import java.time.Instant

@Table("instrument_metadata")
data class InstrumentMetadata(
    @PrimaryKey val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val currency: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val description: String? = null,
    @Column("last_updated")
    val lastUpdated: Instant? = null
) {
    constructor() : this("")
}

@Table("economic_indicator_metadata")
data class EconomicIndicatorMetadata(
    @PrimaryKey @Column("series_id") val seriesId: String,
    val title: String? = null,
    val frequency: String? = null,
    val units: String? = null,
    val notes: String? = null,
    val source: String? = null,
    @Column("last_updated")
    val lastUpdated: Instant? = null
) {
    constructor() : this("")
}