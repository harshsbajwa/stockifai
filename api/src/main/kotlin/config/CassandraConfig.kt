package com.harshsbajwa.stockifai.api.config

import com.datastax.oss.driver.api.core.CqlSession
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
// import org.springframework.boot.autoconfigure.cassandra.CassandraProperties`
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import java.net.InetSocketAddress
import java.nio.file.Paths

@Configuration
class CassandraConfig {
    companion object {
        private val logger = LoggerFactory.getLogger(CassandraConfig::class.java)
    }

    @Configuration
    @Profile("!local-docker")
    @ConditionalOnProperty(
        prefix = "astra.db",
        name = ["secure-connect-bundle-path", "client-id", "client-secret"],
    )
    class AstraCassandraConfiguration : AbstractCassandraConfiguration() {
        @Value("\${astra.db.keyspace-name}")
        private lateinit var keyspaceNameValue: String

        @Value("\${astra.db.secure-connect-bundle-path}")
        private lateinit var cloudSecureConnectBundlePath: String

        @Value("\${astra.db.client-id}")
        private lateinit var clientIdValue: String

        @Value("\${astra.db.client-secret}")
        private lateinit var clientSecretValue: String

        @Value("\${astra.db.local-datacenter:#{null}}")
        private var localDatacenter: String? = null

        init {
            logger.info("AstraDB Cassandra Configuration activated.")
            logger.info(
                "AstraDB details: keyspace={}, bundlePath={}, localDatacenter={}",
                keyspaceNameValue,
                cloudSecureConnectBundlePath,
                localDatacenter ?: "not set",
            )
        }

        override fun getKeyspaceName(): String = keyspaceNameValue

        override fun getSchemaAction(): SchemaAction = SchemaAction.NONE

        @Bean
        @Primary
        fun cassandraSessionBuilderConfigurer(): SessionBuilderConfigurer {
            logger.info(
                "Configuring SessionBuilder for AstraDB with SCB: {} and credentials.",
                cloudSecureConnectBundlePath,
            )
            return SessionBuilderConfigurer { sessionBuilder ->
                sessionBuilder
                    .withCloudSecureConnectBundle(Paths.get(cloudSecureConnectBundlePath))
                    .withAuthCredentials(clientIdValue, clientSecretValue)
                    .also { builder ->
                        localDatacenter?.let { dc ->
                            if (dc.isNotBlank()) {
                                logger.info("Setting local datacenter for AstraDB: {}", dc)
                                builder.withLocalDatacenter(dc)
                            }
                        }
                    }
            }
        }
    }

    @Configuration
    @Profile("local-docker")
    class LocalDockerCassandraConfiguration {
        @Value("\${spring.data.cassandra.contact-points:cassandra}")
        private lateinit var contactPointsValue: String

        @Value("\${spring.data.cassandra.port:9042}")
        private var portValue: Int = 9042

        @Value("\${spring.data.cassandra.local-datacenter:datacenter1}")
        private lateinit var localDatacenterValue: String

        @Value("\${spring.data.cassandra.keyspace-name:stock_keyspace}")
        private lateinit var keyspaceNameValue: String

        init {
            logger.info("Local Docker Cassandra Configuration activated.")
        }

        @Bean
        @Primary
        fun localDockerCqlSession(): CqlSession {
            logger.info(
                "Building CqlSession for local-docker: contact-points={}, port={}, local-datacenter={}, keyspace={}",
                contactPointsValue,
                portValue,
                localDatacenterValue,
                keyspaceNameValue,
            )

            val contactPointAddresses =
                contactPointsValue
                    .split(',')
                    .map { InetSocketAddress(it.trim(), portValue) }

            if (contactPointAddresses.isEmpty()) {
                val errorMessage = "Cassandra contact points are not configured correctly for local-docker profile. Value was: '$contactPointsValue'"
                logger.error(errorMessage)
                throw IllegalStateException(errorMessage)
            }
            logger.info("Resolved InetSocketAddresses for local-docker: {}", contactPointAddresses)

            try {
                return CqlSession
                    .builder()
                    .addContactPoints(contactPointAddresses)
                    .withLocalDatacenter(localDatacenterValue)
                    .withKeyspace(keyspaceNameValue)
                    .build()
                    .also {
                        logger.info(
                            "Successfully built CqlSession for local-docker to {}:{}",
                            contactPointsValue,
                            portValue,
                        )
                    }
            } catch (e: Exception) {
                logger.error("Failed to build CqlSession for local-docker: ${e.message}", e)
                throw e
            }
        }
    }
}
