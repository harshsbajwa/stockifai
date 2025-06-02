package com.harshsbajwa.stockifai.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories
import com.datastax.oss.driver.api.core.CqlSessionBuilder
import java.nio.file.Paths
 
class CassandraConfig : AbstractCassandraConfiguration() {

    @Value("\${spring.data.cassandra.keyspace-name:finrisk_reference_data}")
    private lateinit var keyspaceNameValue: String
     
    @Value("\${astra.db.secure-connect-bundle-path}")
    private lateinit var cloudSecureConnectBundlePath: String
 
    @Value("\${astra.db.client-id}")
    private lateinit var usernameValue: String

    @Value("\${astra.db.client-secret}")
    private lateinit var passwordValue: String
 
    @Value("\${astra.db.local-datacenter:#{null}}")
    private var localDatacenter: String? = null
 
    override fun getKeyspaceName(): String = keyspaceNameValue
    override fun getSchemaAction(): SchemaAction = SchemaAction.NONE
 
    @Bean
    fun sessionBuilderConfigurer(): SessionBuilderConfigurer {
        return SessionBuilderConfigurer { sessionBuilder ->
            sessionBuilder
                .withCloudSecureConnectBundle(Paths.get(cloudSecureConnectBundlePath))
                .withAuthCredentials(usernameValue, passwordValue)
                .also { builder ->
                    localDatacenter?.let { dc ->
                        if (dc.isNotBlank()) builder.withLocalDatacenter(dc)
                    }
                }
        }
    }
}