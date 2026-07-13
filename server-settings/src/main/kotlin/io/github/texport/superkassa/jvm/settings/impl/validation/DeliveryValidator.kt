package io.github.texport.superkassa.jvm.settings.impl.validation

import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.jvm.settings.api.IllegalServerConfigurationException
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver

internal object DeliveryValidator {

    private val resolver = DefaultErrorResolver()

    fun validateDeliveryChannels(delivery: DeliverySettings) {
        for (ch in delivery.channels) {
            if (ch.enabled) {
                val channel = ch.channel
                if (channel.isBlank()) {
                    throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.CHANNEL_NAME_BLANK).toString())
                }
                val upperChannel = channel.uppercase()
                if (upperChannel !in listOf("PRINT", "EMAIL", "SMS", "TELEGRAM", "WHATSAPP")) {
                    throw IllegalServerConfigurationException(
                        resolver.resolve(SettingsErrorKey.UNKNOWN_DELIVERY_CHANNEL).formatArgs(channel).toString()
                    )
                }
                if (ch.payloadType.isBlank()) {
                    throw IllegalServerConfigurationException(
                        resolver.resolve(SettingsErrorKey.PAYLOAD_TYPE_BLANK).formatArgs(upperChannel).toString()
                    )
                }
                if (ch.documentFormat.isBlank()) {
                    throw IllegalServerConfigurationException(
                        resolver.resolve(SettingsErrorKey.DOCUMENT_FORMAT_BLANK).formatArgs(upperChannel).toString()
                    )
                }
                val dest = ch.destination
                if (dest.isNullOrBlank()) {
                    throw IllegalServerConfigurationException(
                        resolver.resolve(SettingsErrorKey.DESTINATION_BLANK).formatArgs(upperChannel).toString()
                    )
                }
                validateDestinationFormat(upperChannel, dest)
                validateChannel(upperChannel, delivery)
            }
        }
    }

    private fun validateDestinationFormat(channel: String, destination: String) {
        when (channel) {
            "EMAIL" -> {
                if (!isValidEmail(destination)) {
                    throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.EMAIL_DESTINATION_INVALID).toString())
                }
            }
            "SMS", "WHATSAPP" -> {
                if (!isValidPhoneNumber(destination)) {
                    throw IllegalServerConfigurationException(
                        resolver.resolve(SettingsErrorKey.PHONE_DESTINATION_INVALID).formatArgs(channel).toString()
                    )
                }
            }
            "TELEGRAM" -> {
                if (!isValidTelegramChatId(destination)) {
                    throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.TELEGRAM_DESTINATION_INVALID).toString())
                }
            }
        }
    }

    private fun validateChannel(channel: String, delivery: DeliverySettings) {
        when (channel) {
            "PRINT" -> PrintValidator.validate(delivery, resolver)
            "EMAIL" -> EmailValidator.validate(delivery, resolver)
            "SMS" -> SmsValidator.validate(delivery, resolver)
            "TELEGRAM" -> TelegramValidator.validate(delivery, resolver)
            "WHATSAPP" -> WhatsAppValidator.validate(delivery, resolver)
        }
    }
}

internal object PrintValidator {
    fun validate(delivery: DeliverySettings, resolver: DefaultErrorResolver) {
        val print = delivery.print
            ?: throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.PRINT_CONFIG_MISSING).toString())
        if (print.paperWidthMm <= 0) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.PRINT_PAPER_WIDTH_INVALID).toString())
        }
        val conn = print.connection
        if (conn == null || conn.host.isNullOrBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.PRINT_HOST_INVALID).toString())
        }
        if (!isValidPort(conn.port)) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.PRINT_PORT_INVALID).toString())
        }
    }
}

internal object EmailValidator {
    fun validate(delivery: DeliverySettings, resolver: DefaultErrorResolver) {
        val email = delivery.email
            ?: throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.EMAIL_CONFIG_MISSING).toString())
        val isInvalid = email.host.isBlank() ||
            !isValidPort(email.port) ||
            email.from.isBlank() ||
            !isValidEmail(email.from) ||
            email.user.isNullOrBlank() ||
            email.password.isNullOrBlank()
        if (isInvalid) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.EMAIL_PARAMS_INVALID).toString())
        }
    }
}

internal object SmsValidator {
    fun validate(delivery: DeliverySettings, resolver: DefaultErrorResolver) {
        val sms = delivery.sms
            ?: throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.SMS_CONFIG_MISSING).toString())
        val url = sms.providerUrl
        if (url.isNullOrBlank() || !isValidUrl(url)) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.SMS_PROVIDER_URL_INVALID).toString())
        }
        if (sms.apiKey.isNullOrBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.SMS_API_KEY_BLANK).toString())
        }
    }
}

internal object TelegramValidator {
    fun validate(delivery: DeliverySettings, resolver: DefaultErrorResolver) {
        val tg = delivery.telegram
            ?: throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.TELEGRAM_CONFIG_MISSING).toString())
        val token = tg.botToken
        if (token.isNullOrBlank() || !isValidTelegramBotToken(token)) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.TELEGRAM_BOT_TOKEN_INVALID).toString())
        }
    }
}

internal object WhatsAppValidator {
    fun validate(delivery: DeliverySettings, resolver: DefaultErrorResolver) {
        val wa = delivery.whatsapp
            ?: throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.WHATSAPP_CONFIG_MISSING).toString())
        if (wa.accessToken.isNullOrBlank()) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.WHATSAPP_ACCESS_TOKEN_BLANK).toString())
        }
        val phoneId = wa.phoneNumberId
        if (phoneId.isNullOrBlank() || !isDigitsOnly(phoneId)) {
            throw IllegalServerConfigurationException(resolver.resolve(SettingsErrorKey.WHATSAPP_PHONE_ID_INVALID).toString())
        }
    }
}
