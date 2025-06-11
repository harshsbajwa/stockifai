package com.harshsbajwa.stockifai.api.service

import com.harshsbajwa.stockifai.api.dto.NewsItem
import com.harshsbajwa.stockifai.api.model.NewsEntity
import com.harshsbajwa.stockifai.api.repository.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class NewsService(
    private val newsRepository: NewsRepository,
) {
    private val logger = LoggerFactory.getLogger(NewsService::class.java)
    private val utcZone = ZoneId.of("UTC")

    fun getRecentNews(
        hours: Long = 24,
        limit: Int = 50,
        searchDepthInDays: Int = 7,
    ): List<NewsItem> {
        logger.info("Fetching recent news for the last {} hours with a limit of {} and search depth of {} days.", hours, limit, searchDepthInDays)
        return try {
            val news = mutableListOf<NewsEntity>()
            val endDate = LocalDate.now(utcZone)

            for (i in 0 until searchDepthInDays) {
                val currentDate = endDate.minusDays(i.toLong())
                val dateBucket = currentDate.toString()
                val pageRequest = PageRequest.of(0, limit)

                logger.debug("Querying Cassandra for dateBucket: {}", dateBucket)
                val dayNews = newsRepository.findByDateBucketOrderByTimestampDesc(dateBucket, pageRequest)

                if (dayNews.hasContent()) {
                    logger.info("Found {} news items for dateBucket: {}", dayNews.content.size, dateBucket)
                    news.addAll(dayNews.content)
                }

                if (news.size >= limit) {
                    logger.info("Collected {} items (>= limit of {}), stopping search.", news.size, limit)
                    break
                }
            }
            logger.info("Total news items fetched from Cassandra across all buckets: {}", news.size)

            val cutoffTime = Instant.now().minus(hours, ChronoUnit.HOURS)
            logger.info("Filtering collected news to only include items after {}.", cutoffTime)

            val filteredNews = news
                .filter { Instant.ofEpochMilli(it.primaryKey.timestamp).isAfter(cutoffTime) }
                .sortedByDescending { it.primaryKey.timestamp }
                .take(limit)
                .map { convertToNewsItem(it) }

            logger.info("Returning {} news items after final filtering and limiting.", filteredNews.size)
            return filteredNews
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

