package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import java.util.Base64

/**
 * Пакеты протокола для проверок: те же, какими их хранит сервер приёма.
 *
 * Числа в восемь байт записаны строками, перечисления — именами: так их
 * пишет схема протокола, и разбор обязан читать именно это.
 */
internal object ProtocolPackets {

    const val RECEIPT_LINK = "https://consumer.oofd.kz/r/417"

    val REGISTRATION = """
        "service": {
          "regInfo": {
            "kkm": {"fnsKkmId": "NZ7700123456", "serialNumber": "SW7700987654", "kkmId": "260940000021"},
            "org": {
              "title": "ТОО Пример", "address": "г. Алматы, пр. Абая, 1",
              "addressKz": "Алматы қ., Абай даңғылы, 1", "iin": "123456789012", "oked": "47110"
            }
          }
        }
    """.trimIndent()

    fun moment(day: Int = 18, hour: Int = 14, minute: Int = 53, second: Int = 7): String =
        """{"date": {"year": 2026, "month": 9, "day": $day}, "time": {"hour": $hour, "minute": $minute, "second": $second}}"""

    fun money(bills: Long, coins: Int = 0): String = """{"bills": "$bills", "coins": $coins}"""

    fun receipt(
        operation: String = "OPERATION_SELL",
        response: String = receiptAnswer()
    ): String = """
        {
          "request": {
            "command": "COMMAND_TICKET",
            "ticket": {
              "operation": "$operation",
              "dateTime": ${moment()},
              "operator": {"code": 1, "name": "Кассир Алия"},
              "domain": {"type": "DOMAIN_TRADING"},
              "items": [
                {
                  "type": "ITEM_TYPE_COMMODITY",
                  "commodity": {
                    "name": "Хлеб", "sectionCode": "001", "quantity": "2000",
                    "price": ${money(150)}, "sum": ${money(300)},
                    "measureUnitCode": "796", "barcode": "4870123456789", "ntin": "NTIN-1",
                    "listExciseStamp": ["AB-1"],
                    "taxes": [{"type": "VAT", "percent": 16000, "sum": ${money(41, 38)}, "isInTotalSum": true}]
                  }
                },
                {
                  "type": "ITEM_TYPE_STORNO_COMMODITY",
                  "stornoCommodity": {
                    "name": "Молоко", "sectionCode": "001", "quantity": "1000",
                    "price": ${money(50)}, "sum": ${money(50)}, "measureUnitCode": "796"
                  }
                },
                {"type": "ITEM_TYPE_DISCOUNT", "discount": {"name": "Скидка", "sum": ${money(10)}}}
              ],
              "payments": [
                {"type": "PAYMENT_CASH", "sum": ${money(200)}},
                {"type": "PAYMENT_CARD", "sum": ${money(100)}}
              ],
              "amounts": {"total": ${money(300)}, "taken": ${money(300)}, "change": ${money(0)}},
              "extensionOptions": {"customerIinOrBin": "123456789012"},
              "printedDocumentNumber": "417",
              "frShiftNumber": 12
            },
            $REGISTRATION
          }
          $response
        }
    """.trimIndent()

    fun receiptAnswer(code: Int = 0): String = """
        , "response": {
          "command": "COMMAND_TICKET",
          "result": {"resultCode": $code},
          "ticket": {
            "ticketNumber": "987654321012",
            "qrCode": "${Base64.getEncoder().encodeToString(RECEIPT_LINK.toByteArray())}"
          }
        }
    """.trimIndent()

