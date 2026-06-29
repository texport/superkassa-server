package io.github.texport.superkassa.jvm.settings.validation

import io.github.texport.superkassa.jvm.settings.IllegalServerConfigurationException
import kz.mybrain.superkassa.core.domain.model.settings.DeliverySettings

internal object DeliveryValidator {

    fun validateDeliveryChannels(delivery: DeliverySettings) {
        for (ch in delivery.channels) {
            if (ch.enabled) {
                val channel = ch.channel
                if (channel.isBlank()) {
                    throw IllegalServerConfigurationException(ValidationErrors.CHANNEL_NAME_BLANK)
                }
                val upperChannel = channel.uppercase()
                if (upperChannel !in listOf("PRINT", "EMAIL", "SMS", "TELEGRAM", "WHATSAPP")) {
                    throw IllegalServerConfigurationException(ValidationErrors.unknownChannel(channel))
                }
                if (ch.payloadType.isBlank()) {
                    throw IllegalServerConfigurationException(ValidationErrors.payloadTypeBlank(upperChannel))
                }
                if (ch.documentFormat.isBlank()) {
                    throw IllegalServerConfigurationException(ValidationErrors.documentFormatBlank(upperChannel))
                }
                val dest = ch.destination
                if (dest.isNullOrBlank()) {
                    throw IllegalServerConfigurationException(ValidationErrors.destinationBlank(upperChannel))
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
                    throw IllegalServerConfigurationException(ValidationErrors.EMAIL_DESTINATION_INVALID)
                }
            }
            "SMS", "WHATSAPP" -> {
                if (!isValidPhoneNumber(destination)) {
                    throw IllegalServerConfigurationException(ValidationErrors.phoneDestinationInvalid(channel))
                }
            }
            "TELEGRAM" -> {
                if (!isValidTelegramChatId(destination)) {
                    throw IllegalServerConfigurationException(ValidationErrors.TELEGRAM_DESTINATION_INVALID)
                }
            }
        }
    }

    private fun validateChannel(channel: String, delivery: DeliverySettings) {
        when (channel) {
            "PRINT" -> PrintValidator.validate(delivery)
            "EMAIL" -> EmailValidator.validate(delivery)
            "SMS" -> SmsValidator.validate(delivery)
            "TELEGRAM" -> TelegramValidator.validate(delivery)
            "WHATSAPP" -> WhatsAppValidator.validate(delivery)
        }
    }
}

internal object PrintValidator {
    fun validate(delivery: DeliverySettings) {
        val print = delivery.print
            ?: throw IllegalServerConfigurationException(ValidationErrors.PRINT_CONFIG_MISSING)
        if (print.paperWidthMm <= 0) {
            throw IllegalServerConfigurationException(ValidationErrors.PRINT_PAPER_WIDTH_INVALID)
        }
        val conn = print.connection
        if (conn == null || conn.host.isNullOrBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.PRINT_HOST_INVALID)
        }
        if (!isValidPort(conn.port)) {
            throw IllegalServerConfigurationException(ValidationErrors.PRINT_PORT_INVALID)
        }
    }
}

internal object EmailValidator {
    fun validate(delivery: DeliverySettings) {
        val email = delivery.email
            ?: throw IllegalServerConfigurationException(ValidationErrors.EMAIL_CONFIG_MISSING)
        val isInvalid = email.host.isBlank() ||
            !isValidPort(email.port) ||
            email.from.isBlank() ||
            !isValidEmail(email.from) ||
            email.user.isNullOrBlank() ||
            email.password.isNullOrBlank()
        if (isInvalid) {
            throw IllegalServerConfigurationException(ValidationErrors.EMAIL_PARAMS_INVALID)
        }
    }
}

internal object SmsValidator {
    fun validate(delivery: DeliverySettings) {
        val sms = delivery.sms
            ?: throw IllegalServerConfigurationException(ValidationErrors.SMS_CONFIG_MISSING)
        val url = sms.providerUrl
        if (url.isNullOrBlank() || !isValidUrl(url)) {
            throw IllegalServerConfigurationException(ValidationErrors.SMS_PROVIDER_URL_INVALID)
        }
        if (sms.apiKey.isNullOrBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.SMS_API_KEY_BLANK)
        }
    }
}

internal object TelegramValidator {
    fun validate(delivery: DeliverySettings) {
        val tg = delivery.telegram
            ?: throw IllegalServerConfigurationException(ValidationErrors.TELEGRAM_CONFIG_MISSING)
        val token = tg.botToken
        if (token.isNullOrBlank() || !isValidTelegramBotToken(token)) {
            throw IllegalServerConfigurationException(ValidationErrors.TELEGRAM_BOT_TOKEN_INVALID)
        }
    }
}

internal object WhatsAppValidator {
    fun validate(delivery: DeliverySettings) {
        val wa = delivery.whatsapp
            ?: throw IllegalServerConfigurationException(ValidationErrors.WHATSAPP_CONFIG_MISSING)
        if (wa.accessToken.isNullOrBlank()) {
            throw IllegalServerConfigurationException(ValidationErrors.WHATSAPP_ACCESS_TOKEN_BLANK)
        }
        val phoneId = wa.phoneNumberId
        if (phoneId.isNullOrBlank() || !isDigitsOnly(phoneId)) {
            throw IllegalServerConfigurationException(ValidationErrors.WHATSAPP_PHONE_ID_INVALID)
        }
    }
}
