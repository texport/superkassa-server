package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи ошибок для веб-слоя (маршрутизация, параметры, общие системные сбои).
 */
enum class WebErrorKey(override val code: String) : ErrorKey {
    /** Метод запроса не поддерживается */
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED"),

    /** Отсутствует обязательный параметр запроса */
    MISSING_PARAMETER("MISSING_PARAMETER"),

    /** Неподдерживаемый тип содержимого (Media Type) */
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE"),

    /** Запрошенный ресурс не найден */
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND"),

    /** Внутренняя ошибка сервера */
    INTERNAL_ERROR("INTERNAL_ERROR"),

    /** Критическая системная ошибка */
    CRITICAL_ERROR("CRITICAL_ERROR")
}
