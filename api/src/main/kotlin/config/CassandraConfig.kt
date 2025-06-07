package com.harshsbajwa.stockifai.api.config

import com.datastax.oss.driver.api.core.CqlSession
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.CassandraTemplate
import org.springframework.data.cassandra.core.convert.CassandraConverter
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories
import java.net.InetSocketAddress
import java.nio.file.Paths

@Configuration
@EnableCassandraRepositories(basePackages = ["com.harshsbajwa.stockifai.api.repository"])
class CassandraConfig {

    private val logger = LoggerFactory.getLogger(CassandraConfig::class.java)

    @Bean
    fun cassandraMappingContext(): CassandraMappingContext {
        return CassandraMappingContext()
    }

    @Bean
    fun cassandraConverter(cqlSession: CqlSession, mappingContext: CassandraMappingContext): CassandraConverter {
        return MappingCassandraConverter(mappingContext)
    }

    @Bean
    fun cassandraTemplate(cqlSession: CqlSession, converter: CassandraConverter): CassandraTemplate {
        return CassandraTemplate(cqlSession, converter)
    }

    @Bean("cqlSession")
    @Profile("k3s-production")
    fun astraCqlSession(
        @Value("\${astra.db.keyspace-name}") keyspaceNameValue: String,
        @Value("\${astra.db.secure-connect-bundle-path}") cloudSecureConnectBundlePath: String,
        @Value("\${astra.db.application-token}") applicationToken: String
    ): CqlSession {
        logger.info("Profile 'k3s-production' active. Building CqlSession for AstraDB.")
        logger.info("AstraDB details: keyspace={}, bundlePath={}", keyspaceNameValue, cloudSecureConnectBundlePath)
        
        try {
            return CqlSession.builder()
                .withCloudSecureConnectBundle(Paths.get(cloudSecureConnectBundlePath))
                .withAuthCredentials("token", applicationToken)
                .withKeyspace(keyspaceNameValue)
                .build()
                .also { logger.info("Successfully built CqlSession for AstraDB.") }
        } catch (e: Exception) {
            logger.error("Failed to build CqlSession for AstraDB: ${e.message}", e)
            throw e
        }
    }

    @Bean("cqlSession")
    @Profile("local-docker")
    fun localDockerCqlSession(
        @Value("\${spring.data.cassandra.contact-points:cassandra}") contactPointsValue: String,
        @Value("\${spring.data.cassandra.port:9042}") portValue: Int,
        @Value("\${spring.data.cassandra.local-datacenter:datacenter1}") localDatacenterValue: String,
        @Value("\${spring.data.cassandra.keyspace-name:stock_keyspace}") keyspaceNameValue: String
    ): CqlSession {
        logger.info("Profile 'local-docker' active. Building CqlSession for local Cassandra.")
        logger.info(
            "Local Cassandra details: points={}, port={}, dc={}, keyspace={}",
            contactPointsValue, portValue, localDatacenterValue, keyspaceNameValue
        )

        val contactPointAddresses = contactPointsValue.split(',').map { InetSocketAddress(it.trim(), portValue) }

        try {
            return CqlSession.builder()
                .addContactPoints(contactPointAddresses)
                .withLocalDatacenter(localDatacenterValue)
                .withKeyspace(keyspaceNameValue)
                .build()
                .also { logger.info("Successfully built CqlSession for local Docker to {}:{}", contactPointsValue, portValue) }
        } catch (e: Exception) {
            logger.error("Failed to build CqlSession for local-docker: ${e.message}", e)
            throw e
        }
    }
}

