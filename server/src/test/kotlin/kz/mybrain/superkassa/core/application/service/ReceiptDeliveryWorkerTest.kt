package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.presentation.api.DeliveryApi
import io.github.texport.superkassa.core.presentation.api.model.delivery.ReceiptDeliveryState
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.application.service.NodeKassa.Companion.BUYER
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Чек покупателю на узле: задача ставится, когда БФД принял чек, и уходит
 * тактом [ReceiptDeliveryWorker] по контакту покупателя из чека — через
 * порт доставки узла и канал, подменённый на запоминающий.
 */
class ReceiptDeliveryWorkerTest {
    private val dir = Files.createTempDirectory("node-delivery")
    private val node = NodeKassa(dir)
    private val kkmId = node.registerAndOpenShift()

    @Test
    fun `чек уходит тактом по телефону покупателя из чека, а не в пробитии`() {
        val receipt = node.sell(kkmId, BUYER)
        val whilePunching = node.sms.sent.size
        val queued = node.deliveries(kkmId, receipt).single().state

        node.worker.sendDueDeliveries()

        assertEquals(0 to ReceiptDeliveryState.PENDING, whilePunching to queued)
        val sent = node.sms.sent.single()
        assertEquals(BUYER.phone to receipt.documentId, sent.destination to sent.documentId)
        assertEquals(ReceiptDeliveryState.DELIVERED, node.deliveries(kkmId, receipt).single().state)
    }

    @Test
    fun `чек без контакта покупателя задач не ставит и никуда не уходит`() {
        val receipt = node.sell(kkmId, buyer = null)

        node.worker.sendDueDeliveries()

        assertTrue(node.deliveries(kkmId, receipt).isEmpty())
        assertTrue(node.sms.sent.isEmpty())
    }

    @Test
    fun `чек, не ушедший до остановки узла, уходит первым тактом после запуска`() {
        val receipt = node.sell(kkmId, BUYER)

        val restarted = NodeKassa(dir, bfd = node.bfd, clock = node.clock)
        restarted.worker.sendDueDeliveries()

        assertEquals(listOf(BUYER.phone), restarted.sms.sent.map { it.destination })
        assertEquals(ReceiptDeliveryState.DELIVERED, restarted.deliveries(kkmId, receipt).single().state)
    }

    @Test
    fun `отказ провайдера виден кассиру кодом и словами, и чек уходит повтором`() {
        node.sms.answer = DeliveryResult(ok = false, message = "SMS gateway is busy", code = "DELIVERY_SMS_REFUSED")
        val receipt = node.sell(kkmId, BUYER)

        node.worker.sendDueDeliveries()
        val refused = node.deliveries(kkmId, receipt).single()
        node.sms.answer = DeliveryResult(ok = true)
        node.clock.move(1.minutes)
        node.worker.sendDueDeliveries()

        assertEquals(ReceiptDeliveryState.PENDING to "DELIVERY_SMS_REFUSED", refused.state to refused.failureCode)
        assertEquals("SMS gateway is busy", refused.failureMessage?.ru)
        assertEquals(ReceiptDeliveryState.DELIVERED, node.deliveries(kkmId, receipt).single().state)
    }

    @Test
    fun `упавший канал не роняет такт, задача ждёт повтора с кодом сбоя канала`() {
        node.sms.failure = IllegalStateException("gateway socket closed for +77017654321")
        val receipt = node.sell(kkmId, BUYER)

        node.worker.sendDueDeliveries()

        val failed = node.deliveries(kkmId, receipt).single()
        assertEquals(ReceiptDeliveryState.PENDING to "DELIVERY_CHANNEL_FAILED", failed.state to failed.failureCode)
        assertTrue(BUYER.phone.orEmpty() !in failed.failureMessage?.ru.orEmpty(), "buyer phone leaked into the reason")
    }

    @Test
    fun `полный заход зовёт следующий сразу, а сбой хранилища ждёт следующего такта`() {
        val delivery = mockk<DeliveryApi>()
        every { delivery.sendDueDeliveries(any()) } returnsMany listOf(FULL_PASS, 3) andThenThrows IllegalStateException()
        val worker = ReceiptDeliveryWorker(delivery)

        worker.sendDueDeliveries()
        worker.sendDueDeliveries()

        verify(exactly = 3) { delivery.sendDueDeliveries(FULL_PASS) }
    }

    private companion object {
        /** Размер захода досылки у узла. */
        const val FULL_PASS = 20
    }
}
