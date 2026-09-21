package io.github.texport.superkassa.jvm.storage.impl.domain.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разбор адреса SQLite один на коннектор и узел: путь к файлу достаётся
 * без параметров после `?`, а база в памяти и адрес в форме `file:`
 * за файл не принимаются.
 */
class SqliteJdbcUrlTest {

    @Test
    fun `file path is taken without query`() {
        assertEquals("data/core.db", SqliteJdbcUrl.filePath("jdbc:sqlite:data/core.db?busy_timeout=30000"))
        assertEquals("/abs/core.db", SqliteJdbcUrl.filePath("jdbc:sqlite:/abs/core.db"))
    }

    @Test
    fun `memory, empty and uri forms have no file`() {
        assertNull(SqliteJdbcUrl.filePath("jdbc:sqlite::memory:"))
        assertNull(SqliteJdbcUrl.filePath("jdbc:sqlite:"))
        assertNull(SqliteJdbcUrl.filePath("jdbc:sqlite:file:memdb?mode=memory&cache=shared"))
    }

    @Test
    fun `other engines are not sqlite`() {
        assertNull(SqliteJdbcUrl.filePath("jdbc:postgresql://localhost/db"))
        assertFalse(SqliteJdbcUrl.isSqlite("jdbc:mysql://localhost/db"))
        assertTrue(SqliteJdbcUrl.isSqlite("JDBC:SQLITE:core.db"))
    }

    @Test
    fun `replacing the path keeps the query`() {
        assertEquals(
            "jdbc:sqlite:/home/data/core.db?busy_timeout=30000",
            SqliteJdbcUrl.withFilePath("jdbc:sqlite:data/core.db?busy_timeout=30000", "/home/data/core.db")
        )
        assertEquals("jdbc:sqlite:/home/core.db", SqliteJdbcUrl.withFilePath("jdbc:sqlite:core.db", "/home/core.db"))
    }
}
