package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.CashboxLock

/**
 * Репозиторий lease/lock на кассу.
 *
 * Зачем нужен:
 * - запретить параллельные фискальные операции,
 * - обеспечить правило "1 касса = 1 операция".
 */
interface CashboxLockRepository {
    /**
     * Пытается захватить lease на кассу.
     */
    fun tryAcquire(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean

    /**
     * Продлевает lease при условии, что владелец совпадает.
     */
    fun renew(
        cashboxId: String,
        ownerId: String,
        leaseUntil: Long,
        now: Long
    ): Boolean

    /**
     * Освобождает lease.
     */
    fun release(cashboxId: String, ownerId: String): Boolean

    /**
     * Возвращает текущий lease по кассе.
     */
    fun findByCashboxId(cashboxId: String): CashboxLock?

    /**
     * Удаляет все lease по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
