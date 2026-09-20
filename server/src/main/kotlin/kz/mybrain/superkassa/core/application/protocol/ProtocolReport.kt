package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.zxreport.MoneyPlacementAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.OperationAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.SectionAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.TaxAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.TaxOperationAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.TicketOperationAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.TicketPaymentAggregate
import io.github.texport.superkassa.core.domain.api.model.zxreport.ZxReportInput
import kotlinx.serialization.json.JsonObject

/**
 * Сменный отчёт протокола тем же, чем его видит рисовальщик.
 *
 * Своя касса собирает отчёт из счётчиков смены; в протоколе он уже
 * собран — обороты, налоги, оплаты, движение денег и остаток ящика
 * стоят готовыми числами. Поэтому счётчики здесь не восстанавливаются:
 * обратный пересчёт потерял бы то, чего в счётчиках нет, — итог смены
 * и ставку налога чужой кассы.
 *
 * @param report блок `zxReport` запроса отчёта или `zReport` закрытия смены.
 */
internal fun reportOf(report: JsonObject): ZxReportInput = ZxReportInput(
    dateTimeMillis = report.moment("dateTime") ?: 0L,
    shiftNumber = (report.number("shiftNumber") ?: 0L).toInt(),
    openShiftTimeMillis = report.moment("openShiftTime") ?: 0L,
    closeShiftTimeMillis = report.moment("closeShiftTime"),
    cashSumTiyn = report.tiyn("cashSum"),
    revenueTiyn = revenueOf(report),
    revenueCoins = 0,
    nonNullableSums = report.children("nonNullableSums").map(::sumOf),
    startShiftNonNullableSums = report.children("startShiftNonNullableSums").map(::sumOf),
    sections = report.children("sections").map(::sectionOf),
    operations = report.children("operations").map(::operationOf),
    discounts = report.children("discounts").map(::operationOf),
    markups = report.children("markups").map(::operationOf),
    totalResult = report.children("totalResult").map(::operationOf),
    ticketOperations = report.children("ticketOperations").map(::ticketOperationOf),
    moneyPlacements = report.children("moneyPlacements").map(::placementOf),
    taxes = report.children("taxes").mapNotNull(::taxOf)
)

/** Выручка смены: протокол передаёт её по модулю, знак — отдельным признаком. */
private fun revenueOf(report: JsonObject): Long {
    val revenue = report.child("revenue") ?: return 0L
    val sum = revenue.tiyn("sum")
    return if (revenue.flag("isNegative")) -sum else sum
}

/** Накопительный итог по виду операции. */
private fun sumOf(entry: JsonObject): Pair<String, Long> =
    operationName(entry) to entry.tiyn("sum")

/** Количество и сумма операций одного вида. */
private fun operationOf(entry: JsonObject): OperationAggregate = OperationAggregate(
    operation = operationName(entry),
    count = entry.number("count") ?: 0L,
    sumTiyn = entry.tiyn("sum")
)

/** Итоги отдела. */
private fun sectionOf(entry: JsonObject): SectionAggregate = SectionAggregate(
    sectionCode = entry.text("sectionCode").orEmpty(),
    operations = entry.children("operations").map(::operationOf)
)

/** Итоги по чекам одного вида операции вместе с разбивкой по оплатам. */
private fun ticketOperationOf(entry: JsonObject): TicketOperationAggregate = TicketOperationAggregate(
    operation = operationName(entry),
    ticketsTotalCount = entry.number("ticketsTotalCount") ?: 0L,
    ticketsCount = entry.number("ticketsCount") ?: 0L,
    ticketsSumTiyn = entry.tiyn("ticketsSum"),
    payments = entry.children("payments").map { payment ->
        TicketPaymentAggregate(
            payment = payment.text("payment") ?: DEFAULT_PAYMENT,
            sumTiyn = payment.tiyn("sum"),
            count = payment.number("count") ?: 0L
        )
    },
    offlineCount = entry.number("offlineCount") ?: 0L,
    discountSumTiyn = entry.tiyn("discountSum"),
    markupSumTiyn = entry.tiyn("markupSum"),
    changeSumTiyn = entry.tiyn("changeSum")
)

/** Внесение или изъятие денег за смену. */
private fun placementOf(entry: JsonObject): MoneyPlacementAggregate = MoneyPlacementAggregate(
    operation = entry.text("operation") ?: DEFAULT_PLACEMENT,
    operationsTotalCount = entry.number("operationsTotalCount") ?: 0L,
    operationsCount = entry.number("operationsCount") ?: 0L,
    operationsSumTiyn = entry.tiyn("operationsSum"),
    offlineCount = entry.number("offlineCount") ?: 0L
)

/**
 * Налог смены по ставке.
 *
 * Вид налога в протоколе один — НДС, — и ставки различает доля процента.
 * Ставка, которой у кассы нет, из отчёта выпадает: подписать её нечем,
 * и строка встала бы в отчёт без названия.
 */
private fun taxOf(entry: JsonObject): TaxAggregate? {
    val group = vatGroupOf(entry.number("percent")) ?: return null
    return TaxAggregate(
        taxType = entry.number("taxType")?.toInt() ?: VAT,
        taxTypeCode = group.taxTypeCode,
        percent = group.percentThousandths,
        operations = entry.children("operations").map { operation ->
            TaxOperationAggregate(
                operation = operationName(operation),
                turnoverTiyn = operation.tiyn("turnover"),
                turnoverWithoutTaxTiyn = operation.tiyn("turnoverWithoutTax"),
                taxSumTiyn = operation.tiyn("sum")
            )
        }
    )
}

/** Вид операции строки отчёта; без него строку не подписать. */
private fun operationName(entry: JsonObject): String = entry.text("operation") ?: DEFAULT_OPERATION

/** Числовой код налога на добавленную стоимость в протоколе. */
private const val VAT = 100

private const val DEFAULT_OPERATION = "OPERATION_SELL"
private const val DEFAULT_PAYMENT = "PAYMENT_CASH"
private const val DEFAULT_PLACEMENT = "MONEY_PLACEMENT_DEPOSIT"
