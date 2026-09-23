package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.presentation.api.SettingsApi
import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDomain
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto

/**
 * Настройки узла в виде, который отдаёт и принимает его API.
 *
 * Правила — когда правка разрешена и какие поля задаёт запуск — у ядра
 * ([SettingsApi]): те же, что у кассы приложения. Узел держал их копию,
 * и правило, поправленное в ядре, в узле оставалось прежним.
 */
class SettingsApplicationService(private val settings: SettingsApi) {

    /** Действующие настройки: сохранённая запись с ОФД и версией протокола запуска. */
    fun getSettings(): CoreSettingsDto = settings.getSettings().toDto()

    /**
     * Сохраняет настройки целиком; действуют они со следующего запуска.
     *
     * @throws io.github.texport.superkassa.core.domain.api.exception.SettingsFrozenException
     *   в режиме SERVER, при запрещённой правке и при смене версии протокола.
     */
    fun updateSettings(newSettingsDto: CoreSettingsDto): CoreSettingsDto =
        settings.updateSettings(newSettingsDto.toDomain()).toDto()
}
