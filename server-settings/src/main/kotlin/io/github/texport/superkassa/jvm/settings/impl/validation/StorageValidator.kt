package io.github.texport.superkassa.jvm.settings.impl.validation

import io.github.texport.superkassa.jvm.settings.api.CoreSettingsValidator
import io.github.texport.superkassa.jvm.settings.api.IllegalServerConfigurationException
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings

internal object StorageValidator {

    private val resolver = DefaultErrorResolver()

    fun validateStorage(settings: CoreSettings) {
        val storage = settings.storage
        val engine = storage.engine
        if (engine.isBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.STORAGE_ENGINE_BLANK).toString())
        }
        val upperEngine = engine.uppercase()
        if (upperEngine !in listOf("SQLITE", "POSTGRESQL", "MYSQL")) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.STORAGE_ENGINE_INVALID).toString())
        }
        val url = storage.jdbcUrl
        if (url.isBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.JDBC_URL_BLANK).toString())
        }
        if (!url.lowercase().startsWith("jdbc:")) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.JDBC_URL_INVALID_SCHEME).toString())
        }
    }

    fun validateServerSpecifics(settings: CoreSettings) {
        val engine = settings.storage.engine.uppercase()
        val url = settings.storage.jdbcUrl.lowercase()
        if (engine == "SQLITE" || url.contains("jdbc:sqlite:")) {
            throw IllegalServerConfigurationException(CoreSettingsValidator.SQLITE_NOT_ALLOWED_ERROR)
        }
        if (settings.nodeId.isBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.NODE_ID_BLANK).toString())
        }
        if (settings.storage.user.isNullOrBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.DATABASE_USER_BLANK).toString())
        }
        if (settings.storage.password.isNullOrBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.DATABASE_PASSWORD_BLANK).toString())
        }
    }
}
