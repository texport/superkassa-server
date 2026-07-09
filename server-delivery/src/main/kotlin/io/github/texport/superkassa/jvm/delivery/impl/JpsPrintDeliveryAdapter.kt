package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import org.slf4j.LoggerFactory
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

/**
 * Адаптер физической печати чеков на принтер, зарегистрированный в операционной системе,
 * с использованием Java Print Service (JPS) API.
 *
 * @property printerName Имя принтера в операционной системе.
 * @property errorResolver Сервис сопоставления локализованных сообщений.
 */
class JpsPrintDeliveryAdapter(
    private val printerName: String,
    private val errorResolver: ErrorResolver = DefaultErrorResolver()
) : DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.PRINT
    private val logger = LoggerFactory.getLogger(JpsPrintDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val bytes = request.payloadBytes ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.PRINT_PAYLOAD_MISSING
            ).toString()
        )
        return try {
            val flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE
            val services = PrintServiceLookup.lookupPrintServices(flavor, null)
            val targetService = services.firstOrNull { it.name.equals(printerName, ignoreCase = true) }
                ?: return DeliveryResult(
                    ok = false,
                    message = errorResolver.resolve(
                        DeliveryErrorKey.PRINTER_NOT_FOUND
                    ).formatArgs(printerName).toString()
                )

            val job = targetService.createPrintJob()
            val doc = SimpleDoc(bytes, flavor, null)
            job.print(doc, null)

            logger.debug("Printed document {} to local OS printer '{}'", request.documentId, printerName)
            DeliveryResult(true)
        } catch (e: Exception) {
            logger.error(
                "Local JPS print failed for document {} on printer '{}': {}",
                request.documentId,
                printerName,
                e.message,
                e
            )
            val msg = e.message ?: "Unknown error"
            val msgStr = errorResolver.resolve(
                DeliveryErrorKey.LOCAL_PRINT_FAILED
            ).formatArgs(msg).toString()
            DeliveryResult(
                ok = false,
                message = msgStr
            )
        }
    }
}
