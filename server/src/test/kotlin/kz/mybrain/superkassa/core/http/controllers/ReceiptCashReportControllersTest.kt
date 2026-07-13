package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryStatus
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.kkm.CashOperationRequest
import io.github.texport.superkassa.core.presentation.api.model.kkm.CashOperationResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ParentTicketRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptBuyRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptBuyReturnRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptItemRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptPaymentRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellReturnRequest
import io.github.texport.superkassa.core.presentation.api.model.shift.ReportResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kz.mybrain.superkassa.core.application.http.controllers.CashOperationsController
import kz.mybrain.superkassa.core.application.http.controllers.ReceiptsController
import kz.mybrain.superkassa.core.application.http.controllers.ReportsController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReceiptCashReportControllersTest {

    private val service = mockk<SuperkassaApi>()
    private val receiptsController = ReceiptsController(service)
    private val cashController = CashOperationsController(service)
    private val reportsController = ReportsController(service)

    @Test
    fun `createSellReceipt maps request and passes pin from bearer header`() {
        val capturedKkmId = slot<String>()
        val capturedPin = slot<String>()
        val capturedRequest = slot<ReceiptSellRequest>()
        every {
            service.createSellReceipt(capture(capturedKkmId), capture(capturedPin), capture(capturedRequest))
        } returns ReceiptResponse(documentId = "doc-sell", deliveryStatus = DeliveryStatus.ONLINE_OK)

        val response =
            receiptsController.createSellReceipt(
                kkmId = "kkm-1",
                authHeader = "Bearer 1234",
                request = sellRequest("idem-sell")
            )

        assertEquals("doc-sell", response.documentId)
        assertEquals("kkm-1", capturedKkmId.captured)
        assertEquals("1234", capturedPin.captured)
        assertEquals("idem-sell", capturedRequest.captured.idempotencyKey)
        verify(exactly = 1) { service.createSellReceipt(any(), any(), any()) }
    }

    @Test
    fun `createSellReturnReceipt maps parent ticket and operation`() {
        val capturedKkmId = slot<String>()
        val capturedPin = slot<String>()
        val capturedRequest = slot<ReceiptSellReturnRequest>()
        every {
            service.createSellReturnReceipt(capture(capturedKkmId), capture(capturedPin), capture(capturedRequest))
        } returns ReceiptResponse(documentId = "doc-sell-return", deliveryStatus = DeliveryStatus.ONLINE_OK)
        val request =
            ReceiptSellReturnRequest(
                idempotencyKey = "idem-sell-return",
                items = listOf(itemDto()),
                payments = listOf(paymentDto()),
                parentTicket =
                ParentTicketRequest(
                    parentTicketNumber = 11,
                    parentTicketDateTime = "2026-03-19T10:00:00Z",
                    kgdKkmId = "RN-1",
                    parentTicketTotal = 100.0,
                    parentTicketIsOffline = false
                )
            )

        receiptsController.createSellReturnReceipt("kkm-2", "2222", request)

        assertEquals("kkm-2", capturedKkmId.captured)
        assertEquals("2222", capturedPin.captured)
        assertEquals("idem-sell-return", capturedRequest.captured.idempotencyKey)
        assertNotNull(capturedRequest.captured.parentTicket)
        verify(exactly = 1) { service.createSellReturnReceipt(any(), any(), any()) }
    }

    @Test
    fun `createBuyReceipt maps operation BUY`() {
        val capturedKkmId = slot<String>()
        val capturedPin = slot<String>()
        val capturedRequest = slot<ReceiptBuyRequest>()
        every {
            service.createBuyReceipt(capture(capturedKkmId), capture(capturedPin), capture(capturedRequest))
        } returns ReceiptResponse(documentId = "doc-buy", deliveryStatus = DeliveryStatus.ONLINE_OK)

        receiptsController.createBuyReceipt("kkm-3", "Bearer 3333", buyRequest("idem-buy"))

        assertEquals("kkm-3", capturedKkmId.captured)
        assertEquals("3333", capturedPin.captured)
        assertEquals("idem-buy", capturedRequest.captured.idempotencyKey)
        verify(exactly = 1) { service.createBuyReceipt(any(), any(), any()) }
    }

    @Test
    fun `createBuyReturnReceipt maps operation BUY_RETURN`() {
        val capturedKkmId = slot<String>()
        val capturedPin = slot<String>()
        val capturedRequest = slot<ReceiptBuyReturnRequest>()
        every {
            service.createBuyReturnReceipt(capture(capturedKkmId), capture(capturedPin), capture(capturedRequest))
        } returns ReceiptResponse(documentId = "doc-buy-return", deliveryStatus = DeliveryStatus.ONLINE_OK)
        val request =
            ReceiptBuyReturnRequest(
                idempotencyKey = "idem-buy-return",
                items = listOf(itemDto(vatGroup = "VAT_5")),
                payments = listOf(paymentDto(type = "CARD"))
            )

        receiptsController.createBuyReturnReceipt("kkm-4", "Bearer 4444", request)

        assertEquals("kkm-4", capturedKkmId.captured)
        assertEquals("4444", capturedPin.captured)
        assertEquals("idem-buy-return", capturedRequest.captured.idempotencyKey)
        verify(exactly = 1) { service.createBuyReturnReceipt(any(), any(), any()) }
    }

    @Test
    fun `cashIn forwards pin from authorization header`() {
        every {
            service.cashIn("kkm-1", "1111", any())
        } returns CashOperationResponse(documentId = "cash-in-doc", deliveryStatus = DeliveryStatus.ONLINE_OK)

        val result =
            cashController.cashIn(
                "kkm-1",
                "Bearer 1111",
                CashOperationRequest(amount = 500.0, idempotencyKey = "cash-in-1")
            )

        assertEquals("cash-in-doc", result.documentId)
        verify(exactly = 1) {
            service.cashIn(
                "kkm-1",
                "1111",
                match {
                    it.amount == 500.0 &&
                        it.idempotencyKey == "cash-in-1"
                }
            )
        }
    }

    @Test
    fun `cashOut forwards pin from raw authorization header`() {
        every {
            service.cashOut("kkm-2", "2222", any())
        } returns CashOperationResponse(documentId = "cash-out-doc", deliveryStatus = DeliveryStatus.ONLINE_OK)

        val result =
            cashController.cashOut(
                "kkm-2",
                "2222",
                CashOperationRequest(amount = 300.0, idempotencyKey = "cash-out-1")
            )

        assertEquals("cash-out-doc", result.documentId)
        verify(exactly = 1) { service.cashOut("kkm-2", "2222", any()) }
    }

    @Test
    fun `createXReport forwards pin from authorization header`() {
        every { service.createReport("kkm-r", "5555") } returns ReportResponse(documentId = "x-report-doc", deliveryStatus = DeliveryStatus.ONLINE_OK)

        val result = reportsController.createXReport("kkm-r", "Bearer 5555")

        assertEquals("x-report-doc", result.documentId)
        verify(exactly = 1) { service.createReport("kkm-r", "5555") }
    }

    private fun itemDto(vatGroup: String = "VAT_16") =
        ReceiptItemRequest(
            name = "Bread",
            price = 100.0,
            quantity = 1.0,
            vatGroup = vatGroup
        )

    private fun paymentDto(type: String = "CASH") =
        ReceiptPaymentRequest(type = type, sum = 100.0)

    private fun sellRequest(idem: String) =
        ReceiptSellRequest(
            idempotencyKey = idem,
            items = listOf(itemDto()),
            payments = listOf(paymentDto()),
            defaultVatGroup = "VAT_16"
        )

    private fun buyRequest(idem: String) =
        ReceiptBuyRequest(
            idempotencyKey = idem,
            items = listOf(itemDto()),
            payments = listOf(paymentDto())
        )
}
