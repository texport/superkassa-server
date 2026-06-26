package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.domain.model.CounterRecord
import kz.mybrain.superkassa.storage.domain.model.ShiftRecord

class JdbcShiftDelegate(private val sessionProvider: () -> StorageSession) {

    fun createShift(info: ShiftInfo): Boolean {
        return sessionProvider().shifts.insert(
            ShiftRecord(
                id = info.id,
                cashboxId = info.kkmId,
                shiftNo = info.shiftNo,
                status = info.status.name,
                openedAt = info.openedAt,
                closedAt = info.closedAt,
                openDocumentId = info.openDocumentId,
                closeDocumentId = info.closeDocumentId
            )
        )
    }

    fun closeShift(shiftId: String, status: ShiftStatus, closedAt: Long, closeDocumentId: String?): Boolean {
        return sessionProvider().shifts.updateClose(
            id = shiftId,
            status = status.name,
            closedAt = closedAt,
            closeDocumentId = closeDocumentId
        )
    }

    fun findShiftById(id: String): ShiftInfo? {
        return sessionProvider().shifts.findById(id)?.let { StorageMapper.mapShift(it) }
    }

    fun findShiftByNo(kkmId: String, shiftNo: Long): ShiftInfo? {
        return sessionProvider().shifts.findByShiftNo(kkmId, shiftNo)?.let { StorageMapper.mapShift(it) }
    }

    fun findOpenShift(kkmId: String): ShiftInfo? {
        return sessionProvider().shifts.findOpenByCashbox(kkmId)?.let { StorageMapper.mapShift(it) }
    }

    fun listShifts(kkmId: String, limit: Int, offset: Int): List<ShiftInfo> {
        return sessionProvider().shifts.listByCashbox(kkmId, limit, offset).map { StorageMapper.mapShift(it) }
    }

    fun deleteShiftsByKkm(kkmId: String): Boolean {
        return sessionProvider().shifts.deleteByCashbox(kkmId)
    }

    fun countClosedShifts(): Long {
        return sessionProvider().shifts.countAll("CLOSED")
    }

    fun loadCounters(kkmId: String, scope: String, shiftId: String?): Map<String, Long> {
        return sessionProvider().counters.listByScope(kkmId, scope, shiftId)
            .associate { it.key to it.value }
    }

    fun listCounters(kkmId: String): List<CounterSnapshot> {
        return sessionProvider().counters.listByCashbox(kkmId).map {
            CounterSnapshot(
                scope = it.scope,
                shiftId = it.shiftId,
                key = it.key,
                value = it.value,
                updatedAt = it.updatedAt
            )
        }
    }

    fun upsertCounter(kkmId: String, scope: String, shiftId: String?, key: String, value: Long): Boolean {
        return sessionProvider().counters.upsert(
            CounterRecord(
                cashboxId = kkmId,
                scope = scope,
                shiftId = shiftId,
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun deleteCounter(kkmId: String, scope: String, shiftId: String?, key: String): Boolean {
        return sessionProvider().counters.deleteByKey(kkmId, scope, shiftId, key)
    }

    fun deleteCountersByKkm(kkmId: String): Boolean {
        return sessionProvider().counters.deleteByCashbox(kkmId)
    }
}
