package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.jvm.settings.api.CoreSettingsValidator
import io.github.texport.superkassa.jvm.settings.api.IllegalServerConfigurationException
import io.github.texport.superkassa.jvm.settings.impl.DefaultCoreSettingsValidator
import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDomain
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * Хранилище конфигурации ядра в базе данных (используется для server-режима).
 * Обеспечивает персистентное хранение настроек сервера в реляционной БД.
 *
 * @property jdbcUrl URL-адрес подключения к БД через JDBC.
 * @property user имя пользователя БД.
 * @property password пароль пользователя БД.
 * @property dataSource альтернативный источник данных (DataSource).
 * @property tableName имя таблицы для хранения конфигурации.
 * @property json сериализатор JSON.
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
) : CoreSettingsRepositoryPort {

    private val logger = LoggerFactory.getLogger(DatabaseCoreSettingsRepository::class.java)
    private val validator: CoreSettingsValidator = DefaultCoreSettingsValidator()

    init {
        require(jdbcUrl != null || dataSource != null) {
            "Either jdbcUrl or dataSource must be provided"
        }
        val urlToCheck = jdbcUrl ?: getJdbcUrlFromConnection()
        validator.validateNotSQLite(urlToCheck)
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

    /**
     * Загружает конфигурацию настроек ядра из базы данных.
     * Выполняет валидацию параметров для серверного режима.
     *
     * @return загруженные настройки [CoreSettings] или null, если запись отсутствует.
     * @throws IllegalServerConfigurationException если настройки некорректны.
     */
    override fun load(): CoreSettings? {
        return runCatching {
            use(getConnection()) { conn ->
                val sql = "SELECT settings_json FROM $tableName WHERE id = 1"
                use(conn.prepareStatement(sql)) { pstmt ->
                    use(pstmt.executeQuery()) { rs ->
                        if (rs.next()) {
                            val jsonText = rs.getString("settings_json")
                            val dto = json.decodeFromString(CoreSettingsDto.serializer(), jsonText)
                            val settings = dto.toDomain()
                            validator.validateSettings(settings, requireServerMode = true)
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

    /**
     * Сохраняет конфигурацию настроек ядра в базу данных.
     * Перед сохранением выполняет валидацию параметров.
     *
     * @param settings сохраняемые настройки [CoreSettings].
     * @return true, если настройки сохранены успешно, иначе false.
     * @throws IllegalServerConfigurationException если настройки некорректны.
     */
    override fun save(settings: CoreSettings): Boolean {
        return runCatching {
            validator.validateSettings(settings, requireServerMode = true)
            val dto = settings.toDto()
            val text = json.encodeToString(CoreSettingsDto.serializer(), dto)
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
        validator.validateSettings(defaults, requireServerMode = true)
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
