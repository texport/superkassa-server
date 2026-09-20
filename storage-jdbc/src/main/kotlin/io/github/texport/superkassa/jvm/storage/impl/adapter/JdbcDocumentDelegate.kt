package io.github.texport.superkassa.jvm.storage.impl.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.texport.superkassa.core.domain.api.model.common.Money
import io.github.texport.superkassa.core.domain.api.model.kkm.FiscalDocumentSnapshot
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptDocumentTypes
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptRequest
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptStoredPayload
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.model.FiscalDocumentRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.model.IdempotencyRecord

/**
 * Делегат для работы с фискальными документами в JDBC хранилище.
 * Выполняет сохранение чеков и кассовых операций, а также обновление статусов доставки в ОФД.
 *
 * @property sessionProvider поставщик активной сессии БД.
 */
class JdbcDocumentDelegate(private val sessionProvider: () -> StorageSession) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(JdbcDocumentDelegate::class.java)

        private const val SHIFT_OPEN_TYPE = "SHIFT_OPEN"

        /** Документ, которому в ОФД дороги нет. */
        private const val INTERNAL_STATUS = "INTERNAL"

        /** Документ ждёт отправки. */
        private const val PENDING_STATUS = "PENDING"
    }

    private val jackson = jacksonObjectMapper()

    /**
     * Сохраняет фискальный чек в базе данных.
     *
     * @param request доменный запрос чека [ReceiptRequest].
     * @param documentId уникальный идентификатор документа.
     * @param shiftId идентификатор смены.
     * @param createdAt время создания документа.
     * @return true в случае успешного сохранения, иначе false.
     */
    fun saveReceipt(request: ReceiptRequest, documentId: String, shiftId: String, createdAt: Long): Boolean {
        val payloadBin = jackson.writeValueAsBytes(
            ReceiptStoredPayload.fromReceiptRequest(request)
        )
        val session = sessionProvider()
        val shiftNo = session.shifts.findById(shiftId)?.shiftNo
        return session.documents.insert(
            FiscalDocumentRecord(
                id = documentId,
                cashboxId = request.kkmId,
                shiftId = shiftId,
                // Тип документа — сама операция, как её называет справочник
                // узла: продажа, возврат продажи, покупка, возврат покупки.
                // Общий «CHECK» на все четыре делал журнал нечитаемым и не
                // давал отличить чек-основание для возврата от возврата.
                docType = ReceiptDocumentTypes.of(request.operation),
                docNo = null,
                shiftNo = shiftNo,
                createdAt = createdAt,
                // Итог хранится в тиынах: контракт представления объявляет
                // именно их, а целые тенге теряли дробную часть чека.
                totalAmount = request.total.tiyn(),
                currency = "KZT",
                payloadBin = payloadBin,
                ofdStatus = "PENDING"
            )
        )
    }

    fun saveCashOperation(
        kkmId: String,
        type: String,
        amount: Money,
        documentId: String,
        shiftId: String,
        createdAt: Long
    ): Boolean {
        val session = sessionProvider()
        val shiftNo = session.shifts.findById(shiftId)?.shiftNo
        return session.documents.insert(
            FiscalDocumentRecord(
                id = documentId,
                cashboxId = kkmId,
                shiftId = shiftId,
                docType = type,
                docNo = null,
                shiftNo = shiftNo,
                createdAt = createdAt,
                totalAmount = amount.tiyn(),
                currency = "KZT",
                payloadBin = null,
                ofdStatus = "PENDING"
            )
        )
    }

    fun saveShiftDocument(
        kkmId: String,
        type: String,
        documentId: String,
        shiftId: String,
        createdAt: Long
    ): Boolean {
        val session = sessionProvider()
        val shiftNo = session.shifts.findById(shiftId)?.shiftNo
        return session.documents.insert(
            FiscalDocumentRecord(
                id = documentId,
                cashboxId = kkmId,
                shiftId = shiftId,
                docType = type,
                docNo = null,
                shiftNo = shiftNo,
                createdAt = createdAt,
                totalAmount = 0L,
                currency = "KZT",
                payloadBin = null,
                // Открытие смены в ОФД не уходит: команда COMMAND_OPEN_SHIFT
                // исключена из протокола. Документ нужен кассиру и
                // администратору и остаётся в журнале, но состояния доставки
                // у него быть не может — иначе он вечно висит «в очереди»
                // и предлагает дослать себя в никуда.
                ofdStatus = if (type == SHIFT_OPEN_TYPE) INTERNAL_STATUS else PENDING_STATUS
            )
        )
    }

    fun updateReceiptStatus(
        documentId: String,
        fiscalSign: String?,
        autonomousSign: String?,
        ofdStatus: String,
        deliveredAt: Long?,
        ofdErrorCode: Int? = null,
        isAutonomous: Boolean? = null,
        receiptUrl: String? = null
    ): Boolean {
        return sessionProvider().documents.updateStatus(
            id = documentId,
            ofdStatus = ofdStatus,
            fiscalSign = fiscalSign,
            autonomousSign = autonomousSign,
            deliveredAt = deliveredAt,
            ofdErrorCode = ofdErrorCode,
            isAutonomous = isAutonomous,
            receiptUrl = receiptUrl
        )
    }

    fun findFiscalDocumentById(id: String): FiscalDocumentSnapshot? {
        val session = sessionProvider()
        return session.documents.findById(id)?.let { StorageMapper.toFiscalDocumentSnapshot(it, session) }
    }

    fun findFiscalDocumentWithReceiptPayload(documentId: String): Pair<FiscalDocumentSnapshot, ReceiptRequest>? {
        val session = sessionProvider()
        val record = session.documents.findById(documentId) ?: return null
        // Тип документа называет операцию чека: SALE, RETURN, BUY, BUY_RETURN.
        // Проверка на единственный «CHECK» осталась от прежнего именования
        // и отсекала каждый чек, написанный после переименования: в ОФД он
        // не уходил вовсе, а пересчёт смены считал кассу пустой.
        if (record.docType !in ReceiptDocumentTypes.ALL) return null
        if (record.payloadBin == null || record.payloadBin.isEmpty()) return null
        val payload = try {
            jackson.readValue(record.payloadBin, ReceiptStoredPayload::class.java)
        } catch (e: Exception) {
            log.error("Failed to decode receipt payload for document {}", documentId, e)
            return null
        }
        return StorageMapper.toFiscalDocumentSnapshot(record, session) to payload.toReceiptRequest()
    }

    fun listFiscalDocumentsByShift(
        kkmId: String,
        shiftId: String,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentSnapshot> {
        val session = sessionProvider()
        return session.documents.listByShift(kkmId, shiftId, limit, offset)
            .map { StorageMapper.toFiscalDocumentSnapshot(it, session) }
    }

    fun listFiscalDocumentsByPeriod(
        kkmId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentSnapshot> {
        val session = sessionProvider()
        return session.documents.listByCashboxAndCreatedAtBetween(kkmId, fromInclusive, toExclusive, limit, offset)
            .map { StorageMapper.toFiscalDocumentSnapshot(it, session) }
    }

    fun countFiscalDocuments(docType: String?): Long {
        return sessionProvider().documents.countAll(docType)
    }

    fun insertIdempotency(kkmId: String, idempotencyKey: String, operation: String): Boolean {
        return sessionProvider().idempotency.insertIfAbsent(
            IdempotencyRecord(
                idempotencyKey = idempotencyKey,
                cashboxId = kkmId,
                operation = operation,
                createdAt = System.currentTimeMillis(),
                status = "CREATED",
                responseRef = null
            )
        )
    }

    fun findIdempotencyResponse(kkmId: String, idempotencyKey: String): String? {
        return sessionProvider().idempotency.findByKey(kkmId, idempotencyKey)?.responseRef
    }

    fun updateIdempotencyResponse(kkmId: String, idempotencyKey: String, responseRef: String?): Boolean {
        return sessionProvider().idempotency.updateResponse(kkmId, idempotencyKey, "DONE", responseRef)
    }
}
