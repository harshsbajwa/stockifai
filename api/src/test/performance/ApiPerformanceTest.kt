package com.harshsbajwa.stockifai.api.performance

import com.harshsbajwa.stockifai.application.Application
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class ApiPerformanceTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    companion object {
        @Container
        val kafkaContainer = ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.1")
        )

        @Container
        val cassandraContainer = CassandraContainer(
            DockerImageName.parse("cassandra:5.0.4")
        )

        @Container
        val influxDBContainer = GenericContainer(
            DockerImageName.parse("influxdb:2.7")
        ).withExposedPorts(8086)
            .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
            .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "testuser")
            .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "testpassword")
            .withEnv("DOCKER_INFLUXDB_INIT_ORG", "testorg")
            .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "testbucket")
            .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "testtoken123!")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers)
            registry.add("spring.data.cassandra.contact-points") { cassandraContainer.host }
            registry.add("spring.data.cassandra.port") { cassandraContainer.getMappedPort(9042) }
            registry.add("spring.data.cassandra.local-datacenter") { cassandraContainer.localDatacenter }
            registry.add("spring.data.cassandra.keyspace-name") { "test_keyspace" }
            registry.add("influxdb.url") {
                "http://${influxDBContainer.host}:${influxDBContainer.getMappedPort(8086)}"
            }
            registry.add("influxdb.token") { "testtoken123!" }
            registry.add("influxdb.org") { "testorg" }
            registry.add("influxdb.bucket") { "testbucket" }
        }
    }

    @Test
    fun `health endpoint should respond quickly under load`() {
        val concurrency = 50
        val requestsPerThread = 10

        val futures = (1..concurrency).map {
            CompletableFuture.supplyAsync {
                val times = mutableListOf<Long>()
                repeat(requestsPerThread) {
                    val time = measureTimeMillis {
                        restTemplate.getForEntity(
                            "http://localhost:$port/api/v1/health",
                            String::class.java
                        )
                    }
                    times.add(time)
                }
                times
            }
        }

        val allTimes = futures.flatMap { it.get(30, TimeUnit.SECONDS) }
        val averageTime = allTimes.average()
        val maxTime = allTimes.maxOrNull() ?: 0L

        println("Performance Results:")
        println("Total requests: ${allTimes.size}")
        println("Average response time: ${averageTime}ms")
        println("Max response time: ${maxTime}ms")
        println("95th percentile: ${allTimes.sorted()[(allTimes.size * 0.95).toInt()]}ms")

        // Assert reasonable performance
        assertTrue(averageTime < 500, "Average response time should be under 500ms")
        assertTrue(maxTime < 2000, "Max response time should be under 2000ms")
    }

    @Test
    fun `market overview endpoint should handle concurrent requests`() {
        val concurrency = 20
        val url = "http://localhost:$port/api/v1/market/overview"

        val futures = (1..concurrency).map {
            CompletableFuture.supplyAsync {
                measureTimeMillis {
                    val response = restTemplate.getForEntity(url, String::class.java)
                    response.statusCode.is2xxSuccessful
                }
            }
        }

        val times = futures.map { it.get(30, TimeUnit.SECONDS) }
        val averageTime = times.average()

        println("Market Overview Performance:")
        println("Concurrent requests: $concurrency")
        println("Average response time: ${averageTime}ms")

        assertTrue(averageTime < 1000, "Average response time should be under 1000ms")
    }
}