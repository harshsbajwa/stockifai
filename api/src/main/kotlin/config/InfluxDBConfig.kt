package com.harshsbajwa.stockifai.api.config

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.InfluxDBClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class InfluxDBConfig {

    @Value("\${influxdb.url:http://localhost:8086}")
    private lateinit var influxUrl: String

    @Value("\${influxdb.token:}")
    private lateinit var influxToken: String

    @Value("\${influxdb.org:stockifai}")
    private lateinit var influxOrg: String

    @Value("\${influxdb.bucket:stockdata}")
    private lateinit var influxBucket: String

    @Bean
    fun influxDBClient(): InfluxDBClient {
        return InfluxDBClientFactory.create(influxUrl, influxToken.toCharArray(), influxOrg, influxBucket)
    }

    @Bean
    fun influxProperties(): InfluxProperties {
        return InfluxProperties(influxUrl, influxBucket, influxOrg)
    }
}

data class InfluxProperties(
    val url: String,
    val bucket: String,
    val org: String
)
