package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SqliteConnectorTest {

    @Test
    fun testConnectCreatesParentDirectory() {
        val testDir = File("build/test_sqlite_dir")
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
        val dbFile = File(testDir, "test.db")
        val jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"

        val config = StorageConfig(
            jdbcUrl = jdbcUrl,
            user = null,
            password = null
        )

        val connector = SqliteConnector()
        val connection = connector.connect(config)

        try {
            assertTrue(testDir.exists(), "The parent directory should have been automatically created")
            assertTrue(testDir.isDirectory, "The path should be a directory")
        } finally {
            connection.close()
            if (testDir.exists()) {
                testDir.deleteRecursively()
            }
        }
    }

    @Test
    fun testConnectHandlesPathTraversalSafely() {
        val testDir = File("build/test_sqlite_traversal/../test_sqlite_traversal_resolved")
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
        val dbFile = File(testDir, "test.db")
        val jdbcUrl = "jdbc:sqlite:${dbFile.path}"

        val config = StorageConfig(
            jdbcUrl = jdbcUrl,
            user = null,
            password = null
        )

        val connector = SqliteConnector()
        val connection = connector.connect(config)

        try {
            val resolvedDir = File("build/test_sqlite_traversal_resolved")
            assertTrue(resolvedDir.exists(), "The traversed parent directory should have been resolved and created")
        } finally {
            connection.close()
            val resolvedDir = File("build/test_sqlite_traversal_resolved")
            if (resolvedDir.exists()) {
                resolvedDir.deleteRecursively()
            }
            val baseDir = File("build/test_sqlite_traversal")
            if (baseDir.exists()) {
                baseDir.deleteRecursively()
            }
        }
    }

    @Test
    fun testConnectWithNonSqliteUrl() {
        val config = StorageConfig(
            jdbcUrl = "jdbc:h2:mem:testdb",
            user = null,
            password = null
        )
        try {
            SqliteConnector().connect(config)
        } catch (_: Exception) {
            // expected connection failure
        }
    }

    @Test
    fun testConnectWithMemoryDatabase() {
        val config = StorageConfig(
            jdbcUrl = "jdbc:sqlite::memory:",
            user = null,
            password = null
        )
        val connector = SqliteConnector()
        val connection = connector.connect(config)
        try {
            assertTrue(connection.isValid(1))
        } finally {
            connection.close()
        }
    }

    @Test
    fun testConnectWithEmptyPath() {
        val config = StorageConfig(
            jdbcUrl = "jdbc:sqlite:",
            user = null,
            password = null
        )
        try {
            SqliteConnector().connect(config)
        } catch (_: Exception) {
            // expected connection failure
        }
    }

    @Test
    fun testConnectWithNoParentFile() {
        val config = StorageConfig(
            jdbcUrl = "jdbc:sqlite:test_no_parent.db",
            user = null,
            password = null
        )
        val connector = SqliteConnector()
        val connection = connector.connect(config)
        try {
            assertTrue(connection.isValid(1))
        } finally {
            connection.close()
            File("test_no_parent.db").delete()
        }
    }

    @Test
    fun testConnectWithExistingParentDirectory() {
        val testDir = File("build/test_sqlite_existing_dir")
        testDir.mkdirs()
        val dbFile = File(testDir, "test.db")
        val config = StorageConfig(
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}",
            user = null,
            password = null
        )
        val connector = SqliteConnector()
        val connection = connector.connect(config)
        try {
            assertTrue(testDir.exists())
        } finally {
            connection.close()
            testDir.deleteRecursively()
        }
    }
}
