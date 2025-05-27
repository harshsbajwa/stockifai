package com.harshsbajwa.stockifai.stream

import org.springframework.context.annotation.Configuration

@Configuration
class KafkaTopicConfig {
    
    fun kafkaTopics(): Map<String, TopicConfiguration> {
        return mapOf(
            "stock_prices" to TopicConfiguration(
                partitions = 6,
                replicationFactor = 1,
                retentionMs = 86400000L, // 24 hours
                description = "Real-time stock price data"
            ),
            "economic_indicators" to TopicConfiguration(
                partitions = 3,
                replicationFactor = 1,
                retentionMs = 604800000L, // 7 days
                description = "Economic indicators from FRED"
            ),
            "intraday_data" to TopicConfiguration(
                partitions = 3,
                replicationFactor = 1,
                retentionMs = 259200000L, // 3 days
                description = "Intraday candlestick data"
            ),
            "volatility_indicators" to TopicConfiguration(
                partitions = 2,
                replicationFactor = 1,
                retentionMs = 86400000L, // 24 hours
                description = "Market volatility indicators"
            )
        )
    }
}

data class TopicConfiguration(
    val partitions: Int,
    val replicationFactor: Short,
    val retentionMs: Long,
    val description: String
)