package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи ошибок печатных адаптеров узла.
 *
 * Отказы каналов SMS, Telegram, WhatsApp и почты говорит ядро.
 *
 * Потокобезопасность: Перечисления (enums) являются потокобезопасными по своей природе.
 */
enum class DeliveryErrorKey(override val code: String) : ErrorKey {
    /** Принтер не найден в операционной системе */
    PRINTER_NOT_FOUND("DELIVERY_PRINTER_NOT_FOUND"),

    /** Отсутствуют данные для печати */
    PRINT_PAYLOAD_MISSING("DELIVERY_PRINT_PAYLOAD_MISSING"),

    /** Ошибка печати */
    PRINT_FAILED("DELIVERY_PRINT_FAILED"),

    /** Ошибка локальной печати ОС */
    LOCAL_PRINT_FAILED("DELIVERY_LOCAL_PRINT_FAILED")
}
