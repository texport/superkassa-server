package kz.mybrain.superkassa.core.data.adapter

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kz.mybrain.superkassa.core.domain.model.kkm.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.common.Money
import kz.mybrain.superkassa.core.domain.model.receipt.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.receipt.ReceiptStoredPayload
import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.domain.model.FiscalDocumentRecord
import kz.mybrain.superkassa.storage.domain.model.IdempotencyRecord

class JdbcDocumentDelegate(private val sessionProvider: () -> StorageSession) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(JdbcDocumentDelegate::class.java)
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun saveReceipt(request: ReceiptRequest, documentId: String, shiftId: String, createdAt: Long): Boolean {
        val payloadBin = json.encodeToString(
            serializer<ReceiptStoredPayload>(),
            ReceiptStoredPayload.fromReceiptRequest(request)
        ).toByteArray(Charsets.UTF_8)
        return sessionProvider().documents.insert(
            FiscalDocumentRecord(
                id = documentId,
                cashboxId = request.kkmId,
                shiftId = shiftId,
                docType = "CHECK",
                docNo = null,
                shiftNo = null,
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
        return sessionProvider().documents.insert(
            FiscalDocumentRecord(
                id = documentId,
                cashboxId = kkmId,
                shiftId = shiftId,
                docType = type,
                docNo = null,
                shiftNo = null,
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
        isAutonomous: Boolean? = null
    ): Boolean {
        return sessionProvider().documents.updateStatus(
            id = documentId,
            ofdStatus = ofdStatus,
            fiscalSign = fiscalSign,
            autonomousSign = autonomousSign,
            deliveredAt = deliveredAt,
            isAutonomous = isAutonomous
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
            json.decodeFromString<ReceiptStoredPayload>(String(record.payloadBin, Charsets.UTF_8))
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
