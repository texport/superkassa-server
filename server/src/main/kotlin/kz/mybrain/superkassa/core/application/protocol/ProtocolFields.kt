package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.common.Money
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Чтение полей пакета CPCR.
 *
 * Пакет приходит тем же JSON, каким протокол описан схемой: имена полей —
 * lowerCamelCase, перечисления — именами, восьмибайтовые числа — строками.
 * Поэтому число читается и из числа, и из строки: сумма чека и сквозной
 * номер документа приходят строками, и строгое чтение теряло бы их молча.
 *
 * Отсутствующее поле — это `null`, а не ноль: в протоколе необязательное
 * поле не пишется вовсе, и ноль означал бы присланный ноль.
 */

/** Вложенный объект поля. */
internal fun JsonObject.child(name: String): JsonObject? = this[name] as? JsonObject

/** Список вложенных объектов поля; пустой, когда поля нет. */
internal fun JsonObject.children(name: String): List<JsonObject> =
    (this[name] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

/** Список строк поля; пустой, когда поля нет. */
internal fun JsonObject.texts(name: String): List<String> =
    (this[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

/** Строковое значение поля. */
internal fun JsonObject.text(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

/** Целое значение поля: протокол пишет восьмибайтовые числа строками. */
internal fun JsonObject.number(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

/** Признак поля; отсутствующий признак считается снятым. */
internal fun JsonObject.flag(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

/** Денежная сумма поля: тенге и тиыны отдельными числами, как в протоколе. */
internal fun JsonObject.money(name: String): Money? = child(name)?.let { sum ->
    Money(bills = sum.number("bills") ?: 0L, coins = (sum.number("coins") ?: 0L).toInt())
}

/** Денежная сумма поля в тиынах; нет поля — ноль. */
internal fun JsonObject.tiyn(name: String): Long = money(name)?.tiyn() ?: 0L

/**
 * Момент поля в миллисекундах.
 *
 * Протокол передаёт время разложенным по частям и без часового пояса:
 * это местное время кассы. Час Казахстана здесь тот же, каким касса
 * собирает свои пакеты, — иначе чек, нарисованный по пакету, показывал
 * бы не то время, что напечатала касса.
 */
internal fun JsonObject.moment(name: String): Long? {
    val moment = child(name) ?: return null
    val date = moment.child("date") ?: return null
    val time = moment.child("time")
    return ZonedDateTime.of(
        (date.number("year") ?: return null).toInt(),
        (date.number("month") ?: return null).toInt(),
        (date.number("day") ?: return null).toInt(),
        (time?.number("hour") ?: 0L).toInt(),
        (time?.number("minute") ?: 0L).toInt(),
        (time?.number("second") ?: 0L).toInt(),
        0,
        KKM_ZONE
    ).toInstant().toEpochMilli()
}

/** Часовой пояс кассы: протокол передаёт местное время без пояса. */
private val KKM_ZONE: ZoneId = ZoneId.of("Asia/Almaty")
