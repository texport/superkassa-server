package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.domain.api.exception.SettingsFrozenException
import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver

/**
 * Юзкейс обновления системных настроек Superkassa.
 * Выполняет бизнес-валидацию возможности изменения конфигурации,
 * проверяет режим работы (запрещает изменения в SERVER-режиме) и сохраняет настройки.
 *
 * @property settingsRepository репозиторий для сохранения настроек.
 * @property currentSettings текущие настройки системы.
 * @property errorResolver сервис получения локализованных сообщений.
 */
class UpdateSettingsUseCase(
    private val settingsRepository: CoreSettingsRepositoryPort,
    private val currentSettings: CoreSettings,
    private val errorResolver: ErrorResolver = DefaultErrorResolver()
) {
    /**
     * Выполняет валидацию и сохранение новых настроек.
     *
     * @param newSettings новые настройки для сохранения.
     * @return сохраненные настройки [CoreSettings].
     * @throws SettingsFrozenException если изменения запрещены.
     */
    fun execute(newSettings: CoreSettings): CoreSettings {
        if (currentSettings.mode == CoreMode.SERVER) {
            val msg = errorResolver.resolve(SettingsErrorKey.SETTINGS_FROZEN_SERVER_MODE)
            throw SettingsFrozenException("RU: ${msg.ru} | KK: ${msg.kk} | EN: ${msg.en}")
        }
        if (!currentSettings.allowChanges) {
            val msg = errorResolver.resolve(SettingsErrorKey.SETTINGS_FROZEN_DISALLOWED)
            throw SettingsFrozenException("RU: ${msg.ru} | KK: ${msg.kk} | EN: ${msg.en}")
        }
        settingsRepository.save(newSettings)
        return newSettings
    }
}
