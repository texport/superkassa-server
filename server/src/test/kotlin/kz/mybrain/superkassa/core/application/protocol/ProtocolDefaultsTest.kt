package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.common.VatGroup
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdServiceInfo
import io.github.texport.superkassa.core.domain.api.model.receipt.PaymentType
import io.github.texport.superkassa.core.domain.api.model.common.TaxRegime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Чего в пакете нет.
 *
 * Необязательное поле протокол не пишет вовсе, и у документа с чужой
 * кассы не бывает половины того, что есть у своего. Каждое такое место
 * обязано отвечать пустотой, а не падением: на экране владельца падение
 * разбора выглядит как сломанный кабинет.
 */
class ProtocolDefaultsTest {

    private val kkm = ProtocolPackets.localKkm()

    private fun read(body: String): JsonObject = Json.parseToJsonElement(body) as JsonObject

    private fun documentOf(body: String): ProtocolDocument? {
        val packet = requireNotNull(ProtocolPacket.of(body))
        return documentOf(packet, kkm.withProtocolRegistration(packet.service))
    }

    @Test
    fun `пустые поля читаются пустотой, а не падением`() {
        val empty = read("""{"nothing": null, "text": "", "wrong": [1, "два", {"a": 1}]}""")

        assertNull(empty.child("nothing"))
        assertTrue(empty.children("nothing").isEmpty())
        assertEquals(listOf("1", "два"), empty.texts("wrong"))
        assertEquals(1, empty.children("wrong").size)
        assertNull(empty.text("text"))
        assertNull(empty.text("nothing"))
        assertNull(empty.number("text"))
        assertFalse(empty.flag("nothing"))
        assertNull(empty.money("nothing"))
        assertEquals(0L, empty.tiyn("nothing"))
    }

    @Test
    fun `сумма без частей и признак строкой читаются как есть`() {
        val values = read("""{"sum": {}, "yes": "true", "no": false}""")

        assertEquals(0L, values.tiyn("sum"))
        assertTrue(values.flag("yes"))
        assertFalse(values.flag("no"))
    }

    @Test
    fun `момент без части даты не собирается`() {
        val moments = read(
            """
            {
              "whole": {"date": {"year": 2026, "month": 9, "day": 18}, "time": {"hour": 1}},
              "noDate": {"time": {"hour": 1}},
              "noYear": {"date": {"month": 9, "day": 18}},
              "noMonth": {"date": {"year": 2026, "day": 18}},
              "noDay": {"date": {"year": 2026, "month": 9}}
            }
            """.trimIndent()
        )

        assertEquals(1_789_675_200_000L, moments.moment("whole"))
        assertNull(moments.moment("missing"))
        assertNull(moments.moment("noDate"))
        assertNull(moments.moment("noYear"))
        assertNull(moments.moment("noMonth"))
        assertNull(moments.moment("noDay"))
        assertEquals(1_789_671_600_000L, read("""{"dayOnly": {"date": {"year": 2026, "month": 9, "day": 18}}}""").moment("dayOnly"))
    }

    @Test
    fun `реквизиты берутся по частям, а известное о кассе не теряется`() {
        val known = kkm.copy(
            ofdServiceInfo = OfdServiceInfo(
                orgTitle = "ТОО Прежнее", orgAddress = "Астана", orgAddressKz = "Астана қ.",
                orgIinOrBin = "000000000000", orgOked = "00000",
                geoLatitude = 43, geoLongitude = 76, geoSource = "GPS"
            )
        )
        val bare = known.withProtocolRegistration(read("""{"regInfo": {"org": {}}}"""))

        assertEquals(kkm.id, bare.id)
        assertEquals(kkm.registrationNumber, bare.registrationNumber)
        assertEquals(kkm.factoryNumber, bare.factoryNumber)
        assertEquals("ТОО Прежнее", bare.ofdServiceInfo?.orgTitle)
        assertEquals(43, bare.ofdServiceInfo?.geoLatitude)
        assertEquals(76, bare.ofdServiceInfo?.geoLongitude)
        assertEquals("GPS", bare.ofdServiceInfo?.geoSource)
    }

