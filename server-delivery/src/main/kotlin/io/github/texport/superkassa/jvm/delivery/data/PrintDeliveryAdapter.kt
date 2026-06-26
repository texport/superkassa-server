package io.github.texport.superkassa.jvm.delivery.data

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.net.Socket

/**
 * Адаптер печати чеков по сети (ESC/POS через raw TCP сокет).
 */
class PrintDeliveryAdapter(
    private val host: String,
    private val port: Int
) : DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.PRINT
    private val logger = LoggerFactory.getLogger(PrintDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val bytes = request.payloadBytes ?: return DeliveryResult(
            ok = false,
            message = "No print payload / Отсутствуют данные для печати / " +
                "Басып шығаруға арналған деректер жоқ"
        )
        return try {
            Socket(host, port).use { socket ->
                val out: OutputStream = socket.getOutputStream()
                out.write(bytes)
                out.flush()
            }
            logger.debug("Printed document {} to {}:{}", request.documentId, host, port)
            DeliveryResult(true)
        } catch (e: Exception) {
            logger.error("Print failed for document {}: {}", request.documentId, e.message, e)
            val msg = e.message ?: "Unknown error"
            DeliveryResult(
                ok = false,
                message = "Print failed: $msg / Ошибка печати: $msg / Басып шығару қатесі: $msg"
            )
        }
    }
}
