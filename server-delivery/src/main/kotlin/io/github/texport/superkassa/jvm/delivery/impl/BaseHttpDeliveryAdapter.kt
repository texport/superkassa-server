package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import org.slf4j.Logger
import java.net.http.HttpClient
import java.net.http.HttpResponse

/**
 * Базовый абстрактный класс для HTTP-адаптеров доставки, дедуплицирующий работу с HTTP-клиентом.
 *
 * @property errorResolver Сервис для получения трехъязычных сообщений об ошибках.
 */
abstract class BaseHttpDeliveryAdapter(
    protected val errorResolver: ErrorResolver = DefaultErrorResolver()
) {

    protected val http: HttpClient = HttpClient.newBuilder().build()

    protected abstract val logger: Logger

    protected fun handleHttpResponse(
        response: HttpResponse<String>,
        documentId: String,
        destination: String,
        channelName: String
    ): DeliveryResult {
        return if (response.statusCode() in 200..299) {
            logger.debug("{} sent for document {} to {}", channelName, documentId, destination)
            DeliveryResult(true)
        } else {
            val status = response.statusCode()
            val body = response.body()
            logger.error(
                "{} failed for document {} to {}: {} {}",
                channelName,
                documentId,
                destination,
                status,
                body
            )
            val msgStr = errorResolver.resolve(
                DeliveryErrorKey.HTTP_DELIVERY_FAILED
            ).formatArgs(channelName, status, body).toString()
            DeliveryResult(
                ok = false,
                message = msgStr
            )
        }
    }

    protected fun handleException(
        e: Exception,
        documentId: String,
        channelName: String
    ): DeliveryResult {
        logger.error("{} exception for document {}: {}", channelName, documentId, e.message, e)
        val msg = e.message ?: "Unknown error"
        val msgStr = errorResolver.resolve(
            DeliveryErrorKey.HTTP_DELIVERY_ERROR
        ).formatArgs(channelName, msg).toString()
        return DeliveryResult(
            ok = false,
            message = msgStr
        )
    }

    protected fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }

    protected fun String.toJsonString(): String {
        return "\"" + this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }
}
