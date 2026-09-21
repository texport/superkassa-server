package kz.mybrain.superkassa.core.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Путь к базе не зависит от каталога запуска.
 *
 * Прежде относительный `data/core.db` из настроек считался от каталога
 * процесса: узел, поднятый из другого каталога, молча заводил там пустую
 * базу, и кассы владельца выглядели исчезнувшими. Теперь относительный
 * путь считается от рабочего места, а отсутствие базы там, где по
 * настройкам она должна быть, — отказ, а не пустой старт.
 */
class NodeHomeTest {

    private fun workspace(withSettings: Boolean = true, withDatabase: Boolean = true): Path {
        val dir = Files.createTempDirectory("workspace")
        if (withSettings) {
            Files.createDirectories(dir.resolve("config"))
            Files.writeString(
                dir.resolve(NodeHome.SETTINGS_FILE),
                """{"mode":"DESKTOP","storage":{"engine":"SQLITE","jdbcUrl":"$OLD_URL"}}"""
            )
        }
        if (withDatabase) {
            Files.createDirectories(dir.resolve("data"))
            Files.write(dir.resolve("data/core.db"), byteArrayOf(1))
        }
        return dir
    }

    /** Старый относительный путь из прежних установок читается и ведёт в рабочее место, а не в каталог процесса. */
    @Test
    fun `relative sqlite path resolves against the workspace`() {
        val dir = workspace()
        val home = NodeHome(dir)

        val resolved = home.resolveJdbcUrl(OLD_URL)

        assertEquals("jdbc:sqlite:${dir.toAbsolutePath().normalize().resolve("data/core.db")}?busy_timeout=30000", resolved)
        assertFalse(resolved.contains(System.getProperty("user.dir")), resolved)
    }

    @Test
    fun `absolute, memory and foreign urls stay as they are`() {
        val home = NodeHome(workspace())

        assertEquals("jdbc:sqlite:/var/lib/superkassa/core.db", home.resolveJdbcUrl("jdbc:sqlite:/var/lib/superkassa/core.db"))
        assertEquals("jdbc:sqlite::memory:", home.resolveJdbcUrl("jdbc:sqlite::memory:"))
        assertEquals("jdbc:postgresql://db/core", home.resolveJdbcUrl("jdbc:postgresql://db/core"))
    }

    @Test
    fun `workspace remembers whether settings were there at start`() {
        assertTrue(NodeHome(workspace()).settingsFound)
        assertFalse(NodeHome(workspace(withSettings = false)).settingsFound)
    }

    /** Первый запуск: настроек нет, базы нет — узел вправе завести и то и другое. */
    @Test
    fun `first start may create the database`() {
        val home = NodeHome(workspace(withSettings = false, withDatabase = false))

        home.requireDatabase(home.resolveJdbcUrl(OLD_URL))
    }

    @Test
    fun `existing workspace with its database passes`() {
        val home = NodeHome(workspace())

        home.requireDatabase(home.resolveJdbcUrl(OLD_URL))
    }

    /** Настройки есть, базы нет: это не новое рабочее место, а потерянная база — отказ с указанием пути. */
    @Test
    fun `settings without database refuse to start`() {
        val dir = workspace(withDatabase = false)
        val home = NodeHome(dir)

        val failure = assertFailsWith<IllegalStateException> {
            home.requireDatabase(home.resolveJdbcUrl(OLD_URL))
        }

        assertTrue(failure.message!!.contains(dir.toAbsolutePath().normalize().resolve("data/core.db").toString()), failure.message)
        assertTrue(failure.message!!.contains(NodeHome.PROPERTY), failure.message)
        assertFalse(Files.exists(dir.resolve("data/core.db")), "the database was created instead of refusing")
    }

    @Test
    fun `postgres settings are not checked for a file`() {
        val home = NodeHome(workspace(withDatabase = false))

        home.requireDatabase("jdbc:postgresql://db/core")
    }

    @Test
    fun `blank property means the launch directory`() {
        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(), NodeHome.of("").dir)
        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(), NodeHome.of(null).dir)
    }

    @Test
    fun `property names the workspace`() {
        val dir = workspace()

        val home = NodeHome.of(dir.toString())

        assertEquals(dir.toAbsolutePath().normalize(), home.dir)
        assertEquals(dir.toAbsolutePath().normalize().resolve("config/core-settings.json"), home.settingsFile)
    }

    private companion object {
        /** Так путь записан в настройках прежних установок. */
        const val OLD_URL = "jdbc:sqlite:data/core.db?busy_timeout=30000"
    }
}
