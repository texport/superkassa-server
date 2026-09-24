package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryFailure
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryOutcome
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.core.string.api.CoreStrings
import io.github.texport.superkassa.delivery.api.DeliveryServiceApi
import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.toOutcome
import org.slf4j.LoggerFactory
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest as ChannelRequest

/**
 * Порт доставки ядра поверх сервиса каналов: переводит задачу ядра
 * в запрос канала, а ответ канала — в итог с кодом и причиной отказа.
 *
 * Причину видят кассир в журнале и владелец: «не настроен», «провайдер
 * отказал» и «канал упал» различаются кодом, а не одним «не доставлено».
 * Сбой канала — недоставка, а не падение фона: чек уже оформлен.
 */
class ServerDeliveryServiceAdapter(
    private val deliveryService: DeliveryServiceApi
) : DeliveryPort {
    private val logger = LoggerFactory.getLogger(ServerDeliveryServiceAdapter::class.java)

    override fun deliver(request: DeliveryRequest): Boolean = send(request).delivered

    override fun send(request: DeliveryRequest): DeliveryOutcome {
        val channel = DeliveryChannel.entries.firstOrNull { it.name.equals(request.channel, ignoreCase = true) }
            ?: return unknownChannel(request.channel)
        return runCatching { deliveryService.deliver(request.toChannelRequest(channel)).toOutcome(channel.name) }
            .getOrElse { failed(channel, it) }
    }

    /** Канал, которого узел не знает: повтор даст тот же отказ. */
    private fun unknownChannel(channel: String): DeliveryOutcome {
        logger.warn("Delivery refused: unknown channel {}", channel)
        val failure = DeliveryFailure(UNKNOWN_CHANNEL, CoreStrings.deliveryChannelUnknown(channel))
        return DeliveryOutcome.failed(failure, retryable = false)
    }

    /** Канал упал, не ответив итогом: в журнал — только род ошибки, её текст может нести адрес покупателя. */
    private fun failed(channel: DeliveryChannel, failure: Throwable): DeliveryOutcome {
        val reason = failure.javaClass.simpleName
        logger.warn("Delivery failed: channel={}, reason={}", channel, reason)
        val message = CoreStrings.deliveryChannelFailed(channel.name, reason)
        return DeliveryOutcome.failed(DeliveryFailure(CHANNEL_FAILED, message), retryable = true)
    }

    private fun DeliveryRequest.toChannelRequest(channel: DeliveryChannel) = ChannelRequest(
        cashboxId = kkmId,
        documentId = documentId,
        channel = channel,
        destination = destination,
        payloadUrl = payloadUrl,
        payloadBytes = payloadBytes,
        payloadType = payloadType
    )

    private companion object {
        /** Коды отказов — те же, что у кассы приложения. */
        const val UNKNOWN_CHANNEL = "DELIVERY_CHANNEL_UNKNOWN"
        const val CHANNEL_FAILED = "DELIVERY_CHANNEL_FAILED"
    }
}
