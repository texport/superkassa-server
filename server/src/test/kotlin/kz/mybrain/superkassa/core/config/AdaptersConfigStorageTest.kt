package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.jvm.settings.impl.FileCoreSettingsRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Проводка рабочего места через конфигурацию узла: настройки читаются
 * из него, а хранилищу отдаётся уже привязанный к нему адрес базы.
 */
class AdaptersConfigStorageTest {

    private val config = AdaptersConfig()

    private fun settings(jdbcUrl: String) = CoreSettings(
        mode = CoreMode.DESKTOP,
        storage = StorageSettings(engine = "SQLITE", jdbcUrl = jdbcUrl),
        allowChanges = true
    )

    @Test
    fun `settings repository lives in the workspace`() {
        val dir = Files.createTempDirectory("workspace")
        val home = config.nodeHome(dir.toString())

        val repository = config.settingsRepository(home, "", null, null)
        repository.loadOrCreate(settings("jdbc:sqlite:data/core.db"))

        assertTrue(repository is FileCoreSettingsRepository)
        assertTrue(Files.isRegularFile(dir.resolve("config/core-settings.json")))
    }

    /** Запуск из любого каталога открывает базу рабочего места. */
    @Test
    fun `storage config carries the workspace-bound url`() {
        val dir = Files.createTempDirectory("workspace")
        Files.createDirectories(dir.resolve("data"))
        Files.write(dir.resolve("data/core.db"), byteArrayOf(1))
        val home = config.nodeHome(dir.toString())

        val storage = config.storageConfig(settings("jdbc:sqlite:data/core.db?busy_timeout=30000"), home)

        assertEquals("jdbc:sqlite:${dir.toAbsolutePath().normalize().resolve("data/core.db")}?busy_timeout=30000", storage.jdbcUrl)
    }

    @Test
    fun `missing database in an existing workspace stops the start`() {
        val dir = Files.createTempDirectory("workspace")
        Files.createDirectories(dir.resolve("config"))
        Files.writeString(dir.resolve("config/core-settings.json"), "{}")
        val home = config.nodeHome(dir.toString())

        assertFailsWith<IllegalStateException> {
            config.storageConfig(settings("jdbc:sqlite:data/core.db"), home)
        }
    }
}
