package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule

/**
 * [Decimal] в HTTP — числом, как его прислала касса.
 *
 * Суммы приходят десятичной записью: `{"amount": 5000.55}`. Разбор идёт из
 * самой записи, минуя `Double`, — иначе тиын теряется ещё до домена.
 * Наружу число уходит той же записью, без хвоста `.0`.
 */
@Configuration
class DecimalJacksonConfig {

    @Bean
    fun decimalModule(): SimpleModule = SimpleModule("decimal")
        .addSerializer(Decimal::class.java, DecimalJsonSerializer)
        .addDeserializer(Decimal::class.java, DecimalJsonDeserializer)
}

private object DecimalJsonSerializer : ValueSerializer<Decimal>() {
    override fun serialize(value: Decimal, generator: JsonGenerator, context: SerializationContext) {
        generator.writeNumber(value.unscaled.toBigDecimal().movePointLeft(value.scale))
    }
}

private object DecimalJsonDeserializer : ValueDeserializer<Decimal>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Decimal =
        Decimal.parse(parser.string)
}
