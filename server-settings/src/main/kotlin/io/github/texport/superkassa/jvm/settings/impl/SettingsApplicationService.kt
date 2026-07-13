package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDomain
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto

class SettingsApplicationService(
    private val settingsRepository: CoreSettingsRepositoryPort,
    private val coreSettings: CoreSettings,
    private val updateSettingsUseCase: UpdateSettingsUseCase
) {
    fun getSettings(): CoreSettingsDto {
        return settingsRepository.loadOrCreate(coreSettings).toDto()
    }

    fun updateSettings(newSettingsDto: CoreSettingsDto): CoreSettingsDto {
        val domainSettings = newSettingsDto.toDomain()
        val updatedDomain = updateSettingsUseCase.execute(domainSettings)
        return updatedDomain.toDto()
    }
}
