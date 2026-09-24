package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.ADMIN_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.CASHIER_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KGD_NUMBER
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KKM
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.cash
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.item
import io.github.texport.superkassa.core.domain.api.model.common.CounterKeyFormats
import io.github.texport.superkassa.core.domain.api.model.common.CounterScopes
import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.github.texport.superkassa.core.presentation.api.model.kkm.CashOperationRequest
import io.github.texport.superkassa.core.presentation.api.model.kkm.ReceiptBrandingRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ParentTicketRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellReturnRequest
import io.github.texport.superkassa.testing.api.bfd.FakeBfd
import kz.kazakhtelecom.proto.v203.MoneyPlacementEnum
import kz.kazakhtelecom.proto.v203.Money as BfdMoney
import kz.kazakhtelecom.proto.v203.OperationTypeEnum
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Касса на хранилище узла ведёт себя фискально так же, как касса на Room
 * в ядре (те же сценарии там — RoomFiscalParityTest): суммы уходят
 * в БФД в тенге и тиынах, отчёт сходится с документами, номер документа
 * даёт БФД, открытие смены внутреннее, оформление чека не теряется.
 */
class JdbcFiscalParityTest {
    private val kassa = JdbcKassa()

    @Test
    fun `внесение 1 234,56 тенге уходит в БФД как 1234 тенге 56 тиын`() {
        kassa.api.openShift(KKM, ADMIN_PIN)

        kassa.api.cashIn(KKM, CASHIER_PIN, CashOperationRequest(Decimal.parse("1234.56"), "in-1"))

        assertEquals(1234L to 56, kassa.bfd.moneyPlacements().single().sum.let { it.bills to it.coins })
    }

    @Test
    fun `X-отчёт и пересчёт смены сходятся с суммами внесения, продажи, возврата и изъятия`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.api.cashIn(KKM, CASHIER_PIN, CashOperationRequest(Decimal.parse("1234.56"), "in-1"))
        val sale = sell("1500.50")
        returnOf(sale, "1500.50", "500.25")
        kassa.api.cashOut(KKM, CASHIER_PIN, CashOperationRequest(Decimal.parse("700.10"), "out-1"))

        kassa.api.createReport(KKM, CASHIER_PIN)

        val report = kassa.bfd.xReports().last()
        assertEquals(tenge(1534, 71), report.cash_sum)
        assertEquals(tenge(1234, 56), report.money_placements.single { it.operation == MoneyPlacementEnum.MONEY_PLACEMENT_DEPOSIT }.operations_sum)
        assertEquals(tenge(700, 10), report.money_placements.single { it.operation == MoneyPlacementEnum.MONEY_PLACEMENT_WITHDRAWAL }.operations_sum)
        assertEquals(tenge(1500, 50), report.ticket_operations.single { it.operation == OperationTypeEnum.OPERATION_SELL }.tickets_sum)
        assertEquals(tenge(500, 25), report.ticket_operations.single { it.operation == OperationTypeEnum.OPERATION_SELL_RETURN }.tickets_sum)
        val shift = checkNotNull(kassa.storage.findOpenShift(KKM))
        assertEquals(153_471L, kassa.storage.loadCounters(KKM, CounterScopes.SHIFT, shift.id)[CounterKeyFormats.CASH_SUM])
    }

    @Test
    fun `тип и номер документа как у узла - номер даёт БФД, у внесения его нет`() {
        kassa.api.openShift(KKM, ADMIN_PIN)
        kassa.api.cashIn(KKM, CASHIER_PIN, CashOperationRequest(Decimal.parse("100.00"), "in-1"))
        val sale = sell("1500.50")
        val refund = returnOf(sale, "1500.50", "500.25")

        val documents = kassa.shiftDocuments().associateBy { it.id }
        assertEquals("SALE" to FakeBfd.FIRST_TICKET_NUMBER, documents.getValue(sale).let { it.docType to it.docNo })
        assertEquals("RETURN" to FakeBfd.FIRST_TICKET_NUMBER + 1, documents.getValue(refund).let { it.docType to it.docNo })
        val cashIn = documents.values.single { it.docType == "CASH_IN" }
        assertEquals(10_000L to null, cashIn.totalAmount to cashIn.docNo)
        assertEquals(150_050L, documents.getValue(sale).totalAmount)
    }

    @Test
    fun `открытие смены внутреннее - в очередь не встаёт, отправки не ждёт, номера не имеет`() {
        val shift = kassa.api.openShift(KKM, ADMIN_PIN)

        val opening = kassa.document(checkNotNull(kassa.storage.findShiftById(shift.id)?.openDocumentId))
        assertEquals("INTERNAL", opening.ofdStatus)
        assertNull(opening.docNo)
        assertTrue(kassa.storage.listQueueTasksByCashbox(KKM, "OFFLINE", 100, 0).isEmpty())
        assertTrue(kassa.bfd.requests.isEmpty(), "opening a shift sends nothing to the BFD")
    }

    @Test
    fun `оформление чека переживает правку настроек кассы`() {
        kassa.api.enterProgramming(KKM, ADMIN_PIN)
        kassa.api.updateBrandingSettings(KKM, ADMIN_PIN, ReceiptBrandingRequest(headerMsg = "Рахмет!", paperWidthMm = 58))

        kassa.api.updateKkmSettings(KKM, ADMIN_PIN, autoCloseShift = true, autoCashout = false)
        kassa.api.exitProgramming(KKM, ADMIN_PIN)

        val branding = kassa.api.getKkm(KKM).branding
        assertEquals("Рахмет!" to 58, branding?.headerMsg to branding?.paperWidthMm)
    }

    private fun sell(total: String): String = kassa.api.createSellReceipt(
        KKM, CASHIER_PIN,
        ReceiptSellRequest(idempotencyKey = "sale-$total", items = listOf(item(total)), payments = listOf(cash(total)))
    ).documentId

    /** Возврат по чеку [saleId] на [saleTotal]; основание собирается из номера, данного БФД. */
    private fun returnOf(saleId: String, saleTotal: String, total: String): String {
        val sale = kassa.document(saleId)
        val basis = ParentTicketRequest(
            parentTicketNumber = checkNotNull(sale.docNo),
            parentTicketDateTime = Instant.ofEpochMilli(sale.createdAt).truncatedTo(ChronoUnit.SECONDS)
                .atOffset(ZoneOffset.UTC).toLocalDateTime().toString(),
            kgdKkmId = KGD_NUMBER,
            parentTicketTotal = Decimal.parse(saleTotal),
            parentTicketIsOffline = false
        )
        return kassa.api.createSellReturnReceipt(
            KKM, CASHIER_PIN,
            ReceiptSellReturnRequest(
                idempotencyKey = "return-$total", items = listOf(item(total)), payments = listOf(cash(total)), parentTicket = basis
            )
        ).documentId
    }

    private fun tenge(bills: Long, coins: Int) = BfdMoney(bills = bills, coins = coins)
}
