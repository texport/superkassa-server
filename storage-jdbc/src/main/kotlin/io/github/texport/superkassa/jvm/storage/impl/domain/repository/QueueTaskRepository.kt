package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.QueueTaskRecord

/**
 * Репозиторий очереди команд (queue_task).
 * Обращение к БД только через storage.
 */
interface QueueTaskRepository {
    fun enqueue(record: QueueTaskRecord): Boolean
    fun nextPending(cashboxId: String, lane: String, now: Long): QueueTaskRecord?
    fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean
    fun markInProgress(id: String, now: Long): Boolean
    fun listByCashbox(cashboxId: String, lane: String, limit: Int, offset: Int): List<QueueTaskRecord>
    fun deleteByCashbox(cashboxId: String): Boolean
    fun countPendingByLane(lane: String): Long
}
