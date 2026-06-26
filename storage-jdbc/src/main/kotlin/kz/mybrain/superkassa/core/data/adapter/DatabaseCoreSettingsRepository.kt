package kz.mybrain.superkassa.core.data.adapter

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.texport.superkassa.jvm.settings.CoreSettingsValidator
import io.github.texport.superkassa.jvm.settings.IllegalServerConfigurationException
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.service.CoreSettingsRepository
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * Хранилище конфигурации ядра в базе данных (используется для server-режима).
 */
class DatabaseCoreSettingsRepository(
    private val jdbcUrl: String? = null,
    private val user: String? = null,
    private val password: String? = null,
    private val dataSource: DataSource? = null,
    private val tableName: String = "superkassa_core_settings",
    createTable: Boolean = true,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) : CoreSettingsRepository {

    private val logger = LoggerFactory.getLogger(DatabaseCoreSettingsRepository::class.java)

    init {
        require(jdbcUrl != null || dataSource != null) {
            "Either jdbcUrl or dataSource must be provided"
        }
        val urlToCheck = jdbcUrl ?: getJdbcUrlFromConnection()
        CoreSettingsValidator.validateNotSQLite(urlToCheck)
        if (createTable) {
            createTableIfNotExists()
        }
    }

    private fun getConnection(): Connection {
        val ds = dataSource
        if (ds != null) return ds.connection
        return DriverManager.getConnection(jdbcUrl!!, user, password)
    }

    private fun getJdbcUrlFromConnection(): String? {
        return runCatching {
            use(getConnection()) { conn ->
                conn.metaData.url
            }
        }.getOrNull()
    }

    private fun createTableIfNotExists() {
        runCatching {
            use(getConnection()) { conn ->
                use(conn.createStatement()) { stmt ->
                    val sql = "CREATE TABLE IF NOT EXISTS $tableName (id INT PRIMARY KEY, settings_json TEXT NOT NULL)"
                    stmt.execute(sql)
                }
            }
        }.onFailure { e ->
            logger.error("Failed to create settings table: {}", e.message)
        }
    }

    override fun load(): CoreSettings? {
        return runCatching {
            use(getConnection()) { conn ->
                val sql = "SELECT settings_json FROM $tableName WHERE id = 1"
                use(conn.prepareStatement(sql)) { pstmt ->
                    use(pstmt.executeQuery()) { rs ->
                        if (rs.next()) {
                            val jsonText = rs.getString("settings_json")
                            val settings = json.decodeFromString(CoreSettings.serializer(), jsonText)
                            CoreSettingsValidator.validateSettings(settings, requireServerMode = true)
                            settings
                        } else {
                            null
                        }
                    }
                }
            }
        }.getOrElse { e ->
            if (e is IllegalServerConfigurationException) throw e
            logger.error("Failed to load settings from database: {}", e.message)
            null
        }
    }

    override fun save(settings: CoreSettings): Boolean {
        return runCatching {
            CoreSettingsValidator.validateSettings(settings, requireServerMode = true)
            val text = json.encodeToString(settings)
            use(getConnection()) { conn ->
                conn.autoCommit = false
                try {
                    val checkSql = "SELECT 1 FROM $tableName WHERE id = 1"
                    val exists = use(conn.prepareStatement(checkSql)) { pstmtSelect ->
                        use(pstmtSelect.executeQuery()) { rs ->
                            rs.next()
                        }
                    }

                    val sql = if (exists) {
                        "UPDATE $tableName SET settings_json = ? WHERE id = 1"
                    } else {
                        "INSERT INTO $tableName (id, settings_json) VALUES (1, ?)"
                    }
                    use(conn.prepareStatement(sql)) { pstmtWrite ->
                        pstmtWrite.setString(1, text)
                        pstmtWrite.executeUpdate()
                    }
                    conn.commit()
                    true
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }.getOrElse { e ->
            if (e is IllegalServerConfigurationException) throw e
            logger.error("Failed to save settings to database: {}", e.message)
            false
        }
    }

    override fun loadOrCreate(defaults: CoreSettings): CoreSettings {
        CoreSettingsValidator.validateSettings(defaults, requireServerMode = true)
        return load() ?: defaults.also { save(it) }
    }

    // Единственная не-inlined функция управления ресурсами для 100% покрытия JaCoCo
    private fun <T : AutoCloseable, R> use(closeable: T, block: (T) -> R): R {
        val result: R
        try {
            result = block(closeable)
        } catch (e: Exception) {
            try {
                closeable.close()
            } catch (_: Exception) {
                // Ignore closeEx to preserve the original exception e
            }
            throw e
        }
        closeable.close()
        return result
    }
}
