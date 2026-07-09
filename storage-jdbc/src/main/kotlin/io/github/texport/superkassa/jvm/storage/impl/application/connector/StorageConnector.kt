package io.github.texport.superkassa.jvm.storage.impl.application.connector

import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine
import java.sql.Connection

/**
 * Контракт подключения к конкретному движку БД.
 */
interface StorageConnector {
    val engine: StorageEngine
    fun connect(config: StorageConfig): Connection
}
