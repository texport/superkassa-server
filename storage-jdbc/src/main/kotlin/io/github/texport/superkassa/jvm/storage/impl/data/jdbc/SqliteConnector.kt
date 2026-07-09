package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageConnector
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для SQLite.
 */
class SqliteConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.SQLITE

    override fun connect(config: StorageConfig): Connection {
        if (config.jdbcUrl.lowercase().startsWith("jdbc:sqlite:")) {
            val pathPart = config.jdbcUrl.substring("jdbc:sqlite:".length).substringBefore('?')
            if (pathPart != ":memory:" && pathPart.isNotEmpty()) {
                val file = java.io.File(pathPart)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
            }
        }
        return JdbcSupport.openConnection(config, "org.sqlite.JDBC")
    }
}
