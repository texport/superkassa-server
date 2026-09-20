package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.common.Money
import io.github.texport.superkassa.core.domain.api.model.common.TaxRegime
import io.github.texport.superkassa.core.domain.api.model.common.VatGroup
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.model.receipt.ParentTicket
import io.github.texport.superkassa.core.domain.api.model.receipt.PaymentType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptItem
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptOperationType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptPayment
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptRequest
import io.github.texport.superkassa.core.domain.api.model.receipt.TaxLine
import kotlinx.serialization.json.JsonObject

/**
 * Чек протокола тем же, чем его видит рисовальщик.
 *
 * Состав, оплаты, итоги и налоги в протоколе те же самые, что касса
 * подавала рисовальщику при печати: протокол и есть слепок пробитого
 * чека. Поэтому перевод здесь — чтение полей, а не пересчёт.
 *
 * @param ticket блок `ticket` запроса.
 * @param kkm касса, которой рисуется документ.
 */
internal fun receiptOf(ticket: JsonObject, kkm: KkmInfo): ReceiptRequest {
    val items = ticket.children("items")
    val goods = items.mapNotNull(::goodOf)
    val amounts = ticket.child("amounts")
    val taxes = taxLinesOf(items)
    return ReceiptRequest(
        kkmId = kkm.id,
        // Пин в чеке не печатается и рисовальщику не нужен: документ уже
        // пробит, и права на это спрашивались на той кассе.
        pin = "",
        operation = operationOf(ticket.text("operation")),
        items = goods,
        payments = ticket.children("payments").map(::paymentOf),
        total = amounts?.money("total") ?: Money(0, 0),
        taken = amounts?.money("taken"),
        change = amounts?.money("change"),
        idempotencyKey = ticket.text("printedDocumentNumber").orEmpty(),
        parentTicket = ticket.child("parentTicket")?.let(::parentOf),
        taxRegime = if (taxes.isEmpty()) TaxRegime.NO_VAT else TaxRegime.VAT_PAYER,
        defaultVatGroup = taxes.firstOrNull()?.vatGroup,
        discount = modifierOf(amounts?.child("discount"), items, "discount"),
        markup = modifierOf(amounts?.child("markup"), items, "markup"),
        customerBin = ticket.child("extensionOptions")?.text("customerIinOrBin"),
        ticketTaxes = taxes,
        operatorName = ticket.child("operator")?.text("name")
    )
}

/** Вид операции чека; неизвестный — продажа, самый частый документ кассы. */
private fun operationOf(code: String?): ReceiptOperationType = when (code) {
    "OPERATION_SELL_RETURN" -> ReceiptOperationType.SELL_RETURN
    "OPERATION_BUY" -> ReceiptOperationType.BUY
    "OPERATION_BUY_RETURN" -> ReceiptOperationType.BUY_RETURN
    else -> ReceiptOperationType.SELL
}

/**
 * Позиция чека.
 *
 * Скидка и наценка приходят такими же строками состава, как товар, но
 * товаром не являются: их суммы уходят в итоги чека, а в список позиций
 * не попадают — иначе одна и та же скидка встанет дважды.
 */
private fun goodOf(item: JsonObject): ReceiptItem? {
    val storno = item.child("stornoCommodity")
    val body = item.child("commodity") ?: storno ?: return null
    val tax = body.children("taxes").firstOrNull()
    return ReceiptItem(
        name = body.text("name").orEmpty(),
        sectionCode = body.text("sectionCode").orEmpty(),
        quantity = body.number("quantity") ?: 0L,
        price = body.money("price") ?: Money(0, 0),
        sum = body.money("sum") ?: Money(0, 0),
        barcode = body.text("barcode"),
        vatGroup = tax?.let { vatGroupOf(it.number("percent")) },
        measureUnitCode = body.text("measureUnitCode"),
        listExciseStamp = body.texts("listExciseStamp").takeIf { it.isNotEmpty() },
        ntin = body.text("ntin"),
        isStorno = storno != null
    )
}

