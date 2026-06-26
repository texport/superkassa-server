package io.github.texport.superkassa.jvm.delivery.data

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Адаптер отправки SMS через HTTP API провайдера.
 */
class SmsDeliveryAdapter(
    private val providerUrl: String?,
    private val apiKey: String?
) : BaseHttpDeliveryAdapter(), DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.SMS
    override val logger: Logger = LoggerFactory.getLogger(SmsDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val phone = request.destination ?: return DeliveryResult(
            ok = false,
            message = "SMS destination (phone) required / " +
                "Требуется номер телефона получателя SMS / " +
                "SMS алушының телефон нөмірі қажет"
        )
        val url = providerUrl ?: return DeliveryResult(
            ok = false,
            message = "SMS provider URL not configured / " +
                "Не настроен URL-адрес провайдера SMS / " +
                "SMS-провайдердің URL-мекенжайы бапталмаған"
        )
        val text = when {
            request.payloadUrl != null -> "Чек: ${request.payloadUrl}"
            else -> "Чек ${request.documentId} готов"
        }
        return try {
            val urlWithParams = url
                .replace("{phone}", phone)
                .replace("{text}", URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8))

            val reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlWithParams))
                .GET()

            apiKey?.let { reqBuilder.header("Authorization", "Bearer $it") }

            val response = http.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            handleHttpResponse(response, request.documentId, phone, "SMS")
        } catch (e: Exception) {
            handleException(e, request.documentId, "SMS")
        }
    }
}
