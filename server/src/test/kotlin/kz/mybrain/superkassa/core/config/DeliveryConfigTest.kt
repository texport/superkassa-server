package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest
import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Доставка отвечает отказом, когда доставлять нечем.
 *
 * Прежде на месте ненастроенного канала стояла заглушка, возвращавшая
 * успех: чек покупателю не уходил, а касса записывала его доставленным.
 */
class AdaptersConfigDeliveryTest {

    private val config = AdaptersConfig()

    private fun settings(channels: List<String>, delivery: DeliverySettings? = null) = CoreSettings(
        mode = CoreMode.DESKTOP,
        storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:data/core.db"),
        deliveryChannels = channels,
        delivery = delivery
    )

    private fun request() = DeliveryRequest(
        kkmId = "kkm-1",
        documentId = "doc-1",
        channel = "PRINT",
        payloadType = "LINK",
        payloadUrl = "https://example.kz/t/1"
    )

    @Test
    fun `delivery refuses when no channel is configured`() {
        val port = config.deliveryPort(settings(emptyList()))

        assertFalse(port.deliver(request()))
    }

    @Test
    fun `delivery refuses when the chosen channel has no settings`() {
        val port = config.deliveryPort(settings(listOf("PRINT")))

        assertFalse(port.deliver(request()))
    }

    /**
     * Канал, выключенный в подробных настройках, доставку не даёт:
     * адаптеров не остаётся, и ответом должен быть отказ.
     */
    @Test
    fun `delivery refuses when every channel is disabled`() {
        val delivery = DeliverySettings(
            channels = listOf(
                io.github.texport.superkassa.core.domain.api.model.settings.DeliveryChannelSettings(
                    channel = "PRINT",
                    enabled = false,
                    payloadType = "LINK",
                    documentFormat = "HTML",
                    destination = "printer"
                )
            )
        )
        val port = config.deliveryPort(settings(listOf("PRINT"), delivery))

        assertFalse(port.deliver(request()))
    }
}
