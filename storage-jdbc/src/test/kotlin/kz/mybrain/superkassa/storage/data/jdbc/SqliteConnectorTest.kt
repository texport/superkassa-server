package kz.mybrain.superkassa.storage.data.jdbc

import kz.mybrain.superkassa.storage.domain.config.StorageConfig
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
}
