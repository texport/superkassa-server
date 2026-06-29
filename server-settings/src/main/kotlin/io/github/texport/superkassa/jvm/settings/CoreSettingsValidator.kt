package io.github.texport.superkassa.jvm.settings

import io.github.texport.superkassa.jvm.settings.validation.*
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings

/**
 * Валидатор для проверки корректности конфигурации ядра.
 */
object CoreSettingsValidator {

    val SQLITE_NOT_ALLOWED_ERROR = ValidationErrors.SQLITE_NOT_ALLOWED_ERROR
    val SERVER_MODE_ONLY_ERROR = ValidationErrors.SERVER_MODE_ONLY_ERROR

    fun validateNotSQLite(jdbcUrl: String?) {
        if (jdbcUrl != null && jdbcUrl.lowercase().contains("jdbc:sqlite:")) {
            throw IllegalServerConfigurationException(SQLITE_NOT_ALLOWED_ERROR)
        }
    }

    fun validateSettings(settings: CoreSettings) {
        validateSettings(settings, false)
    }

    fun validateSettings(settings: CoreSettings, requireServerMode: Boolean) {
        if (requireServerMode && settings.mode != CoreMode.SERVER) {
            throw IllegalServerConfigurationException(SERVER_MODE_ONLY_ERROR)
        }

        StorageValidator.validateStorage(settings)

        if (settings.mode == CoreMode.SERVER) {
            StorageValidator.validateServerSpecifics(settings)
        }

        val proto = settings.ofdProtocolVersion
        if (proto.isBlank() || !isDigitsOnly(proto)) {
            throw IllegalServerConfigurationException(ValidationErrors.OFD_PROTOCOL_VERSION_ERROR)
        }

        if (settings.ofdTimeoutSeconds < MIN_OFD_TIMEOUT_SECONDS) {
            throw IllegalServerConfigurationException(ValidationErrors.OFD_TIMEOUT_ERROR)
        }
        if (settings.ofdReconnectIntervalSeconds < MIN_OFD_RECONNECT_INTERVAL_SECONDS) {
            throw IllegalServerConfigurationException(ValidationErrors.OFD_RECONNECT_INTERVAL_ERROR)
        }

        val delivery = settings.delivery
        if (delivery != null) {
            DeliveryValidator.validateDeliveryChannels(delivery)
        }
    }
}
