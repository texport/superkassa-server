package io.github.texport.superkassa.jvm.storage.impl.data.bootstrap

import io.github.texport.superkassa.jvm.storage.impl.application.bootstrap.StorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageManager
import io.github.texport.superkassa.jvm.storage.impl.application.migration.MigrationRunner
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.DefaultStorageConnectorRegistry
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.JdbcStorageSessionFactory
import io.github.texport.superkassa.jvm.storage.impl.data.migration.JdbcMigrationRunner

/**
 * Bootstrap по умолчанию: миграции + JDBC сессии.
 */
class DefaultStorageBootstrap(
    private val registry: DefaultStorageConnectorRegistry = DefaultStorageConnectorRegistry(),
    private val migrationRunner: MigrationRunner = JdbcMigrationRunner(registry),
    private val storageManager: StorageManager = StorageManager(
        registry,
        JdbcStorageSessionFactory()
    )
) : StorageBootstrap(storageManager, migrationRunner)
