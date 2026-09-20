package kz.mybrain.superkassa.core.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kz.mybrain.superkassa.core.application.http.exception.JsonFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор проверяется на настоящих отказах kotlinx.serialization, а не на
 * выдуманных исключениях: сообщение библиотеки — единственный источник
 * названия поля, и подделка сделала бы тест бессмысленным.
 */
class JsonFailureTest {

    @Serializable
    private enum class Regime { NO_VAT, VAT_PAYER }

    @Serializable
    private data class Settings(val regime: Regime, val title: String)

    private fun failureOf(payload: String): Throwable =
        runCatching { Json.decodeFromString<Settings>(payload) }.exceptionOrNull()!!

    @Test
    fun `unknown enum value names the field and the value`() {
        val (code, message) = JsonFailure.describe(
            failureOf("""{"regime":"GENERAL","title":"x"}""")
        )

        assertEquals("INVALID_FIELD_VALUE", code)
        assertTrue(message.contains("GENERAL"), message)
        assertTrue(message.contains("regime"), message)
    }

    @Test
    fun `missing required field is named`() {
        val (code, message) = JsonFailure.describe(failureOf("""{"regime":"NO_VAT"}"""))

        assertEquals("MISSING_REQUIRED_FIELD", code)
        assertTrue(message.contains("title"), message)
    }

    @Test
    fun `broken syntax stays a malformed json`() {
        val (code, message) = JsonFailure.describe(failureOf("{ не json"))

        assertEquals("INVALID_JSON", code)
        assertTrue(message.contains("Malformed JSON request"), message)
    }

    @Test
    fun `cause without serialization failure stays a malformed json`() {
        val (code, _) = JsonFailure.describe(IllegalStateException("нет причины"))

        assertEquals("INVALID_JSON", code)
    }

    @Test
    fun `absent cause stays a malformed json`() {
        val (code, _) = JsonFailure.describe(null)

        assertEquals("INVALID_JSON", code)
    }

    @Test
    fun `serialization failure without a message stays a malformed json`() {
        val (code, _) = JsonFailure.describe(kotlinx.serialization.SerializationException())

        assertEquals("INVALID_JSON", code)
    }

    @Test
    fun `unknown value without a path is still reported`() {
        val (code, message) = JsonFailure.describe(
            kotlinx.serialization.SerializationException(
                "Regime does not contain element with name 'GENERAL'"
            )
        )

        assertEquals("INVALID_FIELD_VALUE", code)
        assertTrue(message.contains("GENERAL"), message)
    }
}
