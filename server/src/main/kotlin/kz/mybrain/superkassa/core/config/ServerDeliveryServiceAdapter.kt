package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.delivery.api.DeliveryServiceApi
import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import org.slf4j.LoggerFactory
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest as ChannelRequest

/**
 * Порт доставки ядра поверх сервиса каналов: переводит запрос ядра
 * в запрос канала и ответ канала — в «доставлено или нет».
 *
 * Сбой канала — недоставка, а не падение операции кассы: чек уже оформлен.
 */
class ServerDeliveryServiceAdapter(
    private val deliveryService: DeliveryServiceApi
) : DeliveryPort {
    private val logger = LoggerFactory.getLogger(ServerDeliveryServiceAdapter::class.java)

    override fun deliver(request: DeliveryRequest): Boolean {
        val channel = DeliveryChannel.entries.firstOrNull { it.name == request.channel.uppercase() }
            ?: DeliveryChannel.PRINT.also { logger.warn("Unknown delivery channel: {}, using PRINT", request.channel) }
        val delivery = ChannelRequest(
            cashboxId = request.kkmId,
            documentId = request.documentId,
            channel = channel,
            destination = request.destination,
            payloadUrl = request.payloadUrl,
            payloadBytes = request.payloadBytes
        )
        return runCatching { deliveryService.deliver(delivery).ok }
            .onFailure { logger.error("Failed to deliver document {} for KKM {}", request.documentId, request.kkmId, it) }
            .getOrDefault(false)
    }
}
