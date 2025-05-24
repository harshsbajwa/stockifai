package com.yourusername.stockmonitor.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class APIContextTest {
    companion object {
        @Container
        val kafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.1"))

        @Container
        val cassandraContainer =
            CassandraContainer(DockerImageName.parse("cassandra:5.0.4")).withExposedPorts(9042)

        // For InfluxDB 2.x
        @Container
        val influxDBContainer =
            GenericContainer(DockerImageName.parse("influxdb:2.7"))
                .withExposedPorts(8086)
                .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "testuser")
                .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "testpassword")
                .withEnv("DOCKER_INFLUXDB_INIT_ORG", "testorg")
                .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "testbucket")
                .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "testtoken123!")

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
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

            // Override properties from docker-compose.yml if they are used by the app directly
            registry.add("INFLUXDB_URL") {
                "http://${influxDBContainer.host}:${influxDBContainer.getMappedPort(8086)}"
            }
            registry.add("INFLUXDB_TOKEN") { "testtoken123!" }
            registry.add("INFLUXDB_ORG") { "testorg" }
            registry.add("INFLUXDB_BUCKET") { "testbucket" }
        }
    }

    @Test
    fun contextLoads() {
        // Verify that the Spring application context can start successfully
        // and connect to the containerized dependencies.
        println("API Application context loaded successfully with Testcontainers.")
        println("Kafka running at: " + kafkaContainer.bootstrapServers)
        println(
            "Cassandra running at: " +
                cassandraContainer.host +
                ":" +
                cassandraContainer.getMappedPort(9042),
        )
        println(
            "InfluxDB running at: http://" +
                influxDBContainer.host +
                ":" +
                influxDBContainer.getMappedPort(8086),
        )
    }
}
