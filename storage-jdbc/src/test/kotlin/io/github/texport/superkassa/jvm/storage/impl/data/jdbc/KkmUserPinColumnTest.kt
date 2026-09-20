package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * После миграций у таблицы пользователей не остаётся колонки открытого пина.
 *
 * Колонка `pin` жила рядом с `pin_hash` и ничему не служила: вход сверяется
 * по хешу. Стенд обновляется миграцией, поэтому проверяется итог всей
 * цепочки, а не текст последнего скрипта.
 */
class KkmUserPinColumnTest {

    @Test
    fun `kkm_user has pin_hash and no plain pin`() {
        val dbFile = Files.createTempDirectory("kkm-user-pin").resolve("core.db").toFile()
        DefaultStorageBootstrap().migrate(StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"))

        val columns = columnsOf(dbFile.absolutePath, "kkm_user")

        assertEquals(true, columns.isNotEmpty(), "таблица kkm_user не создана")
        assertTrue(columns.contains("pin_hash"), columns.toString())
        assertFalse(columns.contains("pin"), columns.toString())
    }

    private fun columnsOf(dbPath: String, table: String): List<String> {
        val columns = mutableListOf<String>()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            val rs = connection.createStatement().executeQuery("PRAGMA table_info($table)")
            while (rs.next()) {
                columns.add(rs.getString("name"))
            }
        }
        return columns
    }
}
