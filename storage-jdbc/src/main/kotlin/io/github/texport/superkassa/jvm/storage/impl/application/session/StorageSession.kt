package io.github.texport.superkassa.jvm.storage.impl.application.session

import io.github.texport.superkassa.jvm.storage.impl.domain.repository.CashboxLockRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.CashboxRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.CounterRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.ErrorMessageRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.FiscalDocumentRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.FiscalJournalRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.IdempotencyRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.KkmUserRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.OfdMessageRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.OfflineQueueRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.OutboxEventRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.QueueLockRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.QueueTaskRepository
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.ShiftRepository

/**
 * Единая сессия доступа к репозиториям.
 *
 * Зачем это нужно:
 * - чтобы сгруппировать работу с разными таблицами в одном месте,
 * - чтобы гарантировать атомарность операций через транзакцию,
 * - чтобы можно было подменять реализацию (JDBC/Room/корпоративный storage).
 */
interface StorageSession : AutoCloseable {
    val cashboxes: CashboxRepository
    val documents: FiscalDocumentRepository
    val journal: FiscalJournalRepository
    val users: KkmUserRepository
    val ofdMessages: OfdMessageRepository
    val offlineQueue: OfflineQueueRepository
    val queueTask: QueueTaskRepository
    val queueLock: QueueLockRepository
    val idempotency: IdempotencyRepository
    val locks: CashboxLockRepository
    val shifts: ShiftRepository
    val counters: CounterRepository
    val errors: ErrorMessageRepository
    val outbox: OutboxEventRepository

    /**
     * Выполняет блок в транзакции.
     * Используется для операций, которые должны быть атомарными.
     */
    fun <T> inTransaction(block: () -> T): T
    override fun close()
}
