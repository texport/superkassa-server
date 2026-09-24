package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.ADMIN_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.CASHIER_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KKM
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.SYSTEM_ID
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.cash
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.item
import io.github.texport.superkassa.core.domain.api.model.common.CounterKeyFormats
import io.github.texport.superkassa.core.domain.api.model.common.CounterScopes
import io.github.texport.superkassa.core.domain.api.model.common.format
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryStatus
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import kz.kazakhtelecom.proto.v203.CommandTypeEnum
import kz.kazakhtelecom.proto.v203.MoneyPlacementEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kz.kazakhtelecom.proto.v203.Money as BfdMoney

/**
 * Закрытие смены — фискальная операция: смена закрывается тогда, когда
 * Z-отчёт принят или ушёл в автономную очередь, а изъятие при закрытии
 * едет внутри Z-отчёта и ложится в закрываемую смену БФД.
 */
class JdbcShiftCloseTest {
    private val kassa = JdbcKassa()

    @Test
    fun `изъятие при закрытии уходит признаком Z-отчёта и ложится в закрываемую смену БФД`() {
        kassa.settings(autoCashout = true)
        val shift = kassa.api.openShift(KKM, ADMIN_PIN)
        sell("3500")

        kassa.api.closeShift(KKM, CASHIER_PIN)

        val close = kassa.bfd.closeShifts().single()
        assertEquals(true, close.withdraw_money)
        assertTrue(kassa.bfd.moneyPlacements().isEmpty(), "withdrawal is not sent on its own after the Z-report")
        assertEquals(mapOf(1 to 350_000L), kassa.bfd.withdrawnByShift(SYSTEM_ID))
        val z = checkNotNull(close.z_report)
        assertEquals(BfdMoney(bills = 0, coins = 0), z.cash_sum)
        val withdrawal = z.money_placements.single { it.operation == MoneyPlacementEnum.MONEY_PLACEMENT_WITHDRAWAL }
        assertEquals(BfdMoney(bills = 3500, coins = 0), withdrawal.operations_sum)
        val paper = kassa.storage.loadCounters(KKM, CounterScopes.SHIFT, shift.id)
        assertEquals(0L, paper[CounterKeyFormats.CASH_SUM])
        assertEquals(350_000L, paper[CounterKeyFormats.MONEY_PLACEMENT_SUM.format(WITHDRAWAL)])
        assertEquals(0L, kassa.storage.loadCounters(KKM, CounterScopes.GLOBAL, null)[CounterKeyFormats.CASH_SUM])
    }

    @Test
    fun `отклонённый БФД Z-отчёт смену не закрывает и наличные не изымает`() {
        kassa.settings(autoCashout = true)
        kassa.api.openShift(KKM, ADMIN_PIN)
        sell("3500")
        kassa.bfd.reject(CommandTypeEnum.COMMAND_CLOSE_SHIFT, INCORRECT_REQUEST_DATA)

        val refused = kassa.api.closeShift(KKM, CASHIER_PIN)

        assertEquals(DeliveryStatus.ONLINE_ERROR, refused.deliveryStatus)
        assertNotNull(kassa.storage.findOpenShift(KKM), "shift stays open while the BFD has not accepted its Z-report")
        assertEquals("FAILED" to INCORRECT_REQUEST_DATA, kassa.document(refused.documentId).let { it.ofdStatus to it.ofdErrorCode })
        assertEquals(350_000L, kassa.storage.loadCounters(KKM, CounterScopes.GLOBAL, null)[CounterKeyFormats.CASH_SUM])
        assertTrue(kassa.bfd.moneyPlacements().isEmpty())
    }

    @Test
    fun `после отказа смену можно закрыть снова, и изъятие ложится один раз`() {
        kassa.settings(autoCashout = true)
        kassa.api.openShift(KKM, ADMIN_PIN)
        sell("3500")
        kassa.bfd.reject(CommandTypeEnum.COMMAND_CLOSE_SHIFT, INCORRECT_REQUEST_DATA)
        kassa.api.closeShift(KKM, CASHIER_PIN)
        kassa.bfd.acceptAll()

        val accepted = kassa.api.closeShift(KKM, CASHIER_PIN)

        assertEquals(DeliveryStatus.ONLINE_OK, accepted.deliveryStatus, accepted.deliveryError?.ru)
        assertNull(kassa.storage.findOpenShift(KKM))
        assertEquals(mapOf(1 to 350_000L), kassa.bfd.withdrawnByShift(SYSTEM_ID))
        val z = checkNotNull(kassa.bfd.closeShifts().last().z_report)
        assertEquals(1, z.money_placements.single { it.operation == MoneyPlacementEnum.MONEY_PLACEMENT_WITHDRAWAL }.operations_count)
    }

    private fun sell(total: String) {
        kassa.api.createSellReceipt(
            KKM, CASHIER_PIN,
            ReceiptSellRequest(idempotencyKey = "sale-$total", items = listOf(item(total)), payments = listOf(cash(total)))
        )
    }

    private companion object {
        const val WITHDRAWAL = "MONEY_PLACEMENT_WITHDRAWAL"
        const val INCORRECT_REQUEST_DATA = 13
    }
}
