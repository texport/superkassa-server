package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryTemplateKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * Адаптер отправки чеков в Telegram (Bot API).
 *
 * @property botToken Токен Telegram-бота.
 * @param errorResolver Сервис сопоставления локализованных сообщений.
 */
class TelegramDeliveryAdapter(
    private val botToken: String,
    errorResolver: ErrorResolver = DefaultErrorResolver()
) : BaseHttpDeliveryAdapter(errorResolver), DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.TELEGRAM
    override val logger: Logger = LoggerFactory.getLogger(TelegramDeliveryAdapter::class.java)

    /**
     * Отправляет фискальный чек в Telegram-чат через Telegram Bot API.
     *
     * @param request параметры отправки [DeliveryRequest].
     * @return результат отправки [DeliveryResult].
     */
    override fun send(request: DeliveryRequest): DeliveryResult {
        val chatId = request.destination ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.TELEGRAM_CHAT_ID_REQUIRED
            ).toString()
        )
        val text = when {
            request.payloadUrl != null -> errorResolver.resolve(DeliveryTemplateKey.SMS_BODY_URL).formatArgs(request.payloadUrl!!).format("ru")
            else -> errorResolver.resolve(DeliveryTemplateKey.SMS_BODY_READY).formatArgs(request.documentId).format("ru")
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
