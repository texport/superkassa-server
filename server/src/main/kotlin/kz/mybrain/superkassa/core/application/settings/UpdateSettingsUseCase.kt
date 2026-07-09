package kz.mybrain.superkassa.core.application.settings

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import kz.mybrain.superkassa.core.domain.exception.SettingsFrozenException
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.port.CoreSettingsRepositoryPort

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
