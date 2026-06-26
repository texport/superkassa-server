package io.github.texport.superkassa.jvm.delivery.data

import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import org.slf4j.Logger
import java.net.http.HttpClient
import java.net.http.HttpResponse

/**
 * Базовый абстрактный класс для HTTP-адаптеров доставки, дедуплицирующий работу с HTTP-клиентом.
 */
abstract class BaseHttpDeliveryAdapter {

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
            logger.error("{} failed for document {} to {}: {} {}", channelName, documentId, destination, status, body)
            DeliveryResult(
                ok = false,
                message = "Delivery via $channelName failed with status $status. Response: $body / " +
                    "Доставка через $channelName завершилась ошибкой с кодом $status. Ответ: $body / " +
                    "$channelName арқылы жеткізу $status кодымен қате аяқталды. Жауап: $body"
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
        return DeliveryResult(
            ok = false,
            message = "Delivery via $channelName failed: $msg / " +
                "Ошибка доставки через $channelName: $msg / " +
                "$channelName арқылы жеткізу қатесі: $msg"
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
