package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.NewsItem
import com.harshsbajwa.stockifai.api.model.NewsEntity
import com.harshsbajwa.stockifai.api.repository.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class NewsService(
    private val newsRepository: NewsRepository,
) {
    private val logger = LoggerFactory.getLogger(NewsService::class.java)

    fun getRecentNews(
        hours: Long = 24,
        limit: Int = 50,
    ): List<NewsItem> {
        return try {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(hours / 24 + 1)

            val news = mutableListOf<NewsEntity>()
            var currentDate = startDate

            while (!currentDate.isAfter(endDate)) {
                val dateBucket = currentDate.toString()
                val pageRequest = PageRequest.of(0, limit)
                val dayNews = newsRepository.findByDateBucketOrderByTimestampDesc(dateBucket, pageRequest)
                news.addAll(dayNews.content)
                currentDate = currentDate.plusDays(1)

                if (news.size >= limit) break
            }

            val cutoffTime = Instant.now().minus(hours, ChronoUnit.HOURS)
            return news
                .filter { Instant.ofEpochMilli(it.primaryKey.timestamp).isAfter(cutoffTime) }
                .take(limit)
                .map { convertToNewsItem(it) }
        } catch (e: Exception) {
            logger.error("Error fetching recent news", e)
            emptyList()
        }
    }

    fun getNewsBySentiment(
        sentiment: String,
        hours: Long = 24,
        limit: Int = 20,
    ): List<NewsItem> =
        getRecentNews(hours, limit * 3)
            .filter { it.sentiment.equals(sentiment, ignoreCase = true) }
            .take(limit)

    fun getNewsForSymbol(
        symbol: String,
        hours: Long = 24,
        limit: Int = 20,
    ): List<NewsItem> =
        getRecentNews(hours, limit * 5)
            .filter { it.relatedSymbol?.equals(symbol, ignoreCase = true) == true }
            .take(limit)

    private fun convertToNewsItem(entity: NewsEntity): NewsItem =
        NewsItem(
            id = entity.primaryKey.id.toString(),
            headline = entity.headline,
            summary = entity.summary,
            sentiment = entity.sentiment,
            timestamp = Instant.ofEpochMilli(entity.primaryKey.timestamp),
            source = entity.source,
            relatedSymbol = entity.relatedSymbol,
        )
}
