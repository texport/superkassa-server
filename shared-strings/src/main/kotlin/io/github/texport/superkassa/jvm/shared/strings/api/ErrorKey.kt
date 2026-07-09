package io.github.texport.superkassa.jvm.shared.strings.api

/**
 * Представляет уникальный локализованный идентификатор ошибки.
 * Используется [ErrorResolver] для поиска конфигураций перевода.
 *
 * Потокобезопасность: Реализации должны быть неизменяемыми и потокобезопасными.
 */
interface ErrorKey {
    /**
     * Уникальный строковый код ошибки (например, "SETTINGS_SQLITE_NOT_ALLOWED").
     */
    val code: String
}
