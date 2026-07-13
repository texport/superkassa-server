package io.github.texport.superkassa.jvm.settings.impl.mapper

import io.github.texport.superkassa.core.domain.api.model.settings.DeliveryChannelSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.jvm.settings.impl.dto.*

fun DeliveryChannelSettings.toDto() = DeliveryChannelSettingsDto(
    channel = channel,
    enabled = enabled,
    payloadType = payloadType,
    documentFormat = documentFormat,
    destination = destination
)

fun DeliveryChannelSettingsDto.toDomain() = DeliveryChannelSettings(
    channel = channel,
    enabled = enabled,
    payloadType = payloadType,
    documentFormat = documentFormat,
    destination = destination
)

fun DeliverySettings.toDto() = DeliverySettingsDto(
    print = print?.toDto(),
    channels = channels.map { it.toDto() },
    email = email?.toDto(),
    sms = sms?.toDto(),
    telegram = telegram?.toDto(),
    whatsapp = whatsapp?.toDto()
)

fun DeliverySettingsDto.toDomain() = DeliverySettings(
    print = print?.toDomain(),
    channels = channels.map { it.toDomain() },
    email = email?.toDomain(),
    sms = sms?.toDomain(),
    telegram = telegram?.toDomain(),
    whatsapp = whatsapp?.toDomain()
)
