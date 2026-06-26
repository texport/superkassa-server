package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.model.KkmUserRecord
import kz.mybrain.superkassa.storage.domain.model.ShiftRecord
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RepositoriesTest {

    @Test
    fun testJdbcKkmUserRepository() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE kkm_user (
                    id TEXT,
                    cashbox_id TEXT,
                    name TEXT,
                    role TEXT,
                    pin TEXT,
                    pin_hash TEXT,
                    created_at INTEGER,
                    PRIMARY KEY (cashbox_id, id)
                )
            """.trimIndent())
        }

        val repo = JdbcKkmUserRepository(connection)
        val user = KkmUserRecord(
            id = "user-1",
            cashboxId = "cashbox-1",
            name = "John Doe",
            role = "CASHIER",
            pin = "1111",
            pinHash = "hash1111",
            createdAt = 123456789L
        )

        // Insert success
        assertTrue(repo.insert(user))

        // Insert duplicate key (should fail/throw or return false depending on implementation, JDBC repo throws SQLiteException but we can catch it or update non-existent)
        // Let's test update failure
        assertFalse(repo.update("cashbox-1", "non-existent-user", name = "Nobody", role = null, pin = null, pinHash = null))

        // Find by id success
        val found = repo.findById("cashbox-1", "user-1")
        assertNotNull(found)
        assertEquals("John Doe", found.name)
        assertEquals("CASHIER", found.role)

        // Find by id null
        assertNull(repo.findById("cashbox-1", "non-existent"))

        // Find by cashbox and pin hash success
        val foundByPin = repo.findByCashboxAndPinHash("cashbox-1", "hash1111")
        assertNotNull(foundByPin)
        assertEquals("user-1", foundByPin.id)

        // Find by cashbox and pin hash null
        assertNull(repo.findByCashboxAndPinHash("cashbox-1", "wrong-hash"))

        // List
        val list = repo.listByCashbox("cashbox-1")
        assertEquals(1, list.size)
        assertEquals("user-1", list[0].id)

        // Update success
        assertTrue(repo.update("cashbox-1", "user-1", name = "Jane Doe", role = "ADMIN", pin = "2222", pinHash = "hash2222"))
        val updated = repo.findById("cashbox-1", "user-1")
        assertNotNull(updated)
        assertEquals("Jane Doe", updated.name)
        assertEquals("ADMIN", updated.role)
        assertEquals("2222", updated.pin)
        assertEquals("hash2222", updated.pinHash)

        // Delete by id success
        assertTrue(repo.deleteById("cashbox-1", "user-1"))
        assertNull(repo.findById("cashbox-1", "user-1"))

        // Delete by id fail (non-existent)
        assertFalse(repo.deleteById("cashbox-1", "user-1"))

        // Delete by cashbox
        assertTrue(repo.insert(user))
        assertTrue(repo.deleteByCashbox("cashbox-1"))
        assertEquals(0, repo.listByCashbox("cashbox-1").size)

        connection.close()
    }

    @Test
    fun testJdbcShiftRepository() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE shift (
                    id TEXT PRIMARY KEY,
                    cashbox_id TEXT,
                    shift_no INTEGER,
                    status TEXT,
                    opened_at INTEGER,
                    closed_at INTEGER,
                    open_document_id TEXT,
                    close_document_id TEXT
                )
            """.trimIndent())
        }

        val repo = JdbcShiftRepository(connection)
        val shift = ShiftRecord(
            id = "shift-1",
            cashboxId = "cashbox-1",
            shiftNo = 5L,
            status = "OPEN",
            openedAt = 1000L,
            closedAt = null,
            openDocumentId = "doc-open-1",
            closeDocumentId = null
        )

        // Insert success
        assertTrue(repo.insert(shift))

        // Find by id success
        val found = repo.findById("shift-1")
        assertNotNull(found)
        assertEquals("OPEN", found.status)
        assertEquals(5L, found.shiftNo)
        assertNull(found.closedAt)
        assertNull(found.closeDocumentId)

        // Find by id null
        assertNull(repo.findById("non-existent-shift"))

        // Find open by cashbox success
        val openShift = repo.findOpenByCashbox("cashbox-1")
        assertNotNull(openShift)
        assertEquals("shift-1", openShift.id)

        // Find open by cashbox null
        assertNull(repo.findOpenByCashbox("cashbox-2"))

        // Find by shift no success
        val foundByNo = repo.findByShiftNo("cashbox-1", 5L)
        assertNotNull(foundByNo)
        assertEquals("shift-1", foundByNo.id)

        // Find by shift no null
        assertNull(repo.findByShiftNo("cashbox-1", 999L))

        // List by cashbox
        val list = repo.listByCashbox("cashbox-1", limit = 10, offset = 0)
        assertEquals(1, list.size)
        assertEquals("shift-1", list[0].id)

        // Count all
        assertEquals(1L, repo.countAll(null))
        assertEquals(1L, repo.countAll("OPEN"))
        assertEquals(0L, repo.countAll("CLOSED"))

        // Update close success
        assertTrue(repo.updateClose("shift-1", "CLOSED", 2000L, "doc-close-1"))
        val closed = repo.findById("shift-1")
        assertNotNull(closed)
        assertEquals("CLOSED", closed.status)
        assertEquals(2000L, closed.closedAt)
        assertEquals("doc-close-1", closed.closeDocumentId)

        // Update close fail (non-existent)
        assertFalse(repo.updateClose("non-existent-shift", "CLOSED", 3000L, null))

        // Delete by cashbox
        assertTrue(repo.deleteByCashbox("cashbox-1"))
        assertNull(repo.findById("shift-1"))

        connection.close()
    }

    @Test
    fun testJdbcBindings() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE test_bind (val_blob BLOB, val_long INTEGER, val_int INTEGER, val_string TEXT)")
        }

        connection.prepareStatement("INSERT INTO test_bind (val_blob, val_long, val_int, val_string) VALUES (?, ?, ?, ?)").use { pstmt ->
            // Test with nulls
            pstmt.bindBytes(1, null)
            pstmt.bindLong(2, null)
            pstmt.bindInt(3, null)
            pstmt.bindString(4, null)
            pstmt.executeUpdate()

            // Test with values
            pstmt.bindBytes(1, byteArrayOf(4, 5))
            pstmt.bindLong(2, 999L)
            pstmt.bindInt(3, 42)
            pstmt.bindString(4, "hello")
            pstmt.executeUpdate()
        }

        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM test_bind").use { rs ->
                // First row (nulls)
                assertTrue(rs.next())
                assertNull(rs.getBytes("val_blob"))
                assertEquals(0L, rs.getLong("val_long"))
                assertTrue(rs.wasNull())
                assertEquals(0, rs.getInt("val_int"))
                assertTrue(rs.wasNull())
                assertNull(rs.getString("val_string"))

                // Second row (values)
                assertTrue(rs.next())
                val bytes = rs.getBytes("val_blob")
                assertNotNull(bytes)
                assertEquals(2, bytes.size)
                assertEquals(999L, rs.getLong("val_long"))
                assertEquals(42, rs.getInt("val_int"))
                assertEquals("hello", rs.getString("val_string"))
            }
        }
        connection.close()
    }
}
