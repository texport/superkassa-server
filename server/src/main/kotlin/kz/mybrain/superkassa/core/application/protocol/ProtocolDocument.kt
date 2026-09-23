package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.kkm.FiscalDocumentSnapshot
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptLayoutType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptRequest
import io.github.texport.superkassa.core.domain.api.model.zxreport.ZxReportInput
import io.github.texport.superkassa.core.domain.api.port.internal.ReceiptRenderPort
import kotlinx.serialization.json.JsonObject
import java.util.Base64

/**
 * Документ пакета, разобранный до того, что принимает рисовальщик.
 *
 * Видов ровно столько, сколько команд протокола порождают документ:
 * чек, сменный отчёт и движение денег в ящике. Всё остальное документом
 * не является и печатной формы не имеет.
 */
internal sealed interface ProtocolDocument {

    /**
     * Рисует себя тем же рисовальщиком, каким касса рисует свои документы.
     *
     * Какой вход рисовальщика выбрать, знает сам документ: чек, отчёт
     * и кассовый ордер рисуются по-разному, и разбирать это на стороне
     * вызова значило бы держать один и тот же выбор в двух местах.
     *
     * @param renderer рисовальщик печатных форм узла.
     * @param kkm касса, которой рисуется документ.
     * @param layout ширина ленты; не задана — та, что настроена у кассы.
     * @return HTML печатной формы.
     */
    fun draw(renderer: ReceiptRenderPort, kkm: KkmInfo, layout: ReceiptLayoutType?): String

    /** Торговый чек: продажа, покупка и возвраты по ним. */
    class Receipt(val receipt: ReceiptRequest, val document: FiscalDocumentSnapshot) : ProtocolDocument {
        override fun draw(renderer: ReceiptRenderPort, kkm: KkmInfo, layout: ReceiptLayoutType?): String =
            renderer.renderHtml(receipt, document, kkm, layout)
    }

    /** X- или Z-отчёт смены. */
    class Report(
        val report: ZxReportInput,
        val closesShift: Boolean,
        val documentNumber: String?,
        val ofdStatus: String
    ) : ProtocolDocument {
        override fun draw(renderer: ReceiptRenderPort, kkm: KkmInfo, layout: ReceiptLayoutType?): String =
            if (closesShift) {
                renderer.renderCloseShiftHtml(report, kkm, ofdStatus, documentNumber, layout)
            } else {
                renderer.renderXReportHtml(report, kkm, ofdStatus, documentNumber, layout)
            }
    }

    /** Внесение денег в ящик или изъятие из него. */
    class CashOperation(val document: FiscalDocumentSnapshot) : ProtocolDocument {
        override fun draw(renderer: ReceiptRenderPort, kkm: KkmInfo, layout: ReceiptLayoutType?): String =
            renderer.renderCashOperationHtml(document, kkm, layout)
    }
}

/**
 * Разбирает пакет в документ.
 *
 * Вид документа решает команда: она же решала это и на кассе, когда
 * документ печатался там.
 *
 * @param packet пакет протокола.
 * @param kkm касса, которой рисуется документ, с реквизитами из пакета.
 * @return документ либо `null`, если команда пакета документа не порождает.
 */
internal fun documentOf(packet: ProtocolPacket, kkm: KkmInfo): ProtocolDocument? = when (packet.command) {
    "COMMAND_TICKET" -> packet.request.child("ticket")?.let { ticketOf(it, packet, kkm) }
    "COMMAND_REPORT" -> packet.request.child("report")?.let { reportDocumentOf(it, packet) }
    "COMMAND_CLOSE_SHIFT" -> packet.request.child("closeShift")?.let { closeShiftOf(it, packet) }
    "COMMAND_MONEY_PLACEMENT" -> packet.request.child("moneyPlacement")?.let { placementOf(it, packet, kkm) }
    else -> null
}

/** Чек вместе с тем, что ответил о нём ОФД. */
private fun ticketOf(ticket: JsonObject, packet: ProtocolPacket, kkm: KkmInfo): ProtocolDocument.Receipt {
    val receipt = receiptOf(ticket, kkm)
    val answer = packet.response?.child("ticket")
    val offlineNumber = ticket.number("offlineTicketNumber")
    return ProtocolDocument.Receipt(
        receipt,
        snapshotOf(
            ticket = ticket,
            kkm = kkm,
            type = "CHECK",
            totalTiyn = receipt.total.tiyn(),
            ofdStatus = packet.ofdStatus
        ).copy(
            fiscalSign = answer?.text("ticketNumber"),
            autonomousSign = offlineNumber?.toString(),
            isAutonomous = offlineNumber != null,
            receiptUrl = answer?.let(::receiptLinkOf)
        )
    )
}

