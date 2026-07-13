package io.github.texport.superkassa.jvm.settings.impl.mapper

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.jvm.settings.impl.dto.*

fun CoreMode.toDto() = when (this) {
    CoreMode.DESKTOP -> CoreModeDto.DESKTOP
    CoreMode.SERVER -> CoreModeDto.SERVER
}

fun CoreModeDto.toDomain() = when (this) {
    CoreModeDto.DESKTOP -> CoreMode.DESKTOP
    CoreModeDto.SERVER -> CoreMode.SERVER
}

fun StorageSettings.toDto() = StorageSettingsDto(
    engine = engine,
    jdbcUrl = jdbcUrl,
    user = user,
    password = password
)

fun StorageSettingsDto.toDomain() = StorageSettings(
    engine = engine,
    jdbcUrl = jdbcUrl,
    user = user,
    password = password
)

fun CoreSettings.toDto() = CoreSettingsDto(
    mode = mode.toDto(),
    storage = storage.toDto(),
    allowChanges = allowChanges,
    nodeId = nodeId,
    ofdProtocolVersion = ofdProtocolVersion,
    deliveryChannels = deliveryChannels,
    ofdTimeoutSeconds = ofdTimeoutSeconds,
    ofdReconnectIntervalSeconds = ofdReconnectIntervalSeconds,
    delivery = delivery?.toDto(),
    defaultAdminPin = defaultAdminPin,
    defaultAdminName = defaultAdminName,
    defaultCashierPin = defaultCashierPin,
    defaultCashierName = defaultCashierName
)

fun CoreSettingsDto.toDomain() = CoreSettings(
    mode = mode.toDomain(),
    storage = storage.toDomain(),
    allowChanges = allowChanges,
    nodeId = nodeId,
    ofdProtocolVersion = ofdProtocolVersion,
    deliveryChannels = deliveryChannels,
    ofdTimeoutSeconds = ofdTimeoutSeconds,
    ofdReconnectIntervalSeconds = ofdReconnectIntervalSeconds,
    delivery = delivery?.toDomain(),
    defaultAdminPin = defaultAdminPin,
    defaultAdminName = defaultAdminName,
    defaultCashierPin = defaultCashierPin,
    defaultCashierName = defaultCashierName
)
