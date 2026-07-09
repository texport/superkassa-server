package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageConnector
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для MySQL.
 */
class MysqlConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.MYSQL

    override fun connect(config: StorageConfig): Connection {
        return JdbcSupport.openConnection(config, "com.mysql.cj.jdbc.Driver")
    }
}
