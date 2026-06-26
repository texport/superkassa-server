package kz.mybrain.superkassa.core.receipt

import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.model.receipt.ReceiptItemDto
import kz.mybrain.superkassa.core.application.model.receipt.ReceiptMapper
import kz.mybrain.superkassa.core.application.model.receipt.ReceiptPaymentDto
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReceiptMapperTest {

    @Test
    fun `toReceiptRequest rejects mixed item and receipt discounts`() {
        val item = ReceiptItemDto(
            name = "Milk 1L",
            price = 500.0,
            quantity = 1,
            discountSum = 50.0
        )
        val payment = ReceiptPaymentDto(type = "CASH", sum = 450.0)

        val ex = assertFailsWith<ValidationException> {
            ReceiptMapper.toReceiptRequest(
                kkmId = "kkm-1",
                pin = "1111",
                operation = ReceiptOperationType.SELL,
                idempotencyKey = "idem-1",
                items = listOf(item),
                discountPercent = null,
                discountSum = 10.0,
                markupPercent = null,
                markupSum = null,
                payments = listOf(payment),
                taken = null,
                change = null,
                parentTicket = null,
                defaultVatGroup = null
            )
        }

        assertEquals("RECEIPT_DISCOUNT_SCOPES_CONFLICT", ex.code)
    }
}
