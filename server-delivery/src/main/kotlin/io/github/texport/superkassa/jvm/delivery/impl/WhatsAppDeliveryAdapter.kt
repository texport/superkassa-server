package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import io.github.texport.superkassa.delivery.api.port.DeliveryPort
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryTemplateKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Адаптер отправки чеков через WhatsApp Cloud API.
 *
 * @property accessToken Токен доступа WhatsApp Cloud API.
 * @property phoneNumberId Идентификатор телефонного номера WhatsApp.
 * @param errorResolver Сервис сопоставления локализованных сообщений.
 */
class WhatsAppDeliveryAdapter(
    private val accessToken: String,
    private val phoneNumberId: String,
    errorResolver: ErrorResolver = DefaultErrorResolver()
) : BaseHttpDeliveryAdapter(errorResolver), DeliveryPort {

    override val channel: DeliveryChannel = DeliveryChannel.WHATSAPP
    override val logger: Logger = LoggerFactory.getLogger(WhatsAppDeliveryAdapter::class.java)

    /**
     * Отправляет фискальный чек в WhatsApp-чат через Facebook Cloud API.
     *
     * @param request параметры отправки [DeliveryRequest].
     * @return результат отправки [DeliveryResult].
     */
    override fun send(request: DeliveryRequest): DeliveryResult {
        val to = request.destination ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.WHATSAPP_PHONE_REQUIRED
            ).toString()
        )
        val text = when {
            request.payloadUrl != null -> errorResolver.resolve(DeliveryTemplateKey.SMS_BODY_URL).formatArgs(request.payloadUrl!!).format("ru")
            else -> errorResolver.resolve(DeliveryTemplateKey.SMS_BODY_READY).formatArgs(request.documentId).format("ru")
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
