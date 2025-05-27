package com.harshsbajwa.stockifai.api.config

import com.influxdb.v3.client.InfluxDBClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InfluxDBConfig {

    @Value("\${influxdb.url:http://localhost:8086}")
    private lateinit var influxUrl: String

    @Value("\${influxdb.token:}")
    private lateinit var influxToken: String

    @Value("\${influxdb.database:stockifai}")
    private lateinit var influxDatabase: String

    @Bean
    fun influxDBClient(): InfluxDBClient {
        return InfluxDBClient.getInstance(influxUrl, influxToken, influxDatabase)
    }

    @Bean
    fun influxProperties(): InfluxProperties {
        return InfluxProperties(influxUrl, influxDatabase)
    }
}

data class InfluxProperties(
    val url: String,
    val database: String
)