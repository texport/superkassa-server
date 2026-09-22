package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.api.model.DeliveryResult
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
            // Ответ провайдера умеет повторить адрес запроса, а в адресе лежит
            // ключ канала: и в журнал, и наружу он идёт уже без ключа.
            val body = Secrets.mask(response.body().orEmpty())
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
        // Наружу уходит род отказа, а не текст исключения: HTTP-клиент
        // вкладывает в сообщение адрес запроса вместе с ключом канала.
        // Для разбора остаётся журнал, где и сообщение, и стек — без ключа.
        val reason = reasonOf(e)
        logger.error(
            "{} exception for document {}: {} {}",
            channelName,
            documentId,
            reason,
            Secrets.mask(e.stackTraceToString())
        )
        val msgStr = errorResolver.resolve(
            DeliveryErrorKey.HTTP_DELIVERY_ERROR
        ).formatArgs(channelName, reason).toString()
        return DeliveryResult(
            ok = false,
            message = msgStr
        )
    }

    /**
     * Род отказа: класс ошибки и, если он известен, класс её причины.
     *
     * Кода у исключений `java.net.http` нет — код отказа приходит статусом
     * ответа и уходит наружу отдельной веткой [handleHttpResponse].
     */
    private fun reasonOf(e: Exception): String {
        val failure = e::class.simpleName ?: DEFAULT_REASON
        val cause = e.cause?.let { it::class.simpleName }
        return if (cause == null) failure else "$failure ($cause)"
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

    private companion object {
        const val DEFAULT_REASON = "Exception"
    }
}
