package io.github.texport.superkassa.jvm.storage.impl.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.texport.superkassa.core.domain.api.model.common.Money
import io.github.texport.superkassa.core.domain.api.model.kkm.FiscalDocumentSnapshot
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
                docType = "CHECK",
                docNo = null,
                shiftNo = shiftNo,
                createdAt = createdAt,
                totalAmount = request.total.bills,
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
                totalAmount = amount.bills,
                currency = "KZT",
                payloadBin = null,
                ofdStatus = "PENDING"
            )
        )
    }

    fun updateReceiptStatus(
        documentId: String,
        fiscalSign: String?,
        autonomousSign: String?,
        ofdStatus: String,
        deliveredAt: Long?,
        isAutonomous: Boolean? = null,
        receiptUrl: String? = null
    ): Boolean {
        return sessionProvider().documents.updateStatus(
            id = documentId,
            ofdStatus = ofdStatus,
            fiscalSign = fiscalSign,
            autonomousSign = autonomousSign,
            deliveredAt = deliveredAt,
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
        if (record.docType != "CHECK" || record.payloadBin == null || record.payloadBin.isEmpty()) return null
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
