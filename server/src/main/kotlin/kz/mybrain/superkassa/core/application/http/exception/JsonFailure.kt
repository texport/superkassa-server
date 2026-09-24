package kz.mybrain.superkassa.core.application.http.exception

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException

/**
 * Разбор отказа при чтении тела запроса.
 *
 * kotlinx.serialization уже знает, какое поле и какое значение её не устроили.
 * Ответ «некорректный JSON» эту точность терял и отправлял интегратора искать
 * синтаксическую ошибку там, где её нет: тело разобрано, ошибка в одном поле.
 */
internal object JsonFailure {

    private val UNKNOWN_VALUE = Regex("does not contain element with name '([^']+)'")
    private val PATH = Regex("at path:? (\\S+)")

    /**
     * Код ошибки и трёхъязычное сообщение по причине отказа.
     *
     * Список пропущенных полей kotlinx.serialization отдаёт только
     * экспериментальным API; разбирать вместо него текст исключения —
     * хрупче: текст меняется от версии к версии.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun describe(cause: Throwable?): Pair<String, String> {
        val failure = generateSequence(cause) { it.cause }
            .filterIsInstance<SerializationException>()
            .firstOrNull() ?: return malformed()
        val text = failure.message.orEmpty()
        val unknown = UNKNOWN_VALUE.find(text)
        return when {
            failure is MissingFieldException -> missingFields(failure.missingFields)
            unknown != null -> unknownValue(unknown.groupValues[1], text)
            else -> malformed()
        }
    }

    private fun missingFields(fields: List<String>): Pair<String, String> {
        val listed = fields.joinToString(", ")
        return "MISSING_REQUIRED_FIELD" to (
            "[EN] Required field is missing: $listed / " +
                "[RU] Отсутствует обязательное поле: $listed / " +
                "[KK] Міндетті өріс жоқ: $listed"
            )
    }

    /**
     * Допустимые значения перечисления в исключении не приходят, поэтому
     * сообщение называет поле и отвергнутое значение — этого хватает,
     * чтобы найти опечатку, не читая исходники.
     */
    private fun unknownValue(value: String, text: String): Pair<String, String> {
        val field = PATH.find(text)?.groupValues?.get(1)?.removePrefix("$.").orEmpty()
        val where = if (field.isEmpty()) "" else " ($field)"
        return "INVALID_FIELD_VALUE" to (
            "[EN] Value '$value' is not allowed for field$where / " +
                "[RU] Значение '$value' недопустимо для поля$where / " +
                "[KK] '$value' мәні өріс үшін жарамсыз$where"
            )
    }

    private fun malformed(): Pair<String, String> = "INVALID_JSON" to (
        "[EN] Malformed JSON request or invalid format / " +
            "[RU] Некорректный запрос JSON или неверный формат / " +
            "[KK] Қате JSON сұранысы немесе жарамсыз формат"
        )
}
