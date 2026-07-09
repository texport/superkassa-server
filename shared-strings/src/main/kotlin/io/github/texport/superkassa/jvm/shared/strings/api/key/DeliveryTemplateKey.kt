package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи шаблонов сообщений для службы доставки чеков.
 *
 * Потокобезопасность: Перечисления (enums) являются потокобезопасными по своей природе.
 */
enum class DeliveryTemplateKey(override val code: String) : ErrorKey {
    /** Тема письма для отправки чека */
    EMAIL_SUBJECT("DELIVERY_TEMPLATE_EMAIL_SUBJECT"),

    /** Тело письма со ссылкой на чек */
    EMAIL_BODY_URL("DELIVERY_TEMPLATE_EMAIL_BODY_URL"),

    /** Тело письма с вложением чека */
    EMAIL_BODY_ATTACHMENT("DELIVERY_TEMPLATE_EMAIL_BODY_ATTACHMENT"),

    /** Текст SMS со ссылкой на чек */
    SMS_BODY_URL("DELIVERY_TEMPLATE_SMS_BODY_URL"),

    /** Текст SMS о готовности чека */
    SMS_BODY_READY("DELIVERY_TEMPLATE_SMS_BODY_READY")
}
