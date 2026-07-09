package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageConnector
import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageConnectorRegistry
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine

/**
 * Реестр коннекторов по умолчанию.
 */
class DefaultStorageConnectorRegistry(
    connectors: List<StorageConnector> = listOf(
        SqliteConnector(),
        PostgresConnector(),
        MysqlConnector()
    )
) : StorageConnectorRegistry {
    private val connectorByEngine = connectors.associateBy { it.engine }

    override fun connectorFor(engine: StorageEngine): StorageConnector {
        return connectorByEngine[engine]
            ?: error("No connector registered for engine: $engine")
    }
}
