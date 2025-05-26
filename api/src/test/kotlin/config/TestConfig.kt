package com.harshsbajwa.stockifai.api.config

import com.datastax.oss.driver.api.core.CqlSession
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.QueryApi
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@TestConfiguration
@Profile("test")
class TestConfig {

    @Bean
    @Primary
    fun mockInfluxDBClient(): InfluxDBClient {
        return Mockito.mock(InfluxDBClient::class.java)
    }

    @Bean
    @Primary
    fun mockQueryApi(): QueryApi {
        return Mockito.mock(QueryApi::class.java)
    }

    @Bean
    @Primary
    fun mockCqlSession(): CqlSession {
        return Mockito.mock(CqlSession::class.java)
    }
}