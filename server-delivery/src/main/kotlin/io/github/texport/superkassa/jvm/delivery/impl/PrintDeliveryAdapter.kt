package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import io.github.texport.superkassa.delivery.api.port.DeliveryPort
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.net.Socket

/**
 * Адаптер печати чеков по сети (ESC/POS через raw TCP сокет).
 *
 * @property host IP-адрес или хостнейм принтера.
 * @property port Сетевой порт принтера.
 * @property errorResolver Сервис сопоставления локализованных сообщений.
 */
class PrintDeliveryAdapter(
    private val host: String,
    private val port: Int,
    private val errorResolver: ErrorResolver = DefaultErrorResolver()
) : DeliveryPort {

    override val channel: DeliveryChannel = DeliveryChannel.PRINT
    private val logger = LoggerFactory.getLogger(PrintDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val bytes = request.payloadBytes ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.PRINT_PAYLOAD_MISSING
            ).toString()
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
            val msgStr = errorResolver.resolve(
                DeliveryErrorKey.PRINT_FAILED
            ).formatArgs(msg).toString()
            DeliveryResult(
                ok = false,
                message = msgStr
            )
        }
    }
}
