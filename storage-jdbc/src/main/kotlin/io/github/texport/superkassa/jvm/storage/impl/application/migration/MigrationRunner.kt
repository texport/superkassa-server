package io.github.texport.superkassa.jvm.storage.impl.application.migration

import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig

/**
 * Выполняет миграции схемы.
 */
interface MigrationRunner {
    fun migrate(config: StorageConfig)
}
