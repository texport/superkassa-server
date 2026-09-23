package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.ADMIN_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.CASHIER_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KKM
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.cash
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.item
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Автозакрытие смены на хранилище узла: закрытие идёт в своей транзакции,
 * а обмен с БФД — во вложенной, и обе ложатся в одно соединение.
 */
class JdbcAutoCloseShiftTest {
    private val kassa = JdbcKassa()

    @Test
    fun `смена у предела суток закрывается сама, и закрытие фиксируется`() {
        kassa.settings(autoCloseShift = true)
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.api.createSellReceipt(
            KKM, CASHIER_PIN,
            ReceiptSellRequest(idempotencyKey = "sale-1", items = listOf(item("100")), payments = listOf(cash("100")))
        )
        kassa.clock.move(DAY_MILLIS)

        val closed = kassa.api.autoCloseShift(KKM)

        assertNotNull(closed)
        assertNull(JdbcKassa.openStorage(kassa.dir).findOpenShift(KKM), "closing is committed, seen by a new connection")
        assertEquals(1, kassa.bfd.closeShifts().size)
    }

    @Test
    fun `без настройки автозакрытия смена остаётся открытой`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.clock.move(DAY_MILLIS)

        assertNull(kassa.api.autoCloseShift(KKM))
        assertNotNull(kassa.storage.findOpenShift(KKM))
    }

    private companion object {
        const val DAY_MILLIS = 24 * 3_600_000L
    }
}
