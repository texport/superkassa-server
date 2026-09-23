package io.github.texport.superkassa.jvm.storage.impl.parity

import kz.kazakhtelecom.proto.v203.MoneyPlacementEnum
import kz.kazakhtelecom.proto.v203.OperationTypeEnum
import kz.kazakhtelecom.proto.v203.PaymentTypeEnum
import kz.kazakhtelecom.proto.v203.Request
import kz.kazakhtelecom.proto.v203.TicketRequest
import kz.kazakhtelecom.proto.v203.Money as BfdMoney

/**
 * Наличные в ящике и смена кассы на стороне БФД, как у референса:
 * принятое изъятие ложится в текущую смену БФД, закрытие смены
 * с `withdraw_money` изымает весь остаток в закрываемую смену
 * и начинает следующую. Сдачи в проверках нет, и здесь она не учитывается.
 */
internal class BfdDrawer {
    private val withdrawn = mutableMapOf<Int, Long>()
    private var shift = 1
    private var cashTiyn = 0L

    /** Изъятия по сменам БФД, в тиынах: номер смены БФД — сумма. */
    @Synchronized
    fun withdrawnByShift(): Map<Int, Long> = withdrawn.toMap()

    /** Учитывает документ, который БФД только что принял. */
    @Synchronized
    fun apply(request: Request) {
        request.ticket?.let { cashTiyn += cashOf(it) }
        request.money_placement?.let { place(it.operation, tiyn(it.sum)) }
        request.close_shift?.let {
            if (it.withdraw_money == true && cashTiyn != 0L) place(MoneyPlacementEnum.MONEY_PLACEMENT_WITHDRAWAL, cashTiyn)
            shift += 1
        }
    }

    private fun place(operation: MoneyPlacementEnum, sum: Long) {
        if (operation == MoneyPlacementEnum.MONEY_PLACEMENT_WITHDRAWAL) {
            cashTiyn -= sum
            withdrawn[shift] = (withdrawn[shift] ?: 0L) + sum
        } else {
            cashTiyn += sum
        }
    }

    private fun cashOf(ticket: TicketRequest): Long {
        val cash = ticket.payments.filter { it.type == PaymentTypeEnum.PAYMENT_CASH }.sumOf { tiyn(it.sum) }
        val inflow = ticket.operation == OperationTypeEnum.OPERATION_SELL ||
            ticket.operation == OperationTypeEnum.OPERATION_BUY_RETURN
        return if (inflow) cash else -cash
    }

    private fun tiyn(money: BfdMoney): Long = money.bills * TIYN_IN_TENGE + money.coins

    private companion object {
        const val TIYN_IN_TENGE = 100L
    }
}
