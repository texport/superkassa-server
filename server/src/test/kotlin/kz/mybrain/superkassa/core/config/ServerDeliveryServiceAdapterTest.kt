package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest
import io.github.texport.superkassa.delivery.api.createDeliveryServiceApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Отказ доставки узла несёт код и повторяемость: по ним фон решает,
 * повторять ли задачу, а журнал показывает кассиру причину.
 */
class ServerDeliveryServiceAdapterTest {
    private val port = ServerDeliveryServiceAdapter(createDeliveryServiceApi(emptyList()))

    @Test
    fun `канал, которого узел не знает, отказывает окончательно`() {
        val outcome = port.send(request("FAX"))

        assertEquals("DELIVERY_CHANNEL_UNKNOWN" to false, outcome.failure?.code to outcome.retryable)
    }

    @Test
    fun `канал без адаптера отказывает кодом сервиса каналов, а не общим недоставлено`() {
        val outcome = port.send(request("sms"))

        assertEquals(false, outcome.delivered)
        assertEquals(true, outcome.failure?.code?.startsWith("DELIVERY_"), outcome.failure?.code)
        assertEquals(false, outcome.failure?.code == "DELIVERY_FAILED", outcome.failure?.code)
    }

    private fun request(channel: String) = DeliveryRequest(
        kkmId = "kkm-1",
        documentId = "doc-1",
        channel = channel,
        destination = "+77770001122",
        payloadType = "HTML",
        payloadBytes = byteArrayOf(1)
    )
}
