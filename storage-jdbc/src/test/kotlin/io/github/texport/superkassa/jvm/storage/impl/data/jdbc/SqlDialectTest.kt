package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDialectTest {
    private val insert = "INSERT INTO t (id) VALUES (?)"

    @Test
    fun `SQLite пропускает занятый ключ и не запирает строку чтением`() {
        val sqlite = SqlDialect(connectionTo("SQLite"))

        assertEquals("$insert ON CONFLICT (id) DO NOTHING", sqlite.insertIfAbsent(insert, "id"))
        assertEquals("", sqlite.lockingRead())
    }

    @Test
    fun `PostgreSQL пропускает занятый ключ и запирает строку чтением`() {
        val postgres = SqlDialect(connectionTo("PostgreSQL"))

        assertEquals("$insert ON CONFLICT (id) DO NOTHING", postgres.insertIfAbsent(insert, "id"))
        assertEquals(" FOR UPDATE", postgres.lockingRead())
    }

    @Test
    fun `MySQL пропускает занятый ключ своим словом`() {
        val mysql = SqlDialect(connectionTo("MySQL"))

        assertEquals("INSERT IGNORE INTO t (id) VALUES (?)", mysql.insertIfAbsent(insert, "id"))
        assertEquals(" FOR UPDATE", mysql.lockingRead())
    }

    private fun connectionTo(product: String): Connection = mockk {
        every { metaData.databaseProductName } returns product
    }
}
