package com.harshsbajwa.stockifai.api.model

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.util.UUID


@Table("market_news")
data class NewsEntity(
    @PrimaryKey
    val primaryKey: NewsPrimaryKey,
    
    @Column("headline")
    val headline: String,
    
    @Column("summary")
    val summary: String,
    
    @Column("sentiment")
    val sentiment: String,
    
    @Column("source")
    val source: String?,
    
    @Column("related_symbol")
    val relatedSymbol: String?
) {
    constructor() : this(NewsPrimaryKey(), "", "", "", null, null)
}

@PrimaryKeyClass
data class NewsPrimaryKey(
    @PrimaryKeyColumn(name = "date_bucket", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    val dateBucket: String = "",
    
    @PrimaryKeyColumn(name = "timestamp", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    val timestamp: Long = 0L,
    
    @PrimaryKeyColumn(name = "id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    val id: UUID = UUID.randomUUID()
)