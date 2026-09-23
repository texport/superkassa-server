package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.queue.QueueTask
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.model.QueueTaskRecord

class JdbcQueueDelegate(private val sessionProvider: () -> StorageSession) {

    fun enqueueQueueTask(dto: QueueTask): Boolean {
        return sessionProvider().queueTask.enqueue(
            QueueTaskRecord(
                id = dto.id,
                cashboxId = dto.cashboxId,
                lane = dto.lane,
                type = dto.type,
                payloadRef = dto.payloadRef,
                createdAt = dto.createdAt,
                status = dto.status,
                attempt = dto.attempt,
                nextAttemptAt = dto.nextAttemptAt,
                lastError = dto.lastError
            )
        )
    }

    fun listQueueTasksByCashbox(cashboxId: String, lane: String, limit: Int, offset: Int): List<QueueTask> =
        sessionProvider().queueTask.listByCashbox(cashboxId, lane, limit, offset).map { it.toTask() }

    fun listQueueTasksByStatus(cashboxId: String, lane: String, statuses: Set<String>): List<QueueTask> =
        sessionProvider().queueTask.listByStatus(cashboxId, lane, statuses).map { it.toTask() }

    private fun QueueTaskRecord.toTask() = QueueTask(
        id = id,
        cashboxId = cashboxId,
        lane = lane,
        type = type,
        payloadRef = payloadRef,
        createdAt = createdAt,
        status = status,
        attempt = attempt,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError
    )

    fun updateQueueTaskStatus(
        id: String,
        status: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean {
        return sessionProvider().queueTask.updateStatus(id, status, attempt, lastError, nextAttemptAt)
    }

    fun markQueueTaskInProgress(id: String, now: Long): Boolean {
        return sessionProvider().queueTask.markInProgress(id, now)
    }

    fun deleteQueueTasksByCashbox(cashboxId: String): Boolean {
        return sessionProvider().queueTask.deleteByCashbox(cashboxId)
    }

    fun countOfflineQueue(): Long {
        return sessionProvider().queueTask.countPendingByLane("OFFLINE")
    }

    fun tryAcquireQueueLock(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        acquiredAt: Long
    ): Boolean {
        return sessionProvider().queueLock.tryAcquire(cashboxId, ownerId, leaseUntil, acquiredAt)
    }

    fun renewQueueLock(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean {
        return sessionProvider().queueLock.renew(cashboxId, ownerId, leaseUntil, now)
    }

    fun releaseQueueLock(cashboxId: String, ownerId: String): Boolean {
        return sessionProvider().queueLock.release(cashboxId, ownerId)
    }
}
