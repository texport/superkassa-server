package io.github.texport.superkassa.jvm.storage.impl.application.migration

import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine

/**
 * Описание SQL-миграции.
 */
data class MigrationScript(
    val version: String,
    val resourcePath: String,
    val checksum: String
)

/**
 * Каталог миграций по движку БД.
 */
interface MigrationCatalog {
    fun scriptsFor(engine: StorageEngine): List<MigrationScript>
}
