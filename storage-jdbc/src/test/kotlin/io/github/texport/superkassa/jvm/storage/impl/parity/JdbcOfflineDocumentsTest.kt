package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.ADMIN_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.BUYER
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.CASHIER_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KKM
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.cash
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.item
import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.github.texport.superkassa.core.presentation.api.model.kkm.CashOperationRequest
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryStatus
import io.github.texport.superkassa.core.presentation.api.model.receipt.CustomerContactRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.github.texport.superkassa.testing.api.clock.MovableClock
import kz.kazakhtelecom.proto.v203.CommandTypeEnum
import kz.kazakhtelecom.proto.v203.DateTime
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Документы, оформленные без ответа БФД: очередь, доставка покупателю,
 * ответ на повтор и то, с чем документ досылается.
 */
class JdbcOfflineDocumentsTest {
    private val clock = MovableClock()
    private val kassa = JdbcKassa(clock = clock)

    @Test
    fun `чек, принятый онлайн, доставляется покупателю один раз`() {
        kassa.api.openShift(KKM, ADMIN_PIN)

        val sale = sell("100.00", "sale-1", BUYER).documentId
        val queued = kassa.deliveries.of(sale).size
        kassa.delivery.sendDueDeliveries(limit = 10)
        kassa.delivery.sendDueDeliveries(limit = 10)

        assertEquals(0, queued, "delivery waits for the background pass")
        assertEquals(listOf(BUYER.phone), kassa.deliveries.of(sale).map { it.destination })
    }

    @Test
    fun `автономный чек доходит до покупателя после досылки в БФД, а не раньше`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.bfd.unreachableOnce()
        val sale = sell("100.00", "sale-1", BUYER).documentId

        val beforeResend = kassa.delivery.sendDueDeliveries(limit = 10)
        kassa.reconnectAndResend()
        kassa.delivery.sendDueDeliveries(limit = 10)

        assertEquals(0, beforeResend)
        assertEquals(listOf(BUYER.phone), kassa.deliveries.of(sale).map { it.destination })
    }

    @Test
    fun `при непустой очереди чек и внесение ставятся в неё по одному разу`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.bfd.unreachableOnce()
        sell("100.00", "sale-1")

        val sale = sell("200.00", "sale-2").documentId
        val cashIn = kassa.api.cashIn(KKM, CASHIER_PIN, CashOperationRequest(Decimal.parse("50.00"), "in-1")).documentId

        assertEquals(1, kassa.queueTasks(sale).size)
        assertEquals(1, kassa.queueTasks(cashIn).size)
    }

    @Test
    fun `отказ БФД досланному чеку виден в очереди кодом и переживает перезапуск узла`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.bfd.unreachableOnce()
        sell("100.00", "sale-1")
        kassa.bfd.reject(CommandTypeEnum.COMMAND_TICKET, INCORRECT_REQUEST_DATA)

        kassa.reconnectAndResend()
        val restarted = JdbcKassa(dir = kassa.dir, clock = clock, register = false)

        val item = restarted.api.queue.listQueue(KKM, ADMIN_PIN).single()
        assertEquals(INCORRECT_REQUEST_DATA, item.bfdResultCode, item.lastError)
    }

    @Test
    fun `X-отчёт без ответа БФД встаёт в очередь и досылается`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.bfd.loseNextAnswer()

        val report = kassa.api.createReport(KKM, CASHIER_PIN)
        val queued = kassa.queueTasks(report.documentId).size
        kassa.reconnectAndResend()

        assertEquals(DeliveryStatus.OFFLINE_QUEUED to 1, report.deliveryStatus to queued)
        assertEquals("SENT", kassa.document(report.documentId).ofdStatus)
        assertEquals(2, kassa.bfd.xReports().size, "the report was resent")
    }

    @Test
    fun `повтор с тем же ключом отвечает тем, что стало с чеком`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.bfd.unreachableOnce()
        val first = sell("100.00", "same-key")

        val whileQueued = sell("100.00", "same-key")
        kassa.reconnectAndResend()
        val afterResend = sell("100.00", "same-key")

        assertEquals(first.documentId to DeliveryStatus.OFFLINE_QUEUED, whileQueued.documentId to whileQueued.deliveryStatus)
        assertEquals(first.documentId to DeliveryStatus.ONLINE_OK, afterResend.documentId to afterResend.deliveryStatus)
        assertEquals(1, kassa.bfd.countedTickets().size)
    }

    @Test
    fun `досланные документы несут время и смену оформления, а не досылки`() {
        clock.move(-DAY_MILLIS)
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.bfd.unreachableOnce()
        val sale = sell("100.00", "sale-1").documentId
        val cashIn = kassa.api.cashIn(KKM, CASHIER_PIN, CashOperationRequest(Decimal.parse("50.00"), "in-1")).documentId
        clock.move(HOUR_MILLIS)
        val close = kassa.api.closeShift(KKM, ADMIN_PIN).documentId
        clock.move(DAY_MILLIS)
        kassa.api.openShift(KKM, ADMIN_PIN)

        kassa.reconnectAndResend()

        val ticket = kassa.bfd.requests.mapNotNull { it.ticket }.last()
        val placement = kassa.bfd.moneyPlacements().last()
        val closing = kassa.bfd.requests.mapNotNull { it.close_shift }.last()
        assertEquals(almaty(kassa.document(sale).createdAt) to 1, seconds(ticket.date_time) to ticket.fr_shift_number)
        assertEquals(1, placement.fr_shift_number)
        assertEquals(almaty(kassa.document(close).createdAt), seconds(closing.close_time))
    }

    private fun sell(total: String, key: String, buyer: CustomerContactRequest? = null): ReceiptResponse =
        kassa.api.createSellReceipt(
            KKM, CASHIER_PIN,
            ReceiptSellRequest(
                idempotencyKey = key, items = listOf(item(total)), payments = listOf(cash(total)), customerContact = buyer
            )
        )

    /** Время документа в Алматы с точностью до секунды — так его несёт запрос. */
    private fun almaty(millis: Long): List<Int?> = Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Almaty")).let {
        listOf(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute, it.second)
    }

    private fun seconds(value: DateTime): List<Int?> =
        listOf(value.date.year, value.date.month, value.date.day, value.time.hour, value.time.minute, value.time.second)

    private companion object {
        /** RESULT_TYPE_INCORRECT_REQUEST_DATA: БФД не принял данные документа. */
        const val INCORRECT_REQUEST_DATA = 13
        const val HOUR_MILLIS = 3_600_000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
    }
}
