package io.github.texport.superkassa.jvm.settings.impl.mapper

import io.github.texport.superkassa.core.domain.api.model.settings.EmailProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.SmsProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.TelegramProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.WhatsAppProviderSettings
import io.github.texport.superkassa.jvm.settings.impl.dto.*

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
