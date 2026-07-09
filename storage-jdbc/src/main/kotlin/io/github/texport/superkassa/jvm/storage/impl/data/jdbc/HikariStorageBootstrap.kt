package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.texport.superkassa.jvm.storage.impl.application.migration.MigrationRunner
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.data.migration.JdbcMigrationRunner
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import org.slf4j.LoggerFactory

/**
 * Bootstrap с HikariCP для режима PowerMove.
 */
class HikariStorageBootstrap(
    private val storageConfig: StorageConfig,
    private val hikariConfig: HikariConfig,
    private val migrationRunner: MigrationRunner = JdbcMigrationRunner(DefaultStorageConnectorRegistry())
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(HikariStorageBootstrap::class.java)
    private val dataSource: HikariDataSource

    init {
        logger.info("Initializing HikariCP pool.")
        dataSource = HikariDataSource(hikariConfig)
    }

    /**
     * Применяет миграции схемы.
     */
    fun migrate() {
        logger.info("Running migrations with HikariCP bootstrap.")
        migrationRunner.migrate(storageConfig)
    }

    /**
     * Открывает сессию через пул соединений.
     */
    fun openSession(): StorageSession {
        logger.info("Opening storage session from HikariCP pool.")
        return JdbcStorageSession(dataSource.connection)
    }

    override fun close() {
        logger.info("Closing HikariCP pool.")
        dataSource.close()
    }
}
