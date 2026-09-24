package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryStatus
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptBuyReturnRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kz.mybrain.superkassa.core.application.http.RECEIPT_BUY_RETURN_DESCRIPTION
import kz.mybrain.superkassa.core.application.http.RECEIPT_SELL_DESCRIPTION
import kz.mybrain.superkassa.core.application.http.controllers.ReceiptsController
import kz.mybrain.superkassa.core.config.JsonConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Контакт покупателя из тела запроса чека доходит до ядра: по нему ядро
 * ставит доставку, и без него чек покупателю не уходит. Тело разбирается
 * тем же `Json`, которым узел читает запросы.
 */
class ReceiptCustomerContactTest {
    private val json: Json = JsonConfig().kotlinxJson()
    private val service = mockk<SuperkassaApi>()
    private val controller = ReceiptsController(service)
    private val captured = slot<ReceiptSellRequest>()

    init {
        every { service.createSellReceipt(any(), any(), capture(captured)) } returns
            ReceiptResponse(documentId = "doc-1", deliveryStatus = DeliveryStatus.ONLINE_OK)
    }

    @Test
    fun `контакт покупателя из тела доходит до ядра`() {
        val body = sale(""""customerContact": {"phone": "+77017654321", "email": "buyer@example.kz", "telegram": "123456789"}""")

        controller.createSellReceipt("kkm-1", "Bearer 7391", json.decodeFromString(body))

        val contact = captured.captured.customerContact
        assertEquals(listOf("+77017654321", "buyer@example.kz", "123456789"), listOf(contact?.phone, contact?.email, contact?.telegram))
    }

    @Test
    fun `без контакта чек принимается, а покупателю не адресуется`() {
        controller.createSellReceipt("kkm-1", "Bearer 7391", json.decodeFromString(sale()))

        assertNull(captured.captured.customerContact)
    }

    @Test
    fun `контакт покупателя принимают и чеки возврата`() {
        val body = sale(""""customerContact": {"email": "buyer@example.kz"}""")

        assertEquals("buyer@example.kz", json.decodeFromString<ReceiptBuyReturnRequest>(body).customerContact?.email)
    }

    @Test
    fun `описание метода чека называет контакт покупателя`() {
        listOf(RECEIPT_SELL_DESCRIPTION, RECEIPT_BUY_RETURN_DESCRIPTION).forEach {
            assertTrue("customerContact" in it && "без контакта чек покупателю не отправляется" in it, it)
        }
    }

    private fun sale(extra: String? = null) = """
        {
          "idempotencyKey": "sale-1",
          "items": [{"name": "Нан бидайлы", "price": 500.00, "quantity": 1}],
          "payments": [{"type": "CASH", "sum": 500.00}]${extra?.let { ",\n$it" }.orEmpty()}
        }
    """.trimIndent()
}