/** Оплата чека; неизвестный вид считается наличными. */
private fun paymentOf(payment: JsonObject): ReceiptPayment = ReceiptPayment(
    type = when (payment.text("type")) {
        "PAYMENT_CARD" -> PaymentType.CARD
        "PAYMENT_MOBILE" -> PaymentType.MOBILE
        "PAYMENT_ELECTRONIC" -> PaymentType.ELECTRONIC
        "PAYMENT_CREDIT" -> PaymentType.CREDIT
        "PAYMENT_TARE" -> PaymentType.TARE
        else -> PaymentType.CASH
    },
    sum = payment.money("sum") ?: Money(0, 0)
)

/** Исходный чек возврата. */
private fun parentOf(parent: JsonObject): ParentTicket = ParentTicket(
    parentTicketNumber = parent.number("parentTicketNumber") ?: 0L,
    // Имя поля с моментом исходного чека в схеме и у кассы разное:
    // читаются оба, иначе у половины возвратов пропадала бы дата.
    parentTicketDateTimeMillis = parent.moment("parentTicketDataTime")
        ?: parent.moment("parentTicketDateTime") ?: 0L,
    kgdKkmId = parent.text("kgdKkmId").orEmpty(),
    parentTicketTotal = parent.money("parentTicketTotal") ?: Money(0, 0),
    parentTicketIsOffline = parent.flag("parentTicketIsOffline")
)

/**
 * Скидка или наценка на весь чек.
 *
 * Итог чека несёт её отдельным полем; когда поля нет, она собирается
 * из строк состава того же вида — сумма получается та же самая.
 */
private fun modifierOf(total: JsonObject?, items: List<JsonObject>, field: String): Money? {
    total?.money("sum")?.let { return it }
    val parts = items.mapNotNull { it.child(field) }.sumOf { it.tiyn("sum") }
    return parts.takeIf { it != 0L }?.let(Money::fromTiyn)
}

/**
 * Налоги чека по ставкам.
 *
 * В протоколе налог стоит у каждой позиции; на чеке он печатается сводкой
 * по ставке. Сумма налога берётся из пакета, а не считается заново:
 * пересчёт разошёлся бы с кассой на тиын там, где она округлила иначе.
 *
 * Облагаемый оборот протокол не передаёт вовсе — это оборот позиций
 * этой ставки за вычетом самого налога. Сторно-позиция входит в оба
 * итога со знаком минус: она отменяет ранее пробитую.
 */
private fun taxLinesOf(items: List<JsonObject>): List<TaxLine> {
    val byRate = mutableMapOf<VatGroup, Pair<Long, Long>>()
    items.forEach { item ->
        val storno = item.child("stornoCommodity")
        val body = item.child("commodity") ?: storno ?: return@forEach
        val sign = if (storno != null) -1L else 1L
        body.children("taxes").forEach { line ->
            val group = vatGroupOf(line.number("percent")) ?: return@forEach
            val (turnover, tax) = byRate[group] ?: EMPTY_RATE
            byRate[group] = turnover + sign * body.tiyn("sum") to tax + sign * line.tiyn("sum")
        }
    }
    return byRate.map { (group, totals) ->
        val (turnover, tax) = totals
        TaxLine(
            vatGroup = group,
            percent = group.percent,
            taxBase = Money.fromTiyn(turnover - tax),
            taxSum = Money.fromTiyn(tax)
        )
    }
}

/** Ставка, по которой ещё ничего не набрано. */
private val EMPTY_RATE = 0L to 0L

/** Ставка НДС по доле процента, как её передаёт протокол. */
internal fun vatGroupOf(percentThousandths: Long?): VatGroup? {
    val percent = percentThousandths ?: return null
    return VatGroup.entries.firstOrNull { it != VatGroup.NO_VAT && it.percentThousandths.toLong() == percent }
}
