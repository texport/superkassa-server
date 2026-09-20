package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import tools.jackson.databind.json.JsonMapper

/**
 * Суммы едут по HTTP числом и не проходят через `Double`.
 *
 * Пока `amount` был `Double`, «5000.55» превращалось в 5000.549999999999,
 * и тиын терялся до того, как запрос доходил до кассы.
 */
class DecimalJacksonConfigTest {

    private val mapper = JsonMapper.builder()
        .addModule(DecimalJacksonConfig().decimalModule())
        .build()

    @Test
    fun readsDecimalFromJsonNumber() {
        val value = mapper.readValue("""{"amount": 5000.55}""", Holder::class.java)

        assertEquals(Decimal.parse("5000.55"), value.amount)
        assertEquals(500055L, value.amount.scaled(2))
    }

    @Test
    fun readsDecimalFromJsonString() {
        assertEquals(
            Decimal.parse("0.125"),
            mapper.readValue("""{"amount": "0.125"}""", Holder::class.java).amount
        )
    }

    @Test
    fun writesDecimalAsJsonNumber() {
        val json = mapper.writeValueAsString(Holder(Decimal.parse("5000.55")))

        assertEquals("""{"amount":5000.55}""", json)
        assertEquals("""{"amount":12}""", mapper.writeValueAsString(Holder(Decimal.parse("12"))))
    }

    data class Holder(val amount: Decimal)
}
