package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Печатные адаптеры узла. Каналы SMS, Telegram, WhatsApp и почты — ядра,
 * и проверяются там.
 */
class DeliveryAdaptersTest {
    @Test
    fun `jps printer returns error when payload is missing`() {
        val adapter = JpsPrintDeliveryAdapter("NonExistentPrinter")
        val request = DeliveryRequest(
            cashboxId = "cashbox-1",
            documentId = "DOC-123",
            channel = DeliveryChannel.PRINT,
            destination = "local",
            payloadBytes = null,
            payloadUrl = "https://example.com"
        )
        val result = adapter.send(request)
        assertFalse(result.ok)
        val msg = result.message ?: ""
        assertTrue(
            msg.contains("No print payload") || msg.contains("Отсутствуют данные для печати") || msg.contains("Басып шығаруға арналған деректер жоқ")
        )
    }

    @Test
    fun `jps printer returns printer not found error for invalid printer name`() {
        val adapter = JpsPrintDeliveryAdapter("NonExistentPrinter-XYZ-12345")
        val request = DeliveryRequest(
            cashboxId = "cashbox-1",
            documentId = "DOC-123",
            channel = DeliveryChannel.PRINT,
            destination = "local",
            payloadBytes = byteArrayOf(1, 2, 3),
            payloadUrl = null
        )
        val result = adapter.send(request)
        assertFalse(result.ok)
        val msg = result.message ?: ""
        assertTrue(
            msg.contains("not found in the OS") || msg.contains("не найден в операционной системе") || msg.contains("табылмады")
        )
    }
}
