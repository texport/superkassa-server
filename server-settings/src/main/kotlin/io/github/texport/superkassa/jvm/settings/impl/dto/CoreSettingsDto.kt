package io.github.texport.superkassa.jvm.settings.impl.dto

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

@Serializable
data class CoreSettingsDto(
    val mode: CoreModeDto,
    val storage: StorageSettingsDto,
    val allowChanges: Boolean = false,
    val nodeId: String = "node-1",
    val ofdProtocolVersion: String = "203",
    val deliveryChannels: List<String> = listOf("PRINT"),
    val ofdTimeoutSeconds: Long = 30L,
    val ofdReconnectIntervalSeconds: Long = 60L,
    val delivery: DeliverySettingsDto? = null,
    val defaultAdminPin: String = "0000",
    val defaultAdminName: String = "Администратор",
    val defaultCashierPin: String = "1111",
    val defaultCashierName: String = "Кассир"
)
