package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.storage.impl.domain.model.CashboxRecord
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * После миграций у кассы есть колонка названия, и название переживает
 * запись, правку и чтение. Проверяется итог всей цепочки, а не текст
 * последнего скрипта: стенд обновляется миграцией.
 */
class CashboxNameColumnTest {

    private val dbFile = Files.createTempDirectory("cashbox-name").resolve("core.db").toFile()

    init {
        DefaultStorageBootstrap().migrate(StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"))
    }

    @Test
    fun `cashbox has nullable name`() {
        val columns = columnsOf("cashbox")
        assertTrue(columns.contains("name"), columns.toString())
    }

    @Test
    fun `name survives insert, update and read`() {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            val repository = JdbcCashboxRepository(connection)
            val named = CashboxRecord(
                id = "kkm-named", createdAt = 1L, updatedAt = 1L, mode = "REGISTRATION", state = "ACTIVE",
                ofdProvider = "BFD:DEV", registrationNumber = "260940000021",
                taxRegime = "NO_VAT", defaultVatGroup = "NO_VAT", name = "Касса 2 на Достык"
            )
            val unnamed = named.copy(id = "kkm-unnamed", registrationNumber = "260940000020", name = null)
            assertTrue(repository.insert(named))
            assertTrue(repository.insert(unnamed))

            assertEquals("Касса 2 на Достык", repository.findById("kkm-named")!!.name)
            assertNull(repository.findById("kkm-unnamed")!!.name)

            val read = repository.findById("kkm-named")!!
            assertTrue(repository.update(read.copy(name = "Касса у входа")))
            assertEquals("Касса у входа", repository.findById("kkm-named")!!.name)

            assertTrue(repository.update(read.copy(name = null)))
            assertNull(repository.findById("kkm-named")!!.name)
        }
    }

    private fun columnsOf(table: String): List<String> {
        val columns = mutableListOf<String>()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            val rs = connection.createStatement().executeQuery("PRAGMA table_info($table)")
            while (rs.next()) {
                columns.add(rs.getString("name"))
            }
        }
        return columns
    }
}
