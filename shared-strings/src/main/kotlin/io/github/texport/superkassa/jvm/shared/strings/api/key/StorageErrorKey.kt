package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи ошибок для модуля базы данных и хранилища (storage-jdbc).
 *
 * Потокобезопасность: Перечисления (enums) являются потокобезопасными по своей природе.
 */
enum class StorageErrorKey(override val code: String) : ErrorKey {
    /** Общая ошибка базы данных */
    DATABASE_ERROR("DATABASE_ERROR"),

    /** Неверная роль пользователя в БД */
    USER_ROLE_INVALID("USER_ROLE_INVALID"),

    /** Неверный статус смены в БД */
    SHIFT_STATUS_INVALID("SHIFT_STATUS_INVALID"),

    /** Неверный формат Base64 в БД */
    INVALID_BASE_64_FORMAT("INVALID_BASE_64_FORMAT"),

    /** Неверный режим налогообложения в БД */
    INVALID_TAX_REGIME("INVALID_TAX_REGIME"),

    /** Неверная группа НДС в БД */
    INVALID_VAT_GROUP("INVALID_VAT_GROUP")
}
