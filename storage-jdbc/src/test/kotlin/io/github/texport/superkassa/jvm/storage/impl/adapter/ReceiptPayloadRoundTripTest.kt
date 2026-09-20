package io.github.texport.superkassa.jvm.storage.impl.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.texport.superkassa.core.domain.api.model.common.Money
import io.github.texport.superkassa.core.domain.api.model.receipt.PaymentType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptItem
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptOperationType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptPayment
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptRequest
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptStoredPayload
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Чек хранится в журнале сериализованным, и отправка в ОФД собирается
 * из прочитанного обратно. Если запись и чтение разошлись хотя бы одним
 * полем, чек уходит в очередь и не выходит из неё никогда: узел молча
 * копит документы, которых ОФД не видел.
 *
 * Так и случилось, когда в денежной сумме появилось вычисляемое свойство:
 * запись добавляла в JSON лишнее поле, а чтение падало на нём.
 */
class ReceiptPayloadRoundTripTest {

    private val jackson = jacksonObjectMapper()

    @Test
    fun `сохранённый чек читается обратно тем же`() {
        val request = ReceiptRequest(
            kkmId = "kkm-1",
            pin = "4821",
            operation = ReceiptOperationType.SELL,
            items = listOf(
                ReceiptItem(
                    name = "Кофе зерновой",
                    sectionCode = "001",
                    quantity = 2000L,
                    price = Money(1250, 50),
                    sum = Money(2501, 0)
                )
            ),
            payments = listOf(ReceiptPayment(PaymentType.CASH, Money(2501, 0))),
            total = Money(2501, 0),
            idempotencyKey = "round-trip"
        )

        val bytes = jackson.writeValueAsBytes(ReceiptStoredPayload.fromReceiptRequest(request))
        val restored = jackson.readValue(bytes, ReceiptStoredPayload::class.java).toReceiptRequest()

        assertEquals(request.total, restored.total)
        assertEquals(request.items.first().price, restored.items.first().price)
        assertEquals(request.payments.first().sum, restored.payments.first().sum)
    }

    @Test
    fun `в сохранённом чеке нет полей сверх бинов и тиынов суммы`() {
        val json = jackson.writeValueAsString(Money(1250, 50))
        assertEquals("""{"bills":1250,"coins":50}""", json)
    }
}
