package io.github.texport.superkassa.jvm.storage.impl.parity

import kz.kazakhtelecom.proto.v203.CommandTypeEnum
import kz.kazakhtelecom.proto.v203.ReportResponse
import kz.kazakhtelecom.proto.v203.ReportTypeEnum
import kz.kazakhtelecom.proto.v203.Request
import kz.kazakhtelecom.proto.v203.Response
import kz.kazakhtelecom.proto.v203.TicketResponse
import kz.kazakhtelecom.proto.v203.Result as BfdResult

/**
 * Учёт кассы на стороне БФД — правила токена и номера запроса, как в
 * прод-референсе (`FiscalCommand`, `validateNotFiscalCommand`).
 *
 * Документ (чек, деньги, закрытие смены) принимается, только если токен
 * равен выданному в прошлый раз, а номер запроса новый; тогда выдаётся
 * новый токен. Тот же токен, тот же номер и та же команда — повтор:
 * возвращается прежний ответ, документ второй раз не учитывается.
 * Служебные команды проверяют только токен и его не меняют.
 */
internal class BfdLedger(initialToken: Long) {
    /** Токен, выданный кассе последним: с ним касса обязана прийти. */
    var issuedToken: Long = initialToken
        private set

    private var lastToken = 0L
    private var lastReqNum = -1
    private var lastCommand: CommandTypeEnum? = null
    private var lastAnswer: Response? = null
    private var nextTicket = FakeBfd.FIRST_TICKET_NUMBER
    private var nextToken = FIRST_ISSUED_TOKEN
    private val counted = mutableListOf<Request>()

    /** Ящик и смена кассы, как их видит БФД. */
    val drawer = BfdDrawer()

    /** Документы, учтённые БФД, — каждый ровно столько раз, сколько он учтён. */
    val accepted: List<Request> @Synchronized get() = counted.toList()

    /** Ответ и токен в его заголовке. */
    @Synchronized
    fun answer(token: Long, reqNum: Int, request: Request): Pair<Long, Response> = when (request.command) {
        in DOCUMENTS -> document(token, reqNum, request)
        CommandTypeEnum.COMMAND_REPORT -> issuedToken to accepted(request)
        else -> if (token == issuedToken && token != 0L) token to accepted(request) else token to refusal(request, INVALID_TOKEN)
    }

    private fun document(token: Long, reqNum: Int, request: Request): Pair<Long, Response> {
        val sameExchange = token == lastToken && reqNum == lastReqNum
        return when {
            sameExchange && request.command == lastCommand -> issuedToken to checkNotNull(lastAnswer)
            sameExchange -> token to refusal(request, INVALID_RETRY_REQUEST)
            token == issuedToken && reqNum == lastReqNum -> token to refusal(request, INVALID_REQUEST_NUMBER)
            token != issuedToken || token == 0L -> token to refusal(request, INVALID_TOKEN)
            else -> count(token, reqNum, request)
        }
    }

    private fun count(token: Long, reqNum: Int, request: Request): Pair<Long, Response> {
        val answer = accepted(request)
        counted += request
        drawer.apply(request)
        lastToken = token
        lastReqNum = reqNum
        lastCommand = request.command
        lastAnswer = answer
        issuedToken = nextToken++
        return issuedToken to answer
    }

    private fun accepted(request: Request) = Response(
        command = request.command,
        result = BfdResult(result_code = 0, result_text = "OK"),
        ticket = if (request.command == CommandTypeEnum.COMMAND_TICKET) TicketResponse(ticket_number = ticket()) else null,
        report = reportOf(request)
    )

    /** Отчёт в ответе: на X-отчёт — он же, на закрытие смены — Z-отчёт, как у референса. */
    private fun reportOf(request: Request): ReportResponse? =
        request.report?.let { ReportResponse(report = it.report, zx_report = it.zx_report) }
            ?: request.close_shift?.let { ReportResponse(report = ReportTypeEnum.REPORT_Z, zx_report = it.z_report) }

    private fun ticket(): String = (nextTicket++).toString()

    companion object {
        /** Неверный токен. */
        const val INVALID_TOKEN = 2
        private const val INVALID_REQUEST_NUMBER = 8
        private const val INVALID_RETRY_REQUEST = 9

        /** Токены БФД выдаёт выше 2³¹: поле беззнаковое, 0..4294967295. */
        private const val FIRST_ISSUED_TOKEN = 4_000_000_001L

        private val DOCUMENTS = setOf(
            CommandTypeEnum.COMMAND_TICKET,
            CommandTypeEnum.COMMAND_MONEY_PLACEMENT,
            CommandTypeEnum.COMMAND_CLOSE_SHIFT
        )

        /** Отказ с кодом [code]: документ не учитывается. */
        fun refusal(request: Request, code: Int) = Response(
            command = request.command,
            result = BfdResult(result_code = code, result_text = "Refused with code $code")
        )
    }
}
