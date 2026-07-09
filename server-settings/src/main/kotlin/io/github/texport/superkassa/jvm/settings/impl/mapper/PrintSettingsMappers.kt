package io.github.texport.superkassa.jvm.settings.impl.mapper

import io.github.texport.superkassa.jvm.settings.impl.dto.*
import kz.mybrain.superkassa.core.domain.model.settings.PrintConnectionSettings
import kz.mybrain.superkassa.core.domain.model.settings.PrintDeliverySettings

fun PrintConnectionSettings.toDto() = PrintConnectionSettingsDto(
    type = type,
    host = host,
    port = port
)

fun PrintConnectionSettingsDto.toDomain() = PrintConnectionSettings(
    type = type,
    host = host,
    port = port
)

fun PrintDeliverySettings.toDto() = PrintDeliverySettingsDto(
    enabled = enabled,
    paperWidthMm = paperWidthMm,
    connection = connection?.toDto()
)

fun PrintDeliverySettingsDto.toDomain() = PrintDeliverySettings(
    enabled = enabled,
    paperWidthMm = paperWidthMm,
    connection = connection?.toDomain()
)