    @Test
    fun `реквизиты без сведений о кассе остаются пустыми строками`() {
        val bare = kkm.withProtocolRegistration(read("""{"regInfo": {"org": {}}}"""))

        assertEquals("", bare.ofdServiceInfo?.orgTitle)
        assertEquals("", bare.ofdServiceInfo?.orgAddressKz)
        assertEquals("", bare.ofdServiceInfo?.orgOked)
        assertEquals("", bare.ofdServiceInfo?.geoSource)
        assertEquals(kkm.copy(ofdServiceInfo = bare.ofdServiceInfo), bare)
    }

    @Test
    fun `служебный блок без реквизитов и без налогоплательщика ничего не выдумывает`() {
        val onlyDevice = kkm.withProtocolRegistration(read("""{"regInfo": {"kkm": {"kkmId": "77"}}}"""))

        assertEquals(kkm, kkm.withProtocolRegistration(read("""{"other": 1}""")))
        assertEquals("77", onlyDevice.id)
        assertNull(onlyDevice.ofdServiceInfo)
    }

    @Test
    fun `чек без единого необязательного поля собирается пустым`() {
        val document = documentOf("""{"request": {"command": "COMMAND_TICKET", "ticket": {}}}""")
            as ProtocolDocument.Receipt

        assertTrue(document.receipt.items.isEmpty())
        assertTrue(document.receipt.payments.isEmpty())
        assertEquals(0L, document.receipt.total.tiyn())
        assertNull(document.receipt.taken)
        assertNull(document.receipt.change)
        assertNull(document.receipt.discount)
        assertNull(document.receipt.parentTicket)
        assertNull(document.receipt.customerBin)
        assertNull(document.receipt.operatorName)
        assertNull(document.receipt.defaultVatGroup)
        assertEquals(TaxRegime.NO_VAT, document.receipt.taxRegime)
        assertEquals("", document.receipt.idempotencyKey)
        assertEquals("", document.document.id)
        assertNull(document.document.docNo)
        assertNull(document.document.shiftNo)
        assertEquals(0L, document.document.createdAt)
    }

    @Test
    fun `итоги без сумм и позиция без полей не мешают чеку`() {
        val document = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {
              "amounts": {},
              "items": [
                {"type": "ITEM_TYPE_COMMODITY", "commodity": {}},
                {"type": "ITEM_TYPE_MARKUP", "markup": {"sum": {"bills": "5", "coins": 0}}}
              ],
              "payments": [{}]
            }}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt
        val item = document.receipt.items.single()

