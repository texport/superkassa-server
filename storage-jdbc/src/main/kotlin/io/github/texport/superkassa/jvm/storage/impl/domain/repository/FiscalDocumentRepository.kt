package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.FiscalDocumentRecord

/**
 * Репозиторий фискальных документов (чеки/отчеты/операции).
 *
 * Зачем нужен:
 * - хранить все документы в едином формате,
 * - связывать документ с кассой и сменой,
 * - фиксировать фискальные признаки и статус доставки.
 */
interface FiscalDocumentRepository {
    /**
     * Создает фискальный документ.
     */
    fun insert(record: FiscalDocumentRecord): Boolean

    /**
     * Ищет документ по id.
     */
    fun findById(id: String): FiscalDocumentRecord?

    /**
     * Ищет документ по кассе и номеру документа.
     */
    fun findByCashboxAndDocNo(cashboxId: String, docNo: Long): FiscalDocumentRecord?

    /**
     * Обновляет статус доставки и фискальные признаки.
     * @param isAutonomous если не null — обновляет признак автономного документа.
     */
    fun updateStatus(
        id: String,
        ofdStatus: String,
        fiscalSign: String?,
        autonomousSign: String?,
        deliveredAt: Long?,
        isAutonomous: Boolean? = null,
        receiptUrl: String? = null
    ): Boolean

    /**
     * Список документов по кассе (постранично).
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<FiscalDocumentRecord>

    /**
     * Список документов по смене (постранично).
     */
    fun listByShift(
        cashboxId: String,
        shiftId: String,
        limit: Int,
        offset: Int = 0
    ): List<FiscalDocumentRecord>

    /**
     * Список документов по кассе за период по created_at (from включительно, to исключая; epoch millis).
     */
    fun listByCashboxAndCreatedAtBetween(
        cashboxId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int = 0
    ): List<FiscalDocumentRecord>

    /**
     * Удаляет документ по id.
     * Не используется для фискального журнала (он append-only).
     */
    fun deleteById(id: String): Boolean

    /**
     * Удаляет все документы по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean

    /**
     * Общее количество фискальных документов (по всем кассам). Если docType != null — только данного типа (CHECK, CASH_IN и т.д.).
     */
    fun countAll(docType: String? = null): Long
}
