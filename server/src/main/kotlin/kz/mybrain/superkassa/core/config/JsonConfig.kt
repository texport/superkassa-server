package kz.mybrain.superkassa.core.config

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Как узел кодирует ответы, объявленные `@Serializable`.
 *
 * По умолчанию kotlinx выбрасывает из ответа всё, что равно значению
 * по умолчанию: `false`, ноль и пустую строку. Клиент такого поля не
 * видит вовсе и не может отличить «касса не в режиме программирования»
 * от «узел про это не сказал». Настройки печатной формы, где по умолчанию
 * почти всё, доезжали до кассы пустым объектом `{}`.
 *
 * Поэтому значения по умолчанию кодируются: объявленное в ответе поле
 * обязано в ответе быть. Пустые же поля остаются опущенными — `null`
 * означает «нет значения», и писать его в каждый ответ незачем.
 */
@Configuration
class JsonConfig {

    @Bean
    fun kotlinxJson(): Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
}
