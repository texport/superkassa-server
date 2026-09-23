package io.github.texport.superkassa.jvm.storage.impl.parity

import kotlinx.coroutines.delay
import kz.kazakhtelecom.proto.v203.CloseShiftRequest
import kz.kazakhtelecom.proto.v203.CommandTypeEnum
import kz.kazakhtelecom.proto.v203.MoneyPlacementRequest
import kz.kazakhtelecom.proto.v203.Request
import kz.kazakhtelecom.proto.v203.Response
import kz.kazakhtelecom.proto.v203.TicketRequest
import kz.kazakhtelecom.proto.v203.ZXReport
import kz.mybrain.network.OfdEndpoint
import kz.mybrain.network.OfdNetworkClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * БФД для проверок: разбирает каждый запрос кассы и ведёт её учёт по правилам
 * прод-референса ([BfdLedger]).
 *
 * Чеку отвечает своим номером — так касса получает номер документа от БФД,
 * как на стенде. Запросы хранятся разобранными: проверка читает то, что
 * касса отправила бы в БФД, а не то, что она записала у себя.
 *
 * Умеет сбои связи: запрос не дошёл; дошёл и учтён, но ответ потерян;
 * ответ задержан, пока касса шлёт что-то ещё; отказ с кодом — одному
 * следующему запросу или командам одного типа, пока не снят.
 * Наличные в ящике и смену БФД ведёт [BfdDrawer].
 */
internal class FakeBfd(initialToken: Long) : OfdNetworkClient {
    private val ledger = BfdLedger(initialToken)
    private val received = CopyOnWriteArrayList<Exchange>()
    private val faults = ConcurrentLinkedQueue<Fault>()
    private val rejections = ConcurrentHashMap<CommandTypeEnum, Int>()

    /** Запрос кассы, как его видит БФД: токен и номер из заголовка, тело разобрано. */
    data class Exchange(val token: Long, val reqNum: Int, val request: Request)

    val exchanges: List<Exchange> get() = received.toList()

    val requests: List<Request> get() = received.map { it.request }

    /** Токен, с которым касса обязана прийти в следующий раз. */
    val issuedToken: Long get() = ledger.issuedToken

    fun moneyPlacements(): List<MoneyPlacementRequest> = requests.mapNotNull { it.money_placement }

    fun xReports(): List<ZXReport> = requests.mapNotNull { it.report?.zx_report }

    /** Чеки, учтённые БФД: повтор с тем же номером запроса второй раз не учитывается. */
    fun countedTickets(): List<TicketRequest> = ledger.accepted.mapNotNull { it.ticket }

    /** Следующий запрос до БФД не дойдёт. */
    fun unreachableOnce() {
        faults += Fault.Unreachable
    }

    /** Следующий запрос БФД учтёт, а ответ до кассы не дойдёт. */
    fun loseNextAnswer() {
        faults += Fault.Lost(waitForAnotherMillis = 0)
    }

    /** Как [loseNextAnswer], но ответ держится, пока касса не пришлёт другой запрос (не дольше срока). */
    fun holdNextAnswerThenLose(maxMillis: Long) {
        faults += Fault.Lost(waitForAnotherMillis = maxMillis)
    }

    /** Следующему запросу БФД откажет кодом [code], ничего не учитывая. */
    fun refuseNext(code: Int) {
        faults += Fault.Refused(code)
    }

    fun closeShifts(): List<CloseShiftRequest> = requests.mapNotNull { it.close_shift }

    /** Изъятия по сменам БФД, в тиынах: номер смены БФД — сумма. */
    fun withdrawnByShift(): Map<Int, Long> = ledger.drawer.withdrawnByShift()

    /** Отказывать команде [command] кодом [code], пока не вызван [acceptAll]. */
    fun reject(command: CommandTypeEnum, code: Int) {
        rejections[command] = code
    }

    fun acceptAll() = rejections.clear()

    override suspend fun sendAndReceive(endpoint: OfdEndpoint, request: ByteArray): kotlin.Result<ByteArray> {
        val token = readUnsigned(request, TOKEN_OFFSET, TOKEN_BYTES)
        val reqNum = readUnsigned(request, REQNUM_OFFSET, REQNUM_BYTES).toInt()
        val decoded = Request.ADAPTER.decode(request.copyOfRange(HEADER_SIZE, request.size))
        received += Exchange(token, reqNum, decoded)
        val arrived = received.size
        return when (val fault = faults.poll()) {
            Fault.Unreachable -> noAnswer()
            is Fault.Refused -> kotlin.Result.success(reply(request, token, BfdLedger.refusal(decoded, fault.code)))
            is Fault.Lost -> {
                ledger.answer(token, reqNum, decoded)
                awaitAnother(arrived, fault.waitForAnotherMillis)
                noAnswer()
            }
            null -> rejections[decoded.command]
                ?.let { kotlin.Result.success(reply(request, token, BfdLedger.refusal(decoded, it))) }
                ?: ledger.answer(token, reqNum, decoded).let { (issued, answer) ->
                    kotlin.Result.success(reply(request, issued, answer))
                }
        }
    }

    private suspend fun awaitAnother(arrived: Int, maxMillis: Long) {
        var waited = 0L
        while (received.size == arrived && waited < maxMillis) {
            delay(POLL_MILLIS)
            waited += POLL_MILLIS
        }
    }

    private fun noAnswer(): kotlin.Result<ByteArray> = kotlin.Result.failure(Exception("BFD response timeout"))

    /** Заголовок ответа — заголовок запроса с токеном БФД и общей длиной ответа. */
    private fun reply(request: ByteArray, token: Long, answer: Response): ByteArray {
        val payload = Response.ADAPTER.encode(answer)
        val header = request.copyOf(HEADER_SIZE)
        writeUnsigned(header, SIZE_OFFSET, SIZE_BYTES, (HEADER_SIZE + payload.size).toLong())
        writeUnsigned(header, TOKEN_OFFSET, TOKEN_BYTES, token)
        return header + payload
    }

    private sealed interface Fault {
        data object Unreachable : Fault
        data class Lost(val waitForAnotherMillis: Long) : Fault
        data class Refused(val code: Int) : Fault
    }

    companion object {
        /** Первый номер чека, который выдаёт БФД. */
        const val FIRST_TICKET_NUMBER = 9001L

        /** Заголовок CPCR: APPCODE 2, VERSION 2, SIZE 4, ID 4, TOKEN 4, REQNUM 2 — little-endian. */
        private const val HEADER_SIZE = 18
        private const val SIZE_OFFSET = 4
        private const val SIZE_BYTES = 4
        private const val TOKEN_OFFSET = 12
        private const val TOKEN_BYTES = 4
        private const val REQNUM_OFFSET = 16
        private const val REQNUM_BYTES = 2
        private const val BYTE_BITS = 8
        private const val BYTE_MASK = 0xFFL
        private const val POLL_MILLIS = 10L

        private fun readUnsigned(bytes: ByteArray, offset: Int, length: Int): Long =
            (0 until length).fold(0L) { acc, i -> acc or ((bytes[offset + i].toLong() and BYTE_MASK) shl (BYTE_BITS * i)) }

        private fun writeUnsigned(bytes: ByteArray, offset: Int, length: Int, value: Long) {
            for (i in 0 until length) bytes[offset + i] = (value shr (BYTE_BITS * i)).toByte()
        }
    }
}
