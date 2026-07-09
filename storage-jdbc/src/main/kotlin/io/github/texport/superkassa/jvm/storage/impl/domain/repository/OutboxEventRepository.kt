package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.OutboxEventRecord

/**
 * Репозиторий outbox-событий.
 *
 * Зачем нужен:
 * - надежно отдавать события во внешние системы (Kafka и т.д.),
 * - не терять события при сбоях.
 */
interface OutboxEventRepository {
    /**
     * Создает событие.
     */
    fun insert(record: OutboxEventRecord): Boolean

    /**
     * Возвращает событие по id.
     */
    fun findById(id: String): OutboxEventRecord?

    /**
     * Список ожидающих событий.
     */
    fun listPending(limit: Int): List<OutboxEventRecord>

    /**
     * Обновляет статус и попытки отправки.
     */
    fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        nextAttemptAt: Long?,
        lastError: String?
    ): Boolean

    /**
     * Удаляет событие (по политике хранения).
     */
    fun deleteById(id: String): Boolean

    /**
     * Удаляет все события по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
