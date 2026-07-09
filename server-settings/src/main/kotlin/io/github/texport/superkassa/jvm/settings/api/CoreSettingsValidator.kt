package io.github.texport.superkassa.jvm.settings.api

import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings

/**
 * Интерфейс валидатора для проверки корректности конфигурации ядра (сервера или десктопа).
 * Гарантирует соответствие настроек выбранному режиму работы приложения.
 *
 * Потокобезопасность: Реализации ОБЯЗАНЫ быть полностью потокобезопасными и допускать параллельный вызов.
 */
interface CoreSettingsValidator {

    /**
     * Проверяет, что переданный JDBC URL не указывает на базу данных SQLite.
     * Используется для предотвращения некорректных конфигураций БД на сервере.
     *
     * @param jdbcUrl Строка URL подключения к БД или null.
     * @throws IllegalServerConfigurationException если база данных является SQLite.
     */
    fun validateNotSQLite(jdbcUrl: String?)

    /**
     * Выполняет валидацию настроек конфигурации по умолчанию (requireServerMode = false).
     *
     * @param settings Конфигурация ядра для проверки.
     * @throws IllegalServerConfigurationException если нарушены правила валидации.
     */
    fun validateSettings(settings: CoreSettings)

    /**
     * Выполняет валидацию настроек конфигурации, опционально требуя режим сервера.
     *
     * @param settings Конфигурация ядра для проверки.
     * @param requireServerMode Если true, выбрасывает исключение, если приложение работает не в режиме CoreMode.SERVER.
     * @throws IllegalServerConfigurationException если нарушены правила валидации.
     */
    fun validateSettings(settings: CoreSettings, requireServerMode: Boolean)

    companion object {
        private val resolver = DefaultErrorResolver()

        /**
         * Локализованное сообщение об ошибке при недопустимом использовании SQLite.
         */
        val SQLITE_NOT_ALLOWED_ERROR = resolver.resolve(SettingsErrorKey.SQLITE_NOT_ALLOWED).toString()

        /**
         * Локализованное сообщение об ошибке при нарушении требований серверного режима.
         */
        val SERVER_MODE_ONLY_ERROR = resolver.resolve(SettingsErrorKey.SERVER_MODE_ONLY).toString()
    }
}
