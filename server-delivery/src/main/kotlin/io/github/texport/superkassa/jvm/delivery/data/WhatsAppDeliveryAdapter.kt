package io.github.texport.superkassa.jvm.delivery.data

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Адаптер отправки чеков через WhatsApp Cloud API.
 */
class WhatsAppDeliveryAdapter(
    private val accessToken: String,
    private val phoneNumberId: String
) : BaseHttpDeliveryAdapter(), DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.WHATSAPP
    override val logger: Logger = LoggerFactory.getLogger(WhatsAppDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val to = request.destination ?: return DeliveryResult(
            ok = false,
            message = "WhatsApp phone number required / " +
                "Требуется номер телефона WhatsApp / " +
                "WhatsApp телефон нөмірі қажет"
        )
        val text = when {
            request.payloadUrl != null -> "Чек: ${request.payloadUrl}"
            else -> "Чек ${request.documentId} готов"
        }
        val normalizedPhone = normalizePhoneNumber(to)
        val body = """
            {
                "messaging_product": "whatsapp",
                "to": "$normalizedPhone",
                "type": "text",
                "text": { "body": ${text.toJsonString()} }
            }
        """.trimIndent()
        return try {
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v18.0/$phoneNumberId/messages"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            handleHttpResponse(response, request.documentId, to, "WhatsApp")
        } catch (e: Exception) {
            handleException(e, request.documentId, "WhatsApp")
        }
    }
}
