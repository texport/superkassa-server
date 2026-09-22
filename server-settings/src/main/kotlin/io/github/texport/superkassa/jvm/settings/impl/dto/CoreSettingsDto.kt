package io.github.texport.superkassa.jvm.settings.impl.dto

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import kotlinx.serialization.Serializable

@Serializable
enum class CoreModeDto {
    DESKTOP,
    SERVER
}

@Serializable
data class StorageSettingsDto(
    val engine: String,
    val jdbcUrl: String,
    val user: String? = null,
    val password: String? = null
)

@Serializable
data class PrintConnectionSettingsDto(
    val type: String = "NETWORK",
    val host: String? = null,
    val port: Int? = 9100
)

@Serializable
data class PrintDeliverySettingsDto(
    val enabled: Boolean = true,
    val paperWidthMm: Int = 58,
    val connection: PrintConnectionSettingsDto? = null
)

@Serializable
data class DeliveryChannelSettingsDto(
    val channel: String,
    val enabled: Boolean = true,
    val payloadType: String = "DOCUMENT",
    val documentFormat: String = "PDF",
    val destination: String? = null
)

@Serializable
data class EmailProviderSettingsDto(
    val host: String = "localhost",
    val port: Int = 587,
    val user: String? = null,
    val password: String? = null,
    val from: String = "noreply@local"
)

@Serializable
data class SmsProviderSettingsDto(
    val providerUrl: String? = null,
    val apiKey: String? = null
)

@Serializable
data class TelegramProviderSettingsDto(
    val botToken: String? = null
)

@Serializable
data class WhatsAppProviderSettingsDto(
    val accessToken: String? = null,
    val phoneNumberId: String? = null
)

@Serializable
data class DeliverySettingsDto(
    val print: PrintDeliverySettingsDto? = null,
    val channels: List<DeliveryChannelSettingsDto> = emptyList(),
    val email: EmailProviderSettingsDto? = null,
    val sms: SmsProviderSettingsDto? = null,
    val telegram: TelegramProviderSettingsDto? = null,
    val whatsapp: WhatsAppProviderSettingsDto? = null
)

/**
 * Умолчания предметной области, прочитанные у ядра.
 *
 * Представление настроек узла не повторяет их числами и строками.
 * Отсутствующее в записи поле должно означать у узла ровно то же,
 * что у ядра, а два независимо объявленных умолчания рано или поздно
 * расходятся: так срок ожидания БФД оказался тридцатью секундами
 * в ядре и семью в узле, и одна и та же запись читалась по-разному.
 *
 * Режим и хранилище обязательны для экземпляра и здесь ни на что
 * не влияют: у него читаются только умолчания остальных полей.
 */
private val DOMAIN_DEFAULTS = CoreSettings(
    mode = CoreMode.DESKTOP,
    storage = StorageSettings(engine = "", jdbcUrl = "")
)

@Serializable
data class CoreSettingsDto(
    val mode: CoreModeDto,
    val storage: StorageSettingsDto,
    val allowChanges: Boolean = DOMAIN_DEFAULTS.allowChanges,
    val nodeId: String = DOMAIN_DEFAULTS.nodeId,
    val ofdProtocolVersion: String = DOMAIN_DEFAULTS.ofdProtocolVersion,
    val deliveryChannels: List<String> = DOMAIN_DEFAULTS.deliveryChannels,
    val ofdTimeoutSeconds: Long = DOMAIN_DEFAULTS.ofdTimeoutSeconds,
    val ofdReconnectIntervalSeconds: Long = DOMAIN_DEFAULTS.ofdReconnectIntervalSeconds,
    val delivery: DeliverySettingsDto? = null
)
