package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.reference.TrilingualMessageResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryStatus
import io.github.texport.superkassa.core.presentation.api.model.queue.QueueItemResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kz.mybrain.superkassa.core.application.http.controllers.QueueController
import kz.mybrain.superkassa.core.application.http.controllers.ReceiptsController
import kz.mybrain.superkassa.core.config.JsonConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Отказ БФД уходит клиенту узла кодом и причиной на трёх языках: в ответе
 * чека и в списке очереди досылки. Проверка идёт по JSON, которым узел
 * отвечает, а не по полям объекта.
 */
class BfdRefusalResponseTest {
    private val json: Json = JsonConfig().kotlinxJson()
    private val service = mockk<SuperkassaApi>()

    @Test
    fun `чек, отклонённый БФД, отвечает причиной на трёх языках и кодом БФД`() {
        every { service.createSellReceipt(any(), any(), any()) } returns ReceiptResponse(
            documentId = "doc-1", deliveryStatus = DeliveryStatus.ONLINE_ERROR, deliveryError = REASON, bfdResultCode = CODE
        )

        val response = ReceiptsController(service).createSellReceipt("kkm-1", "Bearer 7391", SALE)
        val body = encoded(json.encodeToString(ReceiptResponse.serializer(), response))

        assertEquals(REASON.en, body.getValue("deliveryError").jsonObject.getValue("en").jsonPrimitive.content)
        assertEquals(CODE, body.getValue("bfdResultCode").jsonPrimitive.int)
    }

    @Test
    fun `задача очереди, отклонённая БФД, показывает код БФД`() {
        every { service.queue.listQueue("kkm-1", "7391") } returns listOf(
            QueueItemResponse(
                id = "q-1", lane = "OFFLINE", type = "TICKET", status = "REJECTED", attempt = 1, nextAttemptAt = null,
                lastError = REASON.ru, errorRu = REASON.ru, errorKk = REASON.kk, errorEn = REASON.en, bfdResultCode = CODE
            )
        )

        val items = QueueController(service).listQueue("kkm-1", "Bearer 7391")
        val body = encoded(json.encodeToString(QueueItemResponse.serializer(), items.single()))

        assertEquals(CODE, body.getValue("bfdResultCode").jsonPrimitive.int)
    }

    private fun encoded(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private companion object {
        const val CODE = 13
        val REASON = TrilingualMessageResponse(ru = "БФД не принял данные чека", kk = "БФД чек деректерін қабылдамады", en = "The BFD refused the receipt data")
        val SALE = ReceiptSellRequest(idempotencyKey = "sale-1", items = emptyList(), payments = emptyList())
    }
}