    /** Сменные итоги: по ним рисуются и X-, и Z-отчёт. */
    val TOTALS = """
        {
          "dateTime": ${moment(hour = 21)},
          "openShiftTime": ${moment(hour = 8, minute = 0, second = 0)},
          "closeShiftTime": ${moment(hour = 21, minute = 5, second = 0)},
          "shiftNumber": 12,
          "cashSum": ${money(15_000, 25)},
          "revenue": {"sum": ${money(20_000)}, "isNegative": true},
          "startShiftNonNullableSums": [{"operation": "OPERATION_SELL", "sum": ${money(100_000)}}],
          "nonNullableSums": [{"operation": "OPERATION_SELL", "sum": ${money(120_000)}}],
          "operations": [{"operation": "OPERATION_SELL", "count": 7, "sum": ${money(20_000)}}],
          "sections": [
            {
              "sectionCode": "001",
              "operations": [{"operation": "OPERATION_SELL", "count": 7, "sum": ${money(20_000)}}]
            }
          ],
          "discounts": [{"operation": "OPERATION_SELL", "count": 1, "sum": ${money(100)}}],
          "markups": [{"operation": "OPERATION_SELL", "count": 0, "sum": ${money(0)}}],
          "totalResult": [{"operation": "OPERATION_SELL", "count": 7, "sum": ${money(19_900)}}],
          "taxes": [
            {
              "type": "VAT", "taxType": 100, "percent": 16000,
              "operations": [
                {
                  "operation": "OPERATION_SELL", "turnover": ${money(20_000)},
                  "sum": ${money(2758, 62)}, "turnoverWithoutTax": ${money(17_241, 38)}
                }
              ]
            },
            {"type": "VAT", "percent": 7000, "operations": []}
          ],
          "ticketOperations": [
            {
              "operation": "OPERATION_SELL", "ticketsTotalCount": 8, "ticketsCount": 7,
              "ticketsSum": ${money(20_000)},
              "payments": [
                {"payment": "PAYMENT_CASH", "sum": ${money(15_000)}, "count": 5},
                {"sum": ${money(5000)}, "count": 2}
              ],
              "offlineCount": 1, "discountSum": ${money(100)},
              "markupSum": ${money(0)}, "changeSum": ${money(40)}
            }
          ],
          "moneyPlacements": [
            {
              "operation": "MONEY_PLACEMENT_DEPOSIT", "operationsTotalCount": 2,
              "operationsCount": 2, "operationsSum": ${money(5000)}, "offlineCount": 0
            },
            {"operationsTotalCount": 1, "operationsCount": 1, "operationsSum": ${money(1000)}, "offlineCount": 0}
          ],
          "checksum": "1234567890"
        }
    """.trimIndent()

    fun report(kind: String = "REPORT_X", totals: String = """"zxReport": $TOTALS,"""): String = """
        {
          "request": {
            "command": "COMMAND_REPORT",
            "report": {$totals "report": "$kind", "dateTime": ${moment(hour = 21)}, "printedDocumentNumber": "418"},
            $REGISTRATION
          },
          "response": {"command": "COMMAND_REPORT", "result": {"resultCode": 0}, "report": {"zxReport": $TOTALS}}
        }
    """.trimIndent()

    fun closeShift(totals: String = """"zReport": $TOTALS,"""): String = """
        {
          "request": {
            "command": "COMMAND_CLOSE_SHIFT",
            "closeShift": {
              $totals "closeTime": ${moment(hour = 21, minute = 5, second = 0)},
              "frShiftNumber": 12, "printedDocumentNumber": "419", "operator": {"code": 1}
            },
            $REGISTRATION
          },
          "response": {"command": "COMMAND_CLOSE_SHIFT", "result": {"resultCode": 0}, "report": {"zxReport": $TOTALS}}
        }
    """.trimIndent()

    fun placement(operation: String = "MONEY_PLACEMENT_WITHDRAWAL"): String = """
        {
          "request": {
            "command": "COMMAND_MONEY_PLACEMENT",
            "moneyPlacement": {
              "dateTime": ${moment(hour = 19)}, "operation": "$operation",
              "sum": ${money(1000, 50)}, "frShiftNumber": 12,
              "printedDocumentNumber": "420", "operator": {"code": 1}
            },
            $REGISTRATION
          },
          "response": {"command": "COMMAND_MONEY_PLACEMENT", "result": {"resultCode": 0}}
        }
    """.trimIndent()

    /** Касса рабочего места: оформление её, реквизиты документа — из пакета. */
    fun localKkm(): KkmInfo = KkmInfo(
        id = "4166498c-d0c1-406e-863d-20458dfd3040",
        createdAt = 1_774_567_890_000L,
        updatedAt = 1_774_567_890_000L,
        mode = "PRODUCTION",
        state = "ACTIVE",
        registrationNumber = "NZ0000000000",
        factoryNumber = "SW0000000000"
    )
}
