package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.core.domain.api.model.common.CounterKeyFormats
import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.ADMIN_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.CASHIER_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KKM
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.cash
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.item
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Счётчики X/Z, которые ядро ведёт в базе узла: число скидок и наценок
 * операции и счёт чеков «за всё время» на начало смены. Узел хранит их
 * как любые счётчики, и они обязаны читаться обратно — в том числе
 * после перезапуска узла.
 */
class JdbcShiftCountersTest {
    private val kassa = JdbcKassa()

    @Test
    fun `скидка и наценка на позицию считаются каждая своим счётчиком`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        val discounted = item("1000.00").copy(discountSum = Decimal.parse("100.00"))
        val markedUp = item("1000.00").copy(markupSum = Decimal.parse("100.00"))
        kassa.api.createSellReceipt(
            KKM, CASHIER_PIN,
            ReceiptSellRequest(
                idempotencyKey = "sale-1", items = listOf(discounted, markedUp), payments = listOf(cash("2000.00"))
            )
        )

        assertEquals(1L to 1L, shiftCounter(CounterKeyFormats.DISCOUNT_COUNT) to shiftCounter(CounterKeyFormats.MARKUP_COUNT))
    }

    @Test
    fun `счёт чеков за всё время переходит в новую смену и переживает перезапуск узла`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        sell("sale-1")
        sell("sale-2")
        kassa.api.closeShift(KKM, ADMIN_PIN)
        kassa.api.openShift(KKM, ADMIN_PIN)

        val restarted = JdbcKassa(dir = kassa.dir, clock = kassa.clock, register = false)

        assertEquals(2L, counter(restarted, CounterKeyFormats.START_SHIFT_TICKET_TOTAL_COUNT))
    }

    private fun sell(key: String) {
        kassa.api.createSellReceipt(
            KKM, CASHIER_PIN,
            ReceiptSellRequest(idempotencyKey = key, items = listOf(item("100.00")), payments = listOf(cash("100.00")))
        )
    }

    private fun shiftCounter(format: String): Long = counter(kassa, format)

    /** Значение счётчика вида [format] в открытой смене кассы [kassa], по всем видам операций. */
    private fun counter(kassa: JdbcKassa, format: String): Long {
        val shiftId = checkNotNull(kassa.storage.findOpenShift(KKM)).id
        return kassa.storage.listCounters(KKM).filter { it.shiftId == shiftId && it.key.matches(format) }.sumOf { it.value }
    }

    private fun String.matches(format: String): Boolean {
        val (prefix, suffix) = format.split("%s")
        return startsWith(prefix) && endsWith(suffix)
    }
}
