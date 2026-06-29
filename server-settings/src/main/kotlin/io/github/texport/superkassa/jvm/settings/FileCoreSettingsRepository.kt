package io.github.texport.superkassa.jvm.settings

import kotlinx.serialization.json.Json
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.port.CoreSettingsRepositoryPort
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Файловое хранилище конфигурации ядра (используется для desktop-режима).
 */
class FileCoreSettingsRepository(
    private val path: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) : CoreSettingsRepositoryPort {

    private val lock = Any()

    override fun load(): CoreSettings? = synchronized(lock) {
        if (!Files.exists(path)) return null
        val text = Files.readString(path)
        val settings = json.decodeFromString(CoreSettings.serializer(), text)
        CoreSettingsValidator.validateSettings(settings)
        return settings
    }

    override fun save(settings: CoreSettings): Boolean = synchronized(lock) {
        CoreSettingsValidator.validateSettings(settings)
        val text = json.encodeToString(settings)
        val absolutePath = path.toAbsolutePath()
        val parentDir = absolutePath.parent
        if (parentDir != null) {
            Files.createDirectories(parentDir)
        }

        // Безопасная запись через временный файл и атомарную замену
        val tempFile = Files.createTempFile(parentDir, "core-settings-", ".tmp")
        try {
            Files.writeString(tempFile, text)
            Files.move(
                tempFile,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            Files.deleteIfExists(tempFile)
            throw e
        }
        return true
    }

    override fun loadOrCreate(defaults: CoreSettings): CoreSettings {
        return load() ?: defaults.also { save(it) }
    }
}
