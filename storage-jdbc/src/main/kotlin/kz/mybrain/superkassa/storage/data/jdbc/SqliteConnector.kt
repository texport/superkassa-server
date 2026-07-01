package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.application.connector.StorageConnector
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kz.mybrain.superkassa.storage.domain.config.StorageEngine
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
