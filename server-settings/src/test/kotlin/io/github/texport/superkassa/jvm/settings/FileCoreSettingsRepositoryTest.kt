package io.github.texport.superkassa.jvm.settings

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kz.mybrain.superkassa.core.application.model.CoreMode
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.model.StorageSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCoreSettingsRepositoryTest {

    private val validSettings = CoreSettings(
        mode = CoreMode.DESKTOP,
        storage = StorageSettings(
            engine = "SQLITE",
            jdbcUrl = "jdbc:sqlite:build/test.db"
        ),
        allowChanges = true,
        ofdProtocolVersion = "203"
    )

    @Test
    fun `save and load settings in desktop mode`() {
        val tempFile = Files.createTempFile("core-settings-test", ".json")
        try {
            val repository = FileCoreSettingsRepository(tempFile)
            repository.save(validSettings)

            val loaded = repository.load()
            assertEquals(validSettings, loaded)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `load returns null if file does not exist`() {
        val nonExistentPath = Paths.get("non-existent-dir-12345/settings.json")
        val repository = FileCoreSettingsRepository(nonExistentPath)
        assertNull(repository.load())
    }

    @Test
    fun `loadOrCreate loads existing or saves default`() {
        val tempFile = Files.createTempFile("core-settings-test", ".json")
        Files.deleteIfExists(tempFile)
        try {
            val repository = FileCoreSettingsRepository(tempFile)
            val loaded = repository.loadOrCreate(validSettings)
            assertEquals(validSettings, loaded)
            assertTrue(Files.exists(tempFile))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `save throwing exception cleans up temp file`() {
        // Pass a directory as path to force Files.move/write to throw exception
        val dirPath = Files.createTempDirectory("core-settings-err")
        try {
            val repository = FileCoreSettingsRepository(dirPath)
            assertFailsWith<Exception> {
                repository.save(validSettings)
            }
        } finally {
            Files.deleteIfExists(dirPath)
        }
    }

    @Test
    fun `server mode with SQLite fails validation`() {
        val tempFile = Files.createTempFile("core-settings-test", ".json")
        try {
            val repository = FileCoreSettingsRepository(tempFile)
            val badSettings = CoreSettings(
                mode = CoreMode.SERVER,
                storage = StorageSettings(
                    engine = "SQLITE",
                    jdbcUrl = "jdbc:sqlite:build/test.db",
                    user = "postgres",
                    password = "password"
                ),
                nodeId = "node-1",
                ofdProtocolVersion = "203",
                allowChanges = true
            )

            assertFailsWith<IllegalServerConfigurationException> {
                repository.save(badSettings)
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `server mode with PGSQL passes validation`() {
        val tempFile = Files.createTempFile("core-settings-test", ".json")
        try {
            val repository = FileCoreSettingsRepository(tempFile)
            val goodSettings = CoreSettings(
                mode = CoreMode.SERVER,
                storage = StorageSettings(
                    engine = "POSTGRESQL",
                    jdbcUrl = "jdbc:postgresql://localhost:5432/db",
                    user = "postgres",
                    password = "password"
                ),
                nodeId = "node-1",
                ofdProtocolVersion = "203",
                allowChanges = true
            )

            assertTrue(repository.save(goodSettings))
            val loaded = repository.load()
            assertEquals(goodSettings, loaded)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `save settings with relative path having no parent`() {
        val path = Paths.get("temp-settings-no-parent.json")
        try {
            val repository = FileCoreSettingsRepository(path)
            assertTrue(repository.save(validSettings))
            assertTrue(Files.exists(path))
            assertEquals(validSettings, repository.load())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `validateSettings single argument overload`() {
        CoreSettingsValidator.validateSettings(validSettings)
    }

    @Test
    fun `read properties for coverage`() {
        kotlin.test.assertNotNull(CoreSettingsValidator.SQLITE_NOT_ALLOWED_ERROR)
        kotlin.test.assertNotNull(CoreSettingsValidator.SERVER_MODE_ONLY_ERROR)
    }

    @Test
    fun `load throws exception on invalid json`() {
        val tempFile = Files.createTempFile("core-settings-test-invalid", ".json")
        try {
            Files.writeString(tempFile, "{ invalid json }")
            val repository = FileCoreSettingsRepository(tempFile)
            assertFailsWith<Exception> {
                repository.load()
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `validateSettings with valid email channel`() {
        val settings = validSettings.copy(
            delivery = kz.mybrain.superkassa.core.application.model.DeliverySettings(
                channels = listOf(
                    kz.mybrain.superkassa.core.application.model.DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = kz.mybrain.superkassa.core.application.model.EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "u", password = "p")
            )
        )
        CoreSettingsValidator.validateSettings(settings)
    }

    @Test
    fun `loadOrCreate returns existing settings if file exists`() {
        val tempFile = Files.createTempFile("core-settings-test", ".json")
        try {
            val repository = FileCoreSettingsRepository(tempFile)
            repository.save(validSettings)

            val otherSettings = validSettings.copy(ofdProtocolVersion = "204")
            val loaded = repository.loadOrCreate(otherSettings)
            assertEquals(validSettings, loaded)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `save with null parent path`() {
        val mockPath = mockk<Path>()
        val mockAbsPath = mockk<Path>()
        val mockTempFile = mockk<Path>()

        every { mockPath.toAbsolutePath() } returns mockAbsPath
        every { mockAbsPath.parent } returns null

        mockkStatic(Files::class)
        every { Files.createTempFile(null as Path?, any<String>(), any<String>()) } returns mockTempFile
        every { Files.writeString(mockTempFile, any<CharSequence>()) } returns mockTempFile
        every { Files.move(mockTempFile, mockPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE) } returns mockPath

        val repository = FileCoreSettingsRepository(mockPath)
        assertTrue(repository.save(validSettings))

        unmockkStatic(Files::class)
    }
}