        assertEquals("", item.name)
        assertEquals("", item.sectionCode)
        assertEquals(0L, item.quantity)
        assertEquals(0L, item.price.tiyn())
        assertNull(item.vatGroup)
        assertNull(item.listExciseStamp)
        assertFalse(item.isStorno)
        assertEquals(PaymentType.CASH, document.receipt.payments.single().type)
        assertEquals(0L, document.receipt.payments.single().sum.tiyn())
        assertEquals(500L, document.receipt.markup?.tiyn())
    }

    @Test
    fun `скидка и наценка из итогов чека берутся прежде состава`() {
        val document = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {
              "amounts": {"discount": {"sum": ${ProtocolPackets.money(3)}},
                          "markup": {"sum": ${ProtocolPackets.money(4)}}},
              "items": [{"type": "ITEM_TYPE_DISCOUNT", "discount": {"sum": ${ProtocolPackets.money(9)}}}]
            }}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt

        assertEquals(300L, document.receipt.discount?.tiyn())
        assertEquals(400L, document.receipt.markup?.tiyn())
    }

    @Test
    fun `все виды оплаты протокола узнаются`() {
        val kinds = mapOf(
            "PAYMENT_CASH" to PaymentType.CASH,
            "PAYMENT_CARD" to PaymentType.CARD,
            "PAYMENT_MOBILE" to PaymentType.MOBILE,
            "PAYMENT_ELECTRONIC" to PaymentType.ELECTRONIC,
            "PAYMENT_CREDIT" to PaymentType.CREDIT,
            "PAYMENT_TARE" to PaymentType.TARE
        )
        val payments = kinds.keys.joinToString(",") { """{"type": "$it"}""" }
        val document = documentOf(
            """{"request": {"command": "COMMAND_TICKET", "ticket": {"payments": [$payments]}}}"""
        ) as ProtocolDocument.Receipt

        assertEquals(kinds.values.toList(), document.receipt.payments.map { it.type })
    }

    @Test
    fun `исходный чек возврата читается и пустым, и под именем поля кассы`() {
        val bare = documentOf(
            """{"request": {"command": "COMMAND_TICKET", "ticket": {"parentTicket": {}}}}"""
        ) as ProtocolDocument.Receipt
        val kassaName = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {"parentTicket": {
              "parentTicketNumber": "77", "kgdKkmId": "260940000021", "parentTicketIsOffline": true,
              "parentTicketDateTime": ${ProtocolPackets.moment()}, "parentTicketTotal": ${ProtocolPackets.money(10)}
            }}}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt

        assertEquals(0L, bare.receipt.parentTicket?.parentTicketNumber)
        assertEquals(0L, bare.receipt.parentTicket?.parentTicketDateTimeMillis)
        assertEquals("", bare.receipt.parentTicket?.kgdKkmId)
        assertEquals(0L, bare.receipt.parentTicket?.parentTicketTotal?.tiyn())
        assertFalse(bare.receipt.parentTicket?.parentTicketIsOffline ?: true)
        val schemaName = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {"parentTicket": {
              "parentTicketDataTime": ${ProtocolPackets.moment()}
            }}}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt

        assertEquals(1_789_725_187_000L, schemaName.receipt.parentTicket?.parentTicketDateTimeMillis)
        assertEquals(77L, kassaName.receipt.parentTicket?.parentTicketNumber)
        assertEquals(1_789_725_187_000L, kassaName.receipt.parentTicket?.parentTicketDateTimeMillis)
        assertTrue(kassaName.receipt.parentTicket?.parentTicketIsOffline ?: false)
    }

    @Test
    fun `сторно уменьшает и оборот, и налог своей ставки`() {
        val rate = """"taxes": [{"percent": 16000, "sum": ${ProtocolPackets.money(16)}}]"""
        val document = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {"items": [
              {"type": "ITEM_TYPE_COMMODITY", "commodity": {"sum": ${ProtocolPackets.money(116)}, $rate}},
              {"type": "ITEM_TYPE_STORNO_COMMODITY", "stornoCommodity": {"sum": ${ProtocolPackets.money(116)}, $rate}},
              {"type": "ITEM_TYPE_COMMODITY", "commodity": {"sum": ${ProtocolPackets.money(10)},
                "taxes": [{"percent": 9999}]}}
            ]}}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt

        assertEquals(VatGroup.VAT_16, document.receipt.ticketTaxes?.single()?.vatGroup)
        assertEquals(0L, document.receipt.ticketTaxes?.single()?.taxSum?.tiyn())
        assertEquals(0L, document.receipt.ticketTaxes?.single()?.taxBase?.tiyn())
    }

    @Test
    fun `отчёт без единой строки собирается пустым`() {
        val document = documentOf(
            """{"request": {"command": "COMMAND_REPORT", "report": {"zxReport": {}}}}"""
        ) as ProtocolDocument.Report

        assertEquals(0, document.report.shiftNumber)
        assertEquals(0L, document.report.dateTimeMillis)
        assertEquals(0L, document.report.openShiftTimeMillis)
        assertNull(document.report.closeShiftTimeMillis)
        assertEquals(0L, document.report.cashSumTiyn)
        assertEquals(0L, document.report.revenueTiyn)
        assertTrue(document.report.taxes.isEmpty())
        assertTrue(document.report.ticketOperations.isEmpty())
        assertNull(document.documentNumber)
        assertTrue(document.closesShift)
    }

    @Test
    fun `строки отчёта без полей читаются нулями и умолчаниями`() {
        val document = documentOf(
            """
            {"request": {"command": "COMMAND_REPORT", "report": {"report": "REPORT_X", "zxReport": {
              "revenue": {"sum": ${ProtocolPackets.money(7)}},
              "operations": [{}], "sections": [{"operations": [{}]}],
              "nonNullableSums": [{}],
              "ticketOperations": [{"payments": [{}]}],
              "moneyPlacements": [{}],
              "taxes": [{"percent": 16000, "operations": [{}]}, {}]
            }}}}
            """.trimIndent()
        ) as ProtocolDocument.Report
        val report = document.report

        assertEquals(700L, report.revenueTiyn)
        assertEquals("OPERATION_SELL", report.operations.single().operation)
        assertEquals(0L, report.operations.single().count)
        assertEquals("", report.sections.single().sectionCode)
        assertEquals("OPERATION_SELL" to 0L, report.nonNullableSums.single())
        assertEquals(0L, report.ticketOperations.single().ticketsTotalCount)
        assertEquals("PAYMENT_CASH", report.ticketOperations.single().payments.single().payment)
        assertEquals("MONEY_PLACEMENT_DEPOSIT", report.moneyPlacements.single().operation)
        assertEquals(100, report.taxes.single().taxType)
        assertEquals(0L, report.taxes.single().operations.single().turnoverTiyn)
    }

    @Test
    fun `пакет без документа внутри команды не рисуется`() {
        assertNull(documentOf("""{"request": {"command": "COMMAND_TICKET"}}"""))
        assertNull(documentOf("""{"request": {"command": "COMMAND_REPORT"}}"""))
        assertNull(documentOf("""{"request": {"command": "COMMAND_REPORT", "report": {}}}"""))
        assertNull(documentOf("""{"request": {"command": "COMMAND_CLOSE_SHIFT"}}"""))
        assertNull(documentOf("""{"request": {"command": "COMMAND_CLOSE_SHIFT", "closeShift": {}}}"""))
        assertNull(documentOf("""{"request": {"command": "COMMAND_MONEY_PLACEMENT"}}"""))
    }

    @Test
    fun `ссылка на чек берётся, только когда она ссылка`() {
        val plain = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {}},
             "response": {"ticket": {"qrCodeBase64": "https://consumer.oofd.kz/r/1"}}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt
        val garbage = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {}},
             "response": {"ticket": {"qrCode": "0J/RgNC40LLQtdGCLCDQvNC40YA="}}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt

        val silent = documentOf(
            """
            {"request": {"command": "COMMAND_TICKET", "ticket": {}},
             "response": {"ticket": {"ticketNumber": "1"}}}
            """.trimIndent()
        ) as ProtocolDocument.Receipt

        assertEquals("https://consumer.oofd.kz/r/1", plain.document.receiptUrl)
        assertNull(garbage.document.receiptUrl)
        assertNull(silent.document.receiptUrl)
    }

    @Test
    fun `автономный чек опознаётся своим номером`() {
        val document = documentOf(
            """{"request": {"command": "COMMAND_TICKET", "ticket": {"offlineTicketNumber": 42}}}"""
        ) as ProtocolDocument.Receipt

        assertTrue(document.document.isAutonomous)
        assertEquals("42", document.document.autonomousSign)
    }

    @Test
    fun `пакет без запроса и не объектом вовсе не читается`() {
        assertNull(ProtocolPacket.of("[1, 2]"))
        assertNull(ProtocolPacket.of("{"))
        assertEquals("", requireNotNull(ProtocolPacket.of("""{"request": {}}""")).command)
    }
}
