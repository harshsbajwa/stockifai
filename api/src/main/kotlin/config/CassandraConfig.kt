package com.harshsbajwa.stockifai.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.core.CassandraOperations
import org.springframework.data.cassandra.core.CassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories

@Configuration
@EnableCassandraRepositories(basePackages = ["com.harshsbajwa.stockifai.api.repository"])
class CassandraConfig : AbstractCassandraConfiguration() {

    @Value("\${spring.data.cassandra.keyspace-name:stock_keyspace}")
    private lateinit var keyspaceName: String

    @Value("\${spring.data.cassandra.contact-points:127.0.0.1}")
    private lateinit var contactPoints: String

    @Value("\${spring.data.cassandra.port:9042}")
    private var port: Int = 9042

    @Value("\${spring.data.cassandra.local-datacenter:datacenter1}")
    private lateinit var localDatacenter: String

    override fun getKeyspaceName(): String = keyspaceName

    override fun getContactPoints(): String = contactPoints

    override fun getPort(): Int = port

    override fun getLocalDataCenter(): String = localDatacenter

    override fun getSchemaAction(): SchemaAction = SchemaAction.CREATE_IF_NOT_EXISTS

    @Bean
    fun cassandraOperations(): CassandraOperations {
        return CassandraTemplate(sessionFactory().`object`)
    }
}