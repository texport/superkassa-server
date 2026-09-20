package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ответ узла обязан нести объявленные в нём поля.
 *
 * kotlinx по умолчанию выбрасывает всё, что равно значению по умолчанию,
 * и клиент переставал отличать `false` от «узел про это не сказал».
 * Настройки печатной формы уезжали к кассе пустым объектом `{}`.
 */
class JsonConfigTest {

    private val json = JsonConfig().kotlinxJson()

    @Test
    fun `ложь и ноль остаются в ответе`() {
        val encoded = json.encodeToString(
            KkmResponse(kkmId = "kkm-1", createdAt = 1, updatedAt = 2, mode = "REGISTRATION", state = "ACTIVE")
        )

        assertTrue(encoded.contains("\"autoCloseShift\":false"), encoded)
        assertTrue(encoded.contains("\"isShiftOpen\":false"), encoded)
        assertTrue(encoded.contains("\"offlineQueueCount\":0"), encoded)
    }

    @Test
    fun `пустые поля в ответ не пишутся`() {
        val encoded = json.encodeToString(
            KkmResponse(kkmId = "kkm-1", createdAt = 1, updatedAt = 2, mode = "REGISTRATION", state = "ACTIVE")
        )

        assertFalse(encoded.contains("null"), encoded)
    }
}
