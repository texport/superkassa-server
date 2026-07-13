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
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Адаптер отправки SMS через HTTP API провайдера.
 *
 * @property providerUrl URL-адрес шлюза SMS-провайдера.
 * @property apiKey Токен авторизации для API провайдера.
 * @param errorResolver Сервис сопоставления локализованных сообщений.
 */
class SmsDeliveryAdapter(
    private val providerUrl: String?,
    private val apiKey: String?,
    errorResolver: ErrorResolver = DefaultErrorResolver()
) : BaseHttpDeliveryAdapter(errorResolver), DeliveryPort {

    override val channel: DeliveryChannel = DeliveryChannel.SMS
    override val logger: Logger = LoggerFactory.getLogger(SmsDeliveryAdapter::class.java)

    /**
     * Отправляет фискальный чек по SMS с использованием внешнего HTTP-шлюза.
     *
     * @param request параметры отправки [DeliveryRequest].
     * @return результат отправки [DeliveryResult].
     */
    override fun send(request: DeliveryRequest): DeliveryResult {
        val phone = request.destination ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.SMS_DESTINATION_REQUIRED
            ).toString()
        )
        val url = providerUrl ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.SMS_PROVIDER_NOT_CONFIGURED
            ).toString()
        )
        val text = when {
            request.payloadUrl != null -> errorResolver.resolve(DeliveryTemplateKey.SMS_BODY_URL).formatArgs(request.payloadUrl!!).format("ru")
            else -> errorResolver.resolve(DeliveryTemplateKey.SMS_BODY_READY).formatArgs(request.documentId).format("ru")
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
