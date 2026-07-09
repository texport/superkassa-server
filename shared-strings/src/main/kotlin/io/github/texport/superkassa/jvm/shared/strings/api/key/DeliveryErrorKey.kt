package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи ошибок для службы отправки чеков (SMS, Email, Telegram, WhatsApp, Print).
 *
 * Потокобезопасность: Перечисления (enums) являются потокобезопасными по своей природе.
 */
enum class DeliveryErrorKey(override val code: String) : ErrorKey {
    /** Требуется номер телефона получателя SMS */
    SMS_DESTINATION_REQUIRED("DELIVERY_SMS_DESTINATION_REQUIRED"),

    /** Не настроен URL-адрес провайдера SMS */
    SMS_PROVIDER_NOT_CONFIGURED("DELIVERY_SMS_PROVIDER_NOT_CONFIGURED"),

    /** Требуется адрес электронной почты */
    EMAIL_DESTINATION_REQUIRED("DELIVERY_EMAIL_DESTINATION_REQUIRED"),

    /** Отсутствуют данные чека */
    EMAIL_PAYLOAD_MISSING("DELIVERY_EMAIL_PAYLOAD_MISSING"),

    /** Ошибка отправки почты */
    EMAIL_SEND_FAILED("DELIVERY_EMAIL_SEND_FAILED"),

    /** Принтер не найден в операционной системе */
    PRINTER_NOT_FOUND("DELIVERY_PRINTER_NOT_FOUND"),

    /** Отсутствуют данные для печати */
    PRINT_PAYLOAD_MISSING("DELIVERY_PRINT_PAYLOAD_MISSING"),

    /** Ошибка печати */
    PRINT_FAILED("DELIVERY_PRINT_FAILED"),

    /** Ошибка локальной печати ОС */
    LOCAL_PRINT_FAILED("DELIVERY_LOCAL_PRINT_FAILED"),

    /** Требуется ID чата Telegram */
    TELEGRAM_CHAT_ID_REQUIRED("DELIVERY_TELEGRAM_CHAT_ID_REQUIRED"),

    /** Требуется номер телефона WhatsApp */
    WHATSAPP_PHONE_REQUIRED("DELIVERY_WHATSAPP_PHONE_REQUIRED"),

    /** Ошибка отправки через HTTP API с кодом состояния */
    HTTP_DELIVERY_FAILED("DELIVERY_HTTP_DELIVERY_FAILED"),

    /** Общая HTTP ошибка отправки */
    HTTP_DELIVERY_ERROR("DELIVERY_HTTP_DELIVERY_ERROR")
}
