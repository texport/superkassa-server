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
import java.nio.charset.StandardCharsets

/**
 * Адаптер отправки чеков в Telegram (Bot API).
 */
class TelegramDeliveryAdapter(
    private val botToken: String
) : BaseHttpDeliveryAdapter(), DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.TELEGRAM
    override val logger: Logger = LoggerFactory.getLogger(TelegramDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val chatId = request.destination ?: return DeliveryResult(
            ok = false,
            message = "Telegram chat_id required / " +
                "Требуется ID чата Telegram / " +
                "Telegram чат идентификаторы қажет"
        )
        val text = when {
            request.payloadUrl != null -> "Чек: ${request.payloadUrl}"
            else -> "Чек ${request.documentId} готов"
        }
        return try {
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8)
            val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText"
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()

            val response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            handleHttpResponse(response, request.documentId, chatId, "Telegram")
        } catch (e: Exception) {
            handleException(e, request.documentId, "Telegram")
        }
    }
}