/** Отчёт: вид берётся из запроса, сами итоги — из вложенного отчёта. */
private fun reportDocumentOf(report: JsonObject, packet: ProtocolPacket): ProtocolDocument.Report? {
    val totals = report.child("zxReport")
        ?: packet.response?.child("report")?.child("zxReport")
        ?: return null
    return ProtocolDocument.Report(
        report = reportOf(totals),
        closesShift = report.text("report") != "REPORT_X",
        documentNumber = report.text("printedDocumentNumber"),
        ofdStatus = packet.ofdStatus
    )
}

/** Закрытие смены: Z-отчёт лежит в самом запросе закрытия. */
private fun closeShiftOf(close: JsonObject, packet: ProtocolPacket): ProtocolDocument.Report? {
    val totals = close.child("zReport")
        ?: packet.response?.child("report")?.child("zxReport")
        ?: return null
    return ProtocolDocument.Report(
        report = reportOf(totals),
        closesShift = true,
        documentNumber = close.text("printedDocumentNumber"),
        ofdStatus = packet.ofdStatus
    )
}

/** Движение денег в ящике: документ нефискальный, и признака у него нет. */
private fun placementOf(
    placement: JsonObject,
    packet: ProtocolPacket,
    kkm: KkmInfo
): ProtocolDocument.CashOperation = ProtocolDocument.CashOperation(
    snapshotOf(
        ticket = placement,
        kkm = kkm,
        type = if (placement.text("operation") == "MONEY_PLACEMENT_WITHDRAWAL") "CASH_OUT" else "CASH_IN",
        totalTiyn = placement.tiyn("sum"),
        ofdStatus = packet.ofdStatus
    )
)

/**
 * Общая часть документа: номер, смена, момент, сумма и реквизиты кассы.
 *
 * Смена как запись этой машины сюда не попадает: документ пробит
 * не здесь, и связывать его с чужой сменой нечем. Номер смены при этом
 * есть — он приходит в самом документе и печатается в шапке.
 */
private fun snapshotOf(
    ticket: JsonObject,
    kkm: KkmInfo,
    type: String,
    totalTiyn: Long,
    ofdStatus: String
): FiscalDocumentSnapshot {
    val number = ticket.number("printedDocumentNumber")
    return FiscalDocumentSnapshot(
        id = number?.toString().orEmpty(),
        cashboxId = kkm.id,
        shiftId = "",
        docType = type,
        docNo = number,
        printedDocumentNumber = number,
        shiftNo = ticket.number("frShiftNumber"),
        createdAt = ticket.moment("dateTime") ?: 0L,
        totalAmount = totalTiyn,
        currency = CURRENCY,
        fiscalSign = null,
        autonomousSign = null,
        isAutonomous = false,
        ofdStatus = ofdStatus,
        deliveredAt = null,
        registrationNumber = kkm.registrationNumber,
        taxpayerName = kkm.ofdServiceInfo?.orgTitle,
        taxpayerBin = kkm.ofdServiceInfo?.orgIinOrBin,
        taxpayerAddress = kkm.ofdServiceInfo?.orgAddress,
        factoryNumber = kkm.factoryNumber
    )
}

/**
 * Ссылка на чек у ОФД.
 *
 * В протоколе она лежит двоичным полем, и в JSON попадает записанной
 * по основанию 64. Раскодированное берётся, только если это ссылка:
 * иначе под QR-кодом чека встала бы строка случайных байтов.
 */
private fun receiptLinkOf(answer: JsonObject): String? {
    val raw = answer.text("qrCode") ?: answer.text("qrCodeBase64") ?: return null
    val decoded = runCatching { String(Base64.getDecoder().decode(raw), Charsets.UTF_8) }.getOrNull()
    return decoded?.takeIf { it.startsWith(LINK) } ?: raw.takeIf { it.startsWith(LINK) }
}

/** Начало ссылки на чек: по нему раскодированное отличается от мусора. */
private const val LINK = "http"

/** Валюта документа: тенге и его сотая доля — тиын. */
private const val CURRENCY = "KZT"
