package io.github.texport.superkassa.jvm.settings.validation

import io.github.texport.superkassa.jvm.settings.CoreSettingsValidator
import io.github.texport.superkassa.jvm.settings.IllegalServerConfigurationException
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings

internal object StorageValidator {

    fun validateStorage(settings: CoreSettings) {
        val storage = settings.storage
        val engine = storage.engine
        if (engine.isBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.STORAGE_ENGINE_BLANK)
        }
        val upperEngine = engine.uppercase()
        if (upperEngine !in listOf("SQLITE", "POSTGRESQL", "MYSQL")) {
            throw IllegalServerConfigurationException(ValidationErrors.STORAGE_ENGINE_INVALID)
        }
        val url = storage.jdbcUrl
        if (url.isBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.JDBC_URL_BLANK)
        }
        if (!url.lowercase().startsWith("jdbc:")) {
            throw IllegalServerConfigurationException(ValidationErrors.JDBC_URL_INVALID_SCHEME)
        }
    }

    fun validateServerSpecifics(settings: CoreSettings) {
        val engine = settings.storage.engine.uppercase()
        val url = settings.storage.jdbcUrl.lowercase()
        if (engine == "SQLITE" || url.contains("jdbc:sqlite:")) {
            throw IllegalServerConfigurationException(CoreSettingsValidator.SQLITE_NOT_ALLOWED_ERROR)
        }
        if (settings.nodeId.isBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.NODE_ID_BLANK)
        }
        if (settings.storage.user.isNullOrBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.DATABASE_USER_BLANK)
        }
        if (settings.storage.password.isNullOrBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.DATABASE_PASSWORD_BLANK)
        }
    }
}
