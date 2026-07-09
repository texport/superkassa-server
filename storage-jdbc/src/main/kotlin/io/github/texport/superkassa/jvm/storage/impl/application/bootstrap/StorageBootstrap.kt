package io.github.texport.superkassa.jvm.storage.impl.application.bootstrap

import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageManager
import io.github.texport.superkassa.jvm.storage.impl.application.migration.MigrationRunner
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig

/**
 * Упрощенный фасад для миграций и открытия сессии.
 */
open class StorageBootstrap(
    private val manager: StorageManager,
    private val migrationRunner: MigrationRunner
) {
    /**
     * Применяет миграции схемы.
     */
    fun migrate(config: StorageConfig) {
        migrationRunner.migrate(config)
    }

    /**
     * Открывает сессию для работы с репозиториями.
     */
    fun openSession(config: StorageConfig): StorageSession {
        return manager.openSession(config)
    }
}
