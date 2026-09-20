package kz.mybrain.superkassa.core.application.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Пакет протокола: запрос кассы и ответ ОФД на него.
 *
 * Это всё, что известно о документе вне кассы, которая его пробила:
 * сервер приёма данных хранит документ именно так. Из пакета собирается
 * то же, что касса подавала рисовальщику, когда печатала документ
 * у себя, — потому и вид документа получается один.
 *
 * Ответа может не быть: документ, пробитый в разрыве связи, уходит
 * в ОФД позже. Такой пакет читается, а отметка о передаче у документа
 * остаётся неподтверждённой.
 */
internal class ProtocolPacket private constructor(
    /** Запрос кассы: сам документ со всеми его полями. */
    val request: JsonObject,
    /** Ответ ОФД: фискальный признак, ссылка на чек и итог обработки. */
    val response: JsonObject?
) {

    /** Команда протокола, которой подан документ. */
    val command: String = request.text(COMMAND) ?: response?.text(COMMAND).orEmpty()

    /** Служебный блок запроса: реквизиты кассы и налогоплательщика. */
    val service: JsonObject? = request.child("service")

    /**
     * Отметка о передаче документа в ОФД.
     *
     * Без ответа документ значится непереданным, а не принятым: отметка
     * «отправлен» на документе, которого ОФД не видел, обманывает и
     * владельца, и покупателя.
     */
    val ofdStatus: String = when (response?.child("result")?.number("resultCode")) {
        null -> if (response == null) PENDING else DELIVERED
        RESULT_OK -> DELIVERED
        else -> FAILED
    }

    companion object {

        /**
         * Разбирает пакет.
         *
         * @param body тело пакета: объект с полями `request` и `response`.
         * @return разобранный пакет либо `null`, если запроса в нём нет.
         */
        fun of(body: String): ProtocolPacket? {
            val parsed = runCatching { LENIENT.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val request = parsed.child("request") ?: return null
            return ProtocolPacket(request, parsed.child("response"))
        }

        private val LENIENT = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private const val COMMAND = "command"

        /** Код успешного ответа ОФД. */
        private const val RESULT_OK = 0L

        private const val DELIVERED = "DELIVERED"
        private const val PENDING = "PENDING"
        private const val FAILED = "FAILED"
    }
}
