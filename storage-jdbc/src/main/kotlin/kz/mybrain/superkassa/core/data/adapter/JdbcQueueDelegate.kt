package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.QueueTaskDto
import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.domain.model.QueueTaskRecord

class JdbcQueueDelegate(private val sessionProvider: () -> StorageSession) {

    fun enqueueQueueTask(dto: QueueTaskDto): Boolean {
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

    fun listQueueTasksByCashbox(cashboxId: String, lane: String, limit: Int, offset: Int): List<QueueTaskDto> {
        return sessionProvider().queueTask.listByCashbox(cashboxId, lane, limit, offset).map {
            QueueTaskDto(
                id = it.id,
                cashboxId = it.cashboxId,
                lane = it.lane,
                type = it.type,
                payloadRef = it.payloadRef,
                createdAt = it.createdAt,
                status = it.status,
                attempt = it.attempt,
                nextAttemptAt = it.nextAttemptAt,
                lastError = it.lastError
            )
        }
    }

    fun nextPendingQueueTask(cashboxId: String, lane: String, now: Long): QueueTaskDto? {
        return sessionProvider().queueTask.nextPending(cashboxId, lane, now)?.let {
            QueueTaskDto(
                id = it.id,
                cashboxId = it.cashboxId,
                lane = it.lane,
                type = it.type,
                payloadRef = it.payloadRef,
                createdAt = it.createdAt,
                status = it.status,
                attempt = it.attempt,
                nextAttemptAt = it.nextAttemptAt,
                lastError = it.lastError
            )
        }
    }

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
