package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.ShiftRecord

/**
 * Репозиторий смен.
 *
 * Зачем нужен:
 * - понимать, когда смена открыта/закрыта,
 * - связывать документы с конкретной сменой,
 * - корректно формировать X и Z отчеты.
 */
interface ShiftRepository {
    /**
     * Создает смену.
     */
    fun insert(record: ShiftRecord): Boolean

    /**
     * Обновляет статус/время закрытия смены.
     */
    fun updateClose(
        id: String,
        status: String,
        closedAt: Long,
        closeDocumentId: String?
    ): Boolean

    /**
     * Возвращает смену по id.
     */
    fun findById(id: String): ShiftRecord?

    /**
     * Возвращает смену по номеру.
     */
    fun findByShiftNo(cashboxId: String, shiftNo: Long): ShiftRecord?

    /**
     * Возвращает текущую открытую смену.
     */
    fun findOpenByCashbox(cashboxId: String): ShiftRecord?

    /**
     * Возвращает список смен по кассе.
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<ShiftRecord>

    /**
     * Удаляет все смены по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean

    /**
     * Общее количество смен. Если status != null — только с данным статусом (OPEN, CLOSED).
     */
    fun countAll(status: String? = null): Long
}
