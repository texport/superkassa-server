package kz.mybrain.superkassa.core.application.info

import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import org.springframework.stereotype.Service

@Service
class SystemInfoApplicationService(
    private val coreSettings: CoreSettingsDto,
    private val storage: StoragePort
) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun getInfo(appVersion: String): Map<String, Any> {
        val kkmCount = try {
            storage.countKkms(state = null, search = null)
        } catch (e: Exception) {
            0
        }

        return mapOf(
            "name" to "Superkassa Core",
            "version" to appVersion,
            "mode" to coreSettings.mode.name,
            "nodeId" to coreSettings.nodeId,
            "ofdProtocolVersion" to coreSettings.ofdProtocolVersion,
            "storage" to mapOf(
                "engine" to coreSettings.storage.engine,
                "jdbcUrl" to coreSettings.storage.jdbcUrl.replace(Regex(":.*@"), ":***@") // Скрываем пароль
            ),
            "statistics" to mapOf(
                "registeredKkms" to kkmCount
            ),
            "features" to mapOf(
                "allowSettingsChanges" to coreSettings.allowChanges,
                "deliveryChannels" to coreSettings.deliveryChannels,
                "ofdTimeoutSeconds" to coreSettings.ofdTimeoutSeconds,
                "ofdReconnectIntervalSeconds" to coreSettings.ofdReconnectIntervalSeconds
            )
        )
    }
}
