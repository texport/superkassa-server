package io.github.texport.superkassa.jvm.settings.impl.mapper

import io.github.texport.superkassa.jvm.settings.impl.dto.*
import kz.mybrain.superkassa.core.domain.model.settings.EmailProviderSettings
import kz.mybrain.superkassa.core.domain.model.settings.SmsProviderSettings
import kz.mybrain.superkassa.core.domain.model.settings.TelegramProviderSettings
import kz.mybrain.superkassa.core.domain.model.settings.WhatsAppProviderSettings

fun EmailProviderSettings.toDto() = EmailProviderSettingsDto(
    host = host,
    port = port,
    user = user,
    password = password,
    from = from
)

fun EmailProviderSettingsDto.toDomain() = EmailProviderSettings(
    host = host,
    port = port,
    user = user,
    password = password,
    from = from
)

fun SmsProviderSettings.toDto() = SmsProviderSettingsDto(
    providerUrl = providerUrl,
    apiKey = apiKey
)

fun SmsProviderSettingsDto.toDomain() = SmsProviderSettings(
    providerUrl = providerUrl,
    apiKey = apiKey
)

fun TelegramProviderSettings.toDto() = TelegramProviderSettingsDto(
    botToken = botToken
)

fun TelegramProviderSettingsDto.toDomain() = TelegramProviderSettings(
    botToken = botToken
)

fun WhatsAppProviderSettings.toDto() = WhatsAppProviderSettingsDto(
    accessToken = accessToken,
    phoneNumberId = phoneNumberId
)

fun WhatsAppProviderSettingsDto.toDomain() = WhatsAppProviderSettings(
    accessToken = accessToken,
    phoneNumberId = phoneNumberId
)
