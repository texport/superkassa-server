package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.common.VatGroup
import io.github.texport.superkassa.core.domain.api.model.receipt.PaymentType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptOperationType
import io.github.texport.superkassa.core.domain.api.model.common.TaxRegime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Перевод пакета протокола в то, что принимает рисовальщик. */
class ProtocolDocumentTest {

    private val kkm = ProtocolPackets.localKkm()

    private fun documentOf(body: String) =
        documentOf(
            requireNotNull(ProtocolPacket.of(body)),
            kkm.withProtocolRegistration(requireNotNull(ProtocolPacket.of(body)).service)
        )

    @Test
    fun `чек переводится составом, оплатами и итогами`() {
        val document = documentOf(ProtocolPackets.receipt()) as ProtocolDocument.Receipt
        val receipt = document.receipt

        assertEquals(ReceiptOperationType.SELL, receipt.operation)
        assertEquals(2, receipt.items.size)
        assertEquals("Хлеб", receipt.items[0].name)
        assertEquals(2000L, receipt.items[0].quantity)
        assertEquals(30_000L, receipt.items[0].sum.tiyn())
        assertEquals(VatGroup.VAT_16, receipt.items[0].vatGroup)
        assertEquals(listOf("AB-1"), receipt.items[0].listExciseStamp)
        assertEquals("NTIN-1", receipt.items[0].ntin)
        assertFalse(receipt.items[0].isStorno)
        assertTrue(receipt.items[1].isStorno)
        assertEquals(30_000L, receipt.total.tiyn())
        assertEquals(0L, receipt.change?.tiyn())
        assertEquals(listOf(PaymentType.CASH, PaymentType.CARD), receipt.payments.map { it.type })
        assertEquals(20_000L, receipt.payments[0].sum.tiyn())
        assertEquals("123456789012", receipt.customerBin)
        assertEquals("Кассир Алия", receipt.operatorName)
        assertEquals(TaxRegime.VAT_PAYER, receipt.taxRegime)
    }

    @Test
    fun `налог чека собирается по ставке, а облагаемый оборот считается без налога`() {
        val document = documentOf(ProtocolPackets.receipt()) as ProtocolDocument.Receipt
        val tax = document.receipt.ticketTaxes.orEmpty().single()

        assertEquals(VatGroup.VAT_16, tax.vatGroup)
        assertEquals(16, tax.percent)
        assertEquals(4138L, tax.taxSum.tiyn())
        assertEquals(30_000L - 4138L, tax.taxBase.tiyn())
    }

    @Test
    fun `скидка чека собирается из состава, когда её нет в итогах`() {
        val document = documentOf(ProtocolPackets.receipt()) as ProtocolDocument.Receipt

        assertEquals(1000L, document.receipt.discount?.tiyn())
        assertNull(document.receipt.markup)
    }

    @Test
    fun `у чека остаются номер, смена, момент, признак и ссылка на чек`() {
        val document = (documentOf(ProtocolPackets.receipt()) as ProtocolDocument.Receipt).document

        assertEquals("CHECK", document.docType)
        assertEquals(417L, document.docNo)
        assertEquals(12L, document.shiftNo)
        assertEquals("987654321012", document.fiscalSign)
        assertEquals(ProtocolPackets.RECEIPT_LINK, document.receiptUrl)
        assertEquals("DELIVERED", document.ofdStatus)
        assertEquals("KZT", document.currency)
        assertFalse(document.isAutonomous)
        // 18 сентября 2026 года, 14:53:07 по часам Казахстана
        assertEquals(1_789_725_187_000L, document.createdAt)
    }

    @Test
    fun `виды операции чека читаются все`() {
        val kinds = mapOf(
            "OPERATION_SELL_RETURN" to ReceiptOperationType.SELL_RETURN,
            "OPERATION_BUY" to ReceiptOperationType.BUY,
            "OPERATION_BUY_RETURN" to ReceiptOperationType.BUY_RETURN,
            "OPERATION_UNKNOWN" to ReceiptOperationType.SELL
        )
        kinds.forEach { (code, expected) ->
            val document = documentOf(ProtocolPackets.receipt(operation = code)) as ProtocolDocument.Receipt
            assertEquals(expected, document.receipt.operation, code)
        }
    }

    @Test
    fun `отказ ОФД оставляет чек непринятым`() {
        val refused = ProtocolPackets.receipt(response = ProtocolPackets.receiptAnswer(code = 15))
        val document = (documentOf(refused) as ProtocolDocument.Receipt).document

        assertEquals("FAILED", document.ofdStatus)
    }

    @Test
    fun `чек без ответа ОФД считается непереданным`() {
        val document = (documentOf(ProtocolPackets.receipt(response = "")) as ProtocolDocument.Receipt).document

        assertEquals("PENDING", document.ofdStatus)
        assertNull(document.fiscalSign)
        assertNull(document.receiptUrl)
    }

    @Test
    fun `X-отчёт переводится оборотами, налогами, оплатами и остатком ящика`() {
        val document = documentOf(ProtocolPackets.report()) as ProtocolDocument.Report
        val report = document.report

        assertFalse(document.closesShift)
        assertEquals("418", document.documentNumber)
        assertEquals("DELIVERED", document.ofdStatus)
        assertEquals(12, report.shiftNumber)
        assertEquals(1_500_025L, report.cashSumTiyn)
        assertEquals(-2_000_000L, report.revenueTiyn)
        assertEquals("001", report.sections.single().sectionCode)
        assertEquals(7L, report.operations.single().count)
        assertEquals(1_990_000L, report.totalResult.single().sumTiyn)
        assertEquals(10_000L, report.discounts.single().sumTiyn)
        assertEquals(10_000_000L, report.startShiftNonNullableSums.single().second)
        assertEquals(12_000_000L, report.nonNullableSums.single().second)
    }

