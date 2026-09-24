package kz.mybrain.superkassa.core.application.http

/*
 * Описания методов чека для OpenAPI.
 *
 * Четыре метода чека принимают одно и то же тело и отвечают одним и тем же;
 * общее описано здесь один раз, а метод добавляет только то, чем отличается.
 */

private const val RECEIPT_REQUIREMENTS = """
Требования:
- ККМ зарегистрирована и в состоянии ACTIVE, смена открыта
- ПИН кассира или администратора — в заголовке Authorization ("Bearer <pin>" или просто "<pin>")
"""

private const val RECEIPT_BODY = """
Что передавать:
- kkmId (в пути): идентификатор ККМ
- idempotencyKey: ключ операции; повтор с тем же ключом отвечает тем же чеком, второго документа нет
- items: позиции чека; у каждой:
  * name — наименование (3–128 символов, без обобщённых значений вроде "Товар");
  * price — цена за единицу, quantity — количество (> 0), sum — сумма позиции, точными десятичными числами;
  * barcode (опционально) — штрихкод; vatGroup (опционально) — группа НДС из справочника /vat-rates;
  * discountPercent / discountSum и markupPercent / markupSum (опционально, попарно взаимоисключающие) —
    скидка и наценка на позицию.
- discountPercent / discountSum, markupPercent / markupSum (опционально): скидка и наценка на весь чек
- payments: оплаты; у каждой type — CASH, CARD, ELECTRONIC — и sum
- taken, change (опционально): получено от покупателя и сдача
- total: сумма чека
- customerContact (опционально): контакт покупателя, по которому ему уходит чек:
  * phone — SMS и WhatsApp, email — письмо, telegram — чат Telegram;
  * чек уходит во включённые каналы доставки узла того вида, что указан, в фоне после приёма БФД —
    ответ кассиру доставку не ждёт; автономный чек уходит после досылки в БФД;
  * без контакта чек покупателю не отправляется. Контакт в журнал узла не пишется.
"""

private const val RECEIPT_RESULT = """
Что возвращается:
- documentId: идентификатор фискального документа
- fiscalSign: фискальный признак БФД; null, если чек оформлен автономно
- autonomousSign: автономный фискальный признак, если чек оформлен без связи с БФД
- deliveryStatus: судьба чека в БФД — ONLINE_OK, ONLINE_ERROR, OFFLINE_QUEUED, NOT_SENT
- deliveryError (опционально): почему БФД не принял чек и что делать — ru, kk, en
- bfdResultCode (опционально): код отказа БФД (ResultTypeEnum CPCR); пусто, если БФД не ответил или принял
- deliveryPayload (опционально): печатная форма

Все суммы — в тенге точным десятичным числом, например 1234.56.
"""

/** Чек продажи. */
const val RECEIPT_SELL_DESCRIPTION = "Создаёт чек продажи (приход) и отправляет его в БФД.\n" +
    RECEIPT_REQUIREMENTS + RECEIPT_BODY + RECEIPT_RESULT

private const val PARENT_TICKET =
    "- parentTicket (опционально): исходный чек — номер, дата и время, РНМ ККМ, сумма, признак автономного\n"

/** Чек возврата продажи. */
const val RECEIPT_SELL_RETURN_DESCRIPTION = "Создаёт чек возврата продажи и отправляет его в БФД.\n" +
    RECEIPT_REQUIREMENTS + RECEIPT_BODY + PARENT_TICKET + RECEIPT_RESULT

/** Чек покупки. */
const val RECEIPT_BUY_DESCRIPTION = "Создаёт чек покупки (расход) и отправляет его в БФД.\n" +
    RECEIPT_REQUIREMENTS + RECEIPT_BODY + RECEIPT_RESULT

/** Чек возврата покупки. */
const val RECEIPT_BUY_RETURN_DESCRIPTION = "Создаёт чек возврата покупки и отправляет его в БФД.\n" +
    RECEIPT_REQUIREMENTS + RECEIPT_BODY + PARENT_TICKET + RECEIPT_RESULT
