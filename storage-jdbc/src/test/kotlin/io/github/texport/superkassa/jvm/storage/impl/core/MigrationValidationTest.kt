package io.github.texport.superkassa.jvm.storage.impl.core

import io.github.texport.superkassa.jvm.storage.impl.data.migration.DefaultMigrationCatalog
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationValidationTest {

    @Test
    fun testCatalogCompletenessAndSync() {
        val catalog = DefaultMigrationCatalog()

        // 1. Verify we get the same versions for all engines
        val sqliteScripts = catalog.scriptsFor(StorageEngine.SQLITE)
        val mysqlScripts = catalog.scriptsFor(StorageEngine.MYSQL)
        val postgresScripts = catalog.scriptsFor(StorageEngine.POSTGRES)

        assertEquals(sqliteScripts.size, mysqlScripts.size, "MySQL and SQLite script counts must match")
        assertEquals(sqliteScripts.size, postgresScripts.size, "Postgres and SQLite script counts must match")

        for (i in sqliteScripts.indices) {
            val sqlite = sqliteScripts[i]
            val mysql = mysqlScripts[i]
            val postgres = postgresScripts[i]

            assertEquals(sqlite.version, mysql.version, "Version mismatch at index $i")
            assertEquals(sqlite.version, postgres.version, "Version mismatch at index $i")

            assertTrue(sqlite.resourcePath.endsWith(".sql"), "Resource path must end with .sql")
            assertTrue(mysql.resourcePath.endsWith(".sql"), "Resource path must end with .sql")
            assertTrue(postgres.resourcePath.endsWith(".sql"), "Resource path must end with .sql")
        }

        // 2. Discover all migration files in resources and ensure they are registered
        // We look at the actual directories under src/main/resources/db/migration
        val baseDir = File("src/main/resources/db/migration")
        assertTrue(baseDir.exists(), "Migration base directory must exist at ${baseDir.absolutePath}")

        for (engine in StorageEngine.entries) {
            val engineDirName = when (engine) {
                StorageEngine.SQLITE -> "sqlite"
                StorageEngine.POSTGRES -> "postgres"
                StorageEngine.MYSQL -> "mysql"
            }
            val engineDir = File(baseDir, engineDirName)
            assertTrue(engineDir.exists(), "Directory ${engineDir.absolutePath} must exist")

            val files = engineDir.listFiles { _, name -> name.endsWith(".sql") } ?: emptyArray()
            val registeredPaths = catalog.scriptsFor(engine).map { it.resourcePath.substringAfterLast("/") }.toSet()

            for (file in files) {
                val fileName = file.name
                assertTrue(
                    registeredPaths.contains(fileName),
                    "File $fileName in ${engineDir.name} is NOT registered in DefaultMigrationCatalog"
                )
            }

            assertEquals(
                files.size,
                registeredPaths.size,
                "Number of SQL files in ${engineDir.name} must match registered scripts"
            )
        }
    }

    @Test
    fun testSqliteMigrationsExecution() {
        val catalog = DefaultMigrationCatalog()
        val scripts = catalog.scriptsFor(StorageEngine.SQLITE)

        // Run against an in-memory SQLite database
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            createSchemaMigrationsTable(conn)
            for (script in scripts) {
                executeSqlScript(conn, script.resourcePath)
            }
        }
    }

    private fun createSchemaMigrationsTable(conn: java.sql.Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE schema_migrations (
                    version TEXT PRIMARY KEY,
                    checksum TEXT NOT NULL,
                    applied_at BIGINT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun executeSqlScript(conn: java.sql.Connection, resourcePath: String) {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Migration resource not found: $resourcePath")
        val sql = stream.bufferedReader().use { it.readText() }

        val statements = sql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        conn.createStatement().use { stmt ->
            for (statement in statements) {
                try {
                    stmt.execute(statement)
                } catch (e: java.sql.SQLException) {
                    throw IllegalStateException("Failed executing SQL statement in $resourcePath: $statement", e)
                }
            }
        }
    }

    @Test
    fun testMysqlAndPostgresSqlSyntax() {
        val catalog = DefaultMigrationCatalog()

        for (engine in listOf(StorageEngine.MYSQL, StorageEngine.POSTGRES)) {
            val scripts = catalog.scriptsFor(engine)
            for (script in scripts) {
                val stream = javaClass.classLoader.getResourceAsStream(script.resourcePath)
                    ?: error("Migration resource not found: ${script.resourcePath}")
                val sql = stream.bufferedReader().use { it.readText() }

                // Basic validation: must not have empty query or mismatched quotes/parentheses
                assertTrue(sql.isNotBlank(), "SQL script ${script.resourcePath} is empty")

                val statements = sql.split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                for (stmt in statements) {
                    // Check for unbalanced parentheses/quotes
                    val singleQuotes = stmt.count { it == '\'' }
                    val doubleQuotes = stmt.count { it == '"' }
                    val openParens = stmt.count { it == '(' }
                    val closeParens = stmt.count { it == ')' }

                    assertEquals(
                        0,
                        singleQuotes % 2,
                        "Unbalanced single quotes in statement: '$stmt' in ${script.resourcePath}"
                    )
                    assertEquals(
                        0,
                        doubleQuotes % 2,
                        "Unbalanced double quotes in statement: '$stmt' in ${script.resourcePath}"
                    )
                    assertEquals(
                        openParens,
                        closeParens,
                        "Mismatched parentheses in statement: '$stmt' in ${script.resourcePath}"
                    )

                    // Ensure statement doesn't end with a comma (common copy-paste syntax error)
                    assertTrue(
                        !stmt.endsWith(","),
                        "Statement must not end with a comma: '$stmt' in ${script.resourcePath}"
                    )
                }
            }
        }
    }
}
