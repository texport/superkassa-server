package io.github.texport.superkassa.jvm.delivery.data

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

/**
 * Адаптер физической печати чеков на принтер, зарегистрированный в операционной системе,
 * с использованием Java Print Service (JPS) API.
 */
class JpsPrintDeliveryAdapter(
    private val printerName: String
) : DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.PRINT
    private val logger = LoggerFactory.getLogger(JpsPrintDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val bytes = request.payloadBytes ?: return DeliveryResult(
            ok = false,
            message = "No print payload / Отсутствуют данные для печати / " +
                "Басып шығаруға арналған деректер жоқ"
        )
        return try {
            val flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE
            val services = PrintServiceLookup.lookupPrintServices(flavor, null)
            val targetService = services.firstOrNull { it.name.equals(printerName, ignoreCase = true) }
                ?: return DeliveryResult(
                    ok = false,
                    message = "Printer '$printerName' not found in the OS / " +
                        "Принтер '$printerName' не найден в операционной системе / " +
                        "'$printerName' принтері операциялық жүйеде табылмады"
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
            DeliveryResult(
                ok = false,
                message = "OS local print failed: $msg / Ошибка локальной печати ОС: $msg / Операциялық жүйенің жергілікті басып шығару қатесі: $msg"
            )
        }
    }
}
