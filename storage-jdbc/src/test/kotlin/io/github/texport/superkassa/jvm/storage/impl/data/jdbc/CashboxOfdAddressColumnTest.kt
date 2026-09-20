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
 * После миграций у кассы есть колонки адреса ОФД, и адрес переживает
 * запись и чтение. Стенд обновляется миграцией, поэтому проверяется итог
 * всей цепочки, а не текст последнего скрипта.
 */
class CashboxOfdAddressColumnTest {

    private val dbFile = Files.createTempDirectory("cashbox-ofd-address").resolve("core.db").toFile()

    init {
        DefaultStorageBootstrap().migrate(StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"))
    }

    @Test
    fun `cashbox has nullable ofd_host and ofd_port`() {
        val columns = columnsOf("cashbox")
        assertTrue(columns.contains("ofd_host"), columns.toString())
        assertTrue(columns.contains("ofd_port"), columns.toString())
    }

    @Test
    fun `custom address survives insert, update and read`() {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            val repository = JdbcCashboxRepository(connection)
            val custom = CashboxRecord(
                id = "kkm-custom", createdAt = 1L, updatedAt = 1L, mode = "REGISTRATION", state = "ACTIVE",
                ofdProvider = "CUSTOM:DEV", ofdHost = "192.168.50.35", ofdPort = 17700,
                taxRegime = "NO_VAT", defaultVatGroup = "NO_VAT"
            )
            val reference = custom.copy(id = "kkm-bfd", ofdProvider = "BFD:DEV", ofdHost = null, ofdPort = null)
            assertTrue(repository.insert(custom))
            assertTrue(repository.insert(reference))

            val read = repository.findById("kkm-custom")!!
            assertEquals("192.168.50.35", read.ofdHost)
            assertEquals(17700, read.ofdPort)
            assertNull(repository.findById("kkm-bfd")!!.ofdHost)
            assertNull(repository.findById("kkm-bfd")!!.ofdPort)

            assertTrue(repository.update(read.copy(ofdHost = "ofd.local", ofdPort = 7777)))
            val updated = repository.findById("kkm-custom")!!
            assertEquals("ofd.local", updated.ofdHost)
            assertEquals(7777, updated.ofdPort)
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
