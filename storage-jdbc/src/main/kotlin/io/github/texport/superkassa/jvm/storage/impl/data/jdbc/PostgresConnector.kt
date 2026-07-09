package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageConnector
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для PostgreSQL.
 */
class PostgresConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.POSTGRES

    override fun connect(config: StorageConfig): Connection {
        return JdbcSupport.openConnection(config, "org.postgresql.Driver")
    }
}
