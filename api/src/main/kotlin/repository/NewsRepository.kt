package com.harshsbajwa.stockifai.api.repository

import com.harshsbajwa.stockifai.api.model.NewsEntity
import com.harshsbajwa.stockifai.api.model.NewsPrimaryKey
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository


@Repository
interface NewsRepository : CassandraRepository<NewsEntity, NewsPrimaryKey> {

    @Query("SELECT * FROM news_by_time WHERE date_bucket = ?0 ORDER BY timestamp DESC, id ASC")
    fun findByDateBucketOrderByTimestampDesc(dateBucket: String, pageable: Pageable): Slice<NewsEntity>
}