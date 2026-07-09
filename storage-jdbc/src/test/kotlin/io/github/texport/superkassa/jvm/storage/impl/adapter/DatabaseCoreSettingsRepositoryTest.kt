@file:Suppress("UnusedImport", "AnnotationOnSeparateLine")

package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.jvm.settings.api.IllegalServerConfigurationException
import io.mockk.*
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.model.settings.StorageSettings
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseCoreSettingsRepositoryTest {

    private val validSettings = CoreSettings(
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

    @Test
    fun `constructor validation errors`() {
        // Missing both URL and DataSource
        assertFailsWith<IllegalArgumentException> {
            DatabaseCoreSettingsRepository(jdbcUrl = null, dataSource = null)
        }

        // SQLite disallowed via URL
        assertFailsWith<IllegalServerConfigurationException> {
            DatabaseCoreSettingsRepository(jdbcUrl = "jdbc:sqlite:test.db")
        }

        // SQLite disallowed via DataSource metadata
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:sqlite:test.db"
        every { mockConn.close() } just Runs

        assertFailsWith<IllegalServerConfigurationException> {
            DatabaseCoreSettingsRepository(dataSource = mockDs)
        }
    }

    @Test
    fun `table creation fails on initialization but logs and continues`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.createStatement() } throws SQLException("Permission denied")
        every { mockConn.close() } just Runs

        // Should not throw, should log and proceed
        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = true)
        assertNotNull(repo)
    }

    @Test
    fun `load settings record not found`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmt = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement(any()) } returns mockPstmt
        every { mockPstmt.executeQuery() } returns mockRs
        every { mockRs.next() } returns false
        every { mockRs.close() } just Runs
        every { mockPstmt.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertNull(repo.load())
    }

    @Test
    fun `load settings success`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmt = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement(any()) } returns mockPstmt
        every { mockPstmt.executeQuery() } returns mockRs
        every { mockRs.next() } returns true
        every {
            mockRs.getString("settings_json")
        } returns """
            {
                "mode": "SERVER",
                "storage": {
                    "engine": "POSTGRESQL",
                    "jdbcUrl": "jdbc:postgresql://localhost:5432/db",
                    "user": "postgres",
                    "password": "password"
                },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """.trimIndent()
        every { mockRs.close() } just Runs
        every { mockPstmt.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        val loaded = repo.load()
        assertEquals(validSettings, loaded)
    }

    @Test
    fun `load settings DB error returns null`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement(any()) } throws SQLException("DB is down")
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertNull(repo.load())
    }

    @Test
    fun `save settings update existing`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmtSelect = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()
        val mockPstmtUpdate = mockk<PreparedStatement>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.autoCommit = false } just Runs
        every { mockConn.prepareStatement("SELECT 1 FROM superkassa_core_settings WHERE id = 1") } returns mockPstmtSelect
        every { mockPstmtSelect.executeQuery() } returns mockRs
        every { mockRs.next() } returns true
        every {
            mockConn.prepareStatement("UPDATE superkassa_core_settings SET settings_json = ? WHERE id = 1")
        } returns mockPstmtUpdate
        every { mockPstmtUpdate.setString(1, any()) } just Runs
        every { mockPstmtUpdate.executeUpdate() } returns 1
        every { mockConn.commit() } just Runs
        every { mockConn.autoCommit = true } just Runs
        every { mockRs.close() } just Runs
        every { mockPstmtSelect.close() } just Runs
        every { mockPstmtUpdate.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertTrue(repo.save(validSettings))
    }

    @Test
    fun `save settings insert new`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmtSelect = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()
        val mockPstmtInsert = mockk<PreparedStatement>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.autoCommit = false } just Runs
        every { mockConn.prepareStatement("SELECT 1 FROM superkassa_core_settings WHERE id = 1") } returns mockPstmtSelect
        every { mockPstmtSelect.executeQuery() } returns mockRs
        every { mockRs.next() } returns false
        every {
            mockConn.prepareStatement("INSERT INTO superkassa_core_settings (id, settings_json) VALUES (1, ?)")
        } returns mockPstmtInsert
        every { mockPstmtInsert.setString(1, any()) } just Runs
        every { mockPstmtInsert.executeUpdate() } returns 1
        every { mockConn.commit() } just Runs
        every { mockConn.autoCommit = true } just Runs
        every { mockRs.close() } just Runs
        every { mockPstmtSelect.close() } just Runs
        every { mockPstmtInsert.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertTrue(repo.save(validSettings))
    }

    @Test
    fun `save settings fails rolls back transaction`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.autoCommit = false } just Runs
        every { mockConn.prepareStatement(any()) } throws SQLException("Constraint violation")
        every { mockConn.rollback() } just Runs
        every { mockConn.autoCommit = true } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertFalse(repo.save(validSettings))
    }

    @Test
    fun `loadOrCreate returns existing or saves defaults`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmtSelect = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()
        val mockPstmtInsert = mockk<PreparedStatement>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"

        // Mock load() returning null
        every {
            mockConn.prepareStatement("SELECT settings_json FROM superkassa_core_settings WHERE id = 1")
        } returns mockPstmtSelect
        every { mockPstmtSelect.executeQuery() } returns mockRs
        every { mockRs.next() } returns false
        every { mockRs.close() } just Runs
        every { mockPstmtSelect.close() } just Runs

        // Mock save() flow
        every { mockConn.autoCommit = false } just Runs
        every { mockConn.prepareStatement("SELECT 1 FROM superkassa_core_settings WHERE id = 1") } returns mockPstmtSelect
        every {
            mockConn.prepareStatement("INSERT INTO superkassa_core_settings (id, settings_json) VALUES (1, ?)")
        } returns mockPstmtInsert
        every { mockPstmtInsert.setString(1, any()) } just Runs
        every { mockPstmtInsert.executeUpdate() } returns 1
        every { mockConn.commit() } just Runs
        every { mockConn.autoCommit = true } just Runs
        every { mockPstmtInsert.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        val loaded = repo.loadOrCreate(validSettings)
        assertEquals(validSettings, loaded)
    }

    @Test
    fun `save throws IllegalServerConfigurationException if invalid settings`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        val badSettings = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:db.db"),
            allowChanges = true
        )

        assertFailsWith<IllegalServerConfigurationException> {
            repo.save(badSettings)
        }
    }

    @Test
    fun `load throws IllegalServerConfigurationException if invalid settings stored`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmt = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement(any()) } returns mockPstmt
        every { mockPstmt.executeQuery() } returns mockRs
        every { mockRs.next() } returns true
        every {
            mockRs.getString("settings_json")
        } returns """
            {
                "mode": "DESKTOP",
                "storage": {
                    "engine": "SQLITE",
                    "jdbcUrl": "jdbc:sqlite:db.db"
                },
                "allowChanges": true
            }
        """.trimIndent()
        every { mockRs.close() } just Runs
        every { mockPstmt.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertFailsWith<IllegalServerConfigurationException> {
            repo.load()
        }
    }

    @Test
    fun `constructor works with jdbcUrl and DriverManager mock`() {
        mockkStatic(DriverManager::class)
        val mockConn = mockk<Connection>()
        val mockStmt = mockk<Statement>()

        every { DriverManager.getConnection("jdbc:postgresql://localhost:5432/db", any(), any()) } returns mockConn
        every { mockConn.createStatement() } returns mockStmt
        every { mockStmt.execute(any()) } returns true
        every { mockStmt.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(
            jdbcUrl = "jdbc:postgresql://localhost:5432/db",
            user = "user",
            password = "pwd",
            createTable = true
        )
        assertNotNull(repo)
        unmockkStatic(DriverManager::class)
    }

    @Test
    fun `getJdbcUrlFromConnection throws exception returns null`() {
        val mockDs = mockk<DataSource>()
        every { mockDs.connection } throws SQLException("Connection error")
        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertNotNull(repo)
    }

    @Test
    fun `save settings connection error returns false`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        every { mockDs.connection } throws SQLException("DB closed")
        assertFalse(repo.save(validSettings))
    }

    @Test
    fun `table creation success on initialization`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockStmt = mockk<Statement>()
        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.createStatement() } returns mockStmt
        every { mockStmt.execute(any()) } returns true
        every { mockStmt.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = true)
        assertNotNull(repo)
    }

    @Test
    fun `loadOrCreate returns existing and does not save`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmt = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement("SELECT settings_json FROM superkassa_core_settings WHERE id = 1") } returns mockPstmt
        every { mockPstmt.executeQuery() } returns mockRs
        every { mockRs.next() } returns true
        every {
            mockRs.getString("settings_json")
        } returns """
            {
                "mode": "SERVER",
                "storage": {
                    "engine": "POSTGRESQL",
                    "jdbcUrl": "jdbc:postgresql://localhost:5432/db",
                    "user": "postgres",
                    "password": "password"
                },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """.trimIndent()
        every { mockRs.close() } just Runs
        every { mockPstmt.close() } just Runs
        every { mockConn.close() } just Runs

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        val loaded = repo.loadOrCreate(validSettings)
        assertEquals(validSettings, loaded)
    }

    @Test
    fun `use close fails logs error and returns null`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()
        val mockPstmt = mockk<PreparedStatement>()
        val mockRs = mockk<ResultSet>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement(any()) } returns mockPstmt
        every { mockPstmt.executeQuery() } returns mockRs
        every { mockRs.next() } returns false
        every { mockRs.close() } just Runs
        every { mockPstmt.close() } just Runs
        every { mockConn.close() } throws SQLException("Connection close error")

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        assertNull(repo.load())
    }

    @Test
    fun `use block throws and close also fails preserves original exception`() {
        val mockDs = mockk<DataSource>()
        val mockConn = mockk<Connection>()
        val mockMeta = mockk<DatabaseMetaData>()

        every { mockDs.connection } returns mockConn
        every { mockConn.metaData } returns mockMeta
        every { mockMeta.url } returns "jdbc:postgresql://localhost:5432/db"
        every { mockConn.prepareStatement(any()) } throws SQLException("Query failed")
        every { mockConn.close() } throws SQLException("Close failed")

        val repo = DatabaseCoreSettingsRepository(dataSource = mockDs, createTable = false)
        // should capture SQLException("Query failed") and return null
        assertNull(repo.load())
    }
}
