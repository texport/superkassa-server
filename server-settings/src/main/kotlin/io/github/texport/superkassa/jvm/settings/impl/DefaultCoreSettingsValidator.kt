package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.jvm.settings.api.CoreSettingsValidator
import io.github.texport.superkassa.jvm.settings.api.IllegalServerConfigurationException
import io.github.texport.superkassa.jvm.settings.impl.validation.*
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver

class DefaultCoreSettingsValidator(
    private val resolver: ErrorResolver = DefaultErrorResolver()
) : CoreSettingsValidator {

    override fun validateNotSQLite(jdbcUrl: String?) {
        if (jdbcUrl != null && jdbcUrl.lowercase().contains("jdbc:sqlite:")) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.SQLITE_NOT_ALLOWED).toString())
        }
    }

    override fun validateSettings(settings: CoreSettings) {
        validateSettings(settings, false)
    }

    override fun validateSettings(settings: CoreSettings, requireServerMode: Boolean) {
        if (requireServerMode && settings.mode != CoreMode.SERVER) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.SERVER_MODE_ONLY).toString())
        }

        StorageValidator.validateStorage(settings)

        if (settings.mode == CoreMode.SERVER) {
            StorageValidator.validateServerSpecifics(settings)
        }

        val proto = settings.ofdProtocolVersion
        if (proto.isBlank() || !isDigitsOnly(proto)) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.OFD_PROTOCOL_VERSION).toString())
        }

        if (settings.ofdTimeoutSeconds < MIN_OFD_TIMEOUT_SECONDS) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.OFD_TIMEOUT).toString())
        }
        if (settings.ofdReconnectIntervalSeconds < MIN_OFD_RECONNECT_INTERVAL_SECONDS) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.OFD_RECONNECT_INTERVAL).toString())
        }

        // Оба списка каналов проверяются одинаково строго. Верхнеуровневый
        // не проверял никто, и имя вне перечисления доходило до сборки узла:
        // адаптеров по нему не собиралось, доставки не было, а кассир получал
        // ответ об успешной отправке чека.
        DeliveryValidator.validateChannelNames(settings.deliveryChannels)

        val delivery = settings.delivery
        if (delivery != null) {
            DeliveryValidator.validateDeliveryChannels(delivery)
        }
    }

    override fun validateSettingsToStore(settings: CoreSettings, requireServerMode: Boolean) {
        validateSettings(settings, requireServerMode)
        DefaultUsersValidator.validateDefaultPins(settings)
    }

    override fun reviewStoredSettings(settings: CoreSettings) {
        DefaultUsersValidator.warnOnIdenticalDefaultPins(settings)
    }
}