    @Test
    fun `налог отчёта берёт ставку из пакета, а неизвестную ставку не печатает`() {
        val report = (documentOf(ProtocolPackets.report()) as ProtocolDocument.Report).report
        val tax = report.taxes.single()

        assertEquals("TAX_TYPE_VAT_16", tax.taxTypeCode)
        assertEquals(16_000, tax.percent)
        assertEquals(100, tax.taxType)
        assertEquals(275_862L, tax.operations.single().taxSumTiyn)
        assertEquals(1_724_138L, tax.operations.single().turnoverWithoutTaxTiyn)
    }

    @Test
    fun `итоги по чекам и движению денег читаются вместе с их умолчаниями`() {
        val report = (documentOf(ProtocolPackets.report()) as ProtocolDocument.Report).report
        val tickets = report.ticketOperations.single()

        assertEquals(8L, tickets.ticketsTotalCount)
        assertEquals(1L, tickets.offlineCount)
        assertEquals(4000L, tickets.changeSumTiyn)
        assertEquals(listOf("PAYMENT_CASH", "PAYMENT_CASH"), tickets.payments.map { it.payment })
        assertEquals(
            listOf("MONEY_PLACEMENT_DEPOSIT", "MONEY_PLACEMENT_DEPOSIT"),
            report.moneyPlacements.map { it.operation }
        )
    }

    @Test
    fun `Z-отчёт запроса отчёта помечается закрывающим смену`() {
        val document = documentOf(ProtocolPackets.report(kind = "REPORT_Z")) as ProtocolDocument.Report

        assertTrue(document.closesShift)
    }

    @Test
    fun `закрытие смены рисуется Z-отчётом из самого запроса`() {
        val document = documentOf(ProtocolPackets.closeShift()) as ProtocolDocument.Report

        assertTrue(document.closesShift)
        assertEquals("419", document.documentNumber)
        assertEquals(12, document.report.shiftNumber)
        assertNotNull(document.report.closeShiftTimeMillis)
    }

    @Test
    fun `итоги отчёта берутся из ответа, когда в запросе их нет`() {
        val fromRequest = documentOf(ProtocolPackets.report(totals = "")) as ProtocolDocument.Report
        val fromClose = documentOf(ProtocolPackets.closeShift(totals = "")) as ProtocolDocument.Report

        assertEquals(12, fromRequest.report.shiftNumber)
        assertEquals(12, fromClose.report.shiftNumber)
    }

    @Test
    fun `внесение и изъятие денег становятся кассовым ордером`() {
        val out = (documentOf(ProtocolPackets.placement()) as ProtocolDocument.CashOperation).document
        val deposit = documentOf(ProtocolPackets.placement("MONEY_PLACEMENT_DEPOSIT")) as ProtocolDocument.CashOperation

        assertEquals("CASH_OUT", out.docType)
        assertEquals(100_050L, out.totalAmount)
        assertEquals(420L, out.docNo)
        assertEquals(12L, out.shiftNo)
        assertEquals("CASH_IN", deposit.document.docType)
    }

    @Test
    fun `реквизиты документа берутся из пакета, а не у рисующей кассы`() {
        val packet = requireNotNull(ProtocolPacket.of(ProtocolPackets.receipt()))
        val drawnBy = kkm.withProtocolRegistration(packet.service)

        assertEquals("260940000021", drawnBy.id)
        assertEquals("NZ7700123456", drawnBy.registrationNumber)
        assertEquals("SW7700987654", drawnBy.factoryNumber)
        assertEquals("ТОО Пример", drawnBy.ofdServiceInfo?.orgTitle)
        assertEquals("123456789012", drawnBy.ofdServiceInfo?.orgIinOrBin)
        assertEquals("Алматы қ., Абай даңғылы, 1", drawnBy.ofdServiceInfo?.orgAddressKz)
    }

    @Test
    fun `пакет без служебного блока оставляет реквизиты рисующей кассы`() {
        val bare = """{"request": {"command": "COMMAND_TICKET"}}"""
        val packet = requireNotNull(ProtocolPacket.of(bare))

        assertEquals(kkm, kkm.withProtocolRegistration(packet.service))
        assertNull(documentOf(bare))
    }

    @Test
    fun `команда без документа и нечитаемый пакет отвечают пустотой`() {
        assertNull(documentOf("""{"request": {"command": "COMMAND_INFO"}}"""))
        assertNull(ProtocolPacket.of("не JSON вовсе"))
        assertNull(ProtocolPacket.of("""{"response": {"command": "COMMAND_TICKET"}}"""))
    }

    @Test
    fun `команда читается из ответа, когда запрос её не назвал`() {
        val packet = requireNotNull(
            ProtocolPacket.of("""{"request": {}, "response": {"command": "COMMAND_TICKET", "ticket": {}}}""")
        )

        assertEquals("COMMAND_TICKET", packet.command)
        assertEquals("DELIVERED", packet.ofdStatus)
    }
}
