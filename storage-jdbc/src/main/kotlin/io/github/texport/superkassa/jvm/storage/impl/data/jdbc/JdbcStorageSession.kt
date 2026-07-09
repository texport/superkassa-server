package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
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
import java.sql.Connection

/**
 * JDBC-реализация StorageSession.
 */
class JdbcStorageSession(
    private val connection: Connection
) : StorageSession {
    override val cashboxes: CashboxRepository = JdbcCashboxRepository(connection)
    override val documents: FiscalDocumentRepository = JdbcFiscalDocumentRepository(connection)
    override val journal: FiscalJournalRepository = JdbcFiscalJournalRepository(connection)
    override val users: KkmUserRepository = JdbcKkmUserRepository(connection)
    override val ofdMessages: OfdMessageRepository = JdbcOfdMessageRepository(connection)
    override val offlineQueue: OfflineQueueRepository = JdbcOfflineQueueRepository(connection)
    override val queueTask: QueueTaskRepository = JdbcQueueTaskRepository(connection)
    override val queueLock: QueueLockRepository = JdbcQueueLockRepository(connection)
    override val idempotency: IdempotencyRepository = JdbcIdempotencyRepository(connection)
    override val locks: CashboxLockRepository = JdbcCashboxLockRepository(connection)
    override val shifts: ShiftRepository = JdbcShiftRepository(connection)
    override val counters: CounterRepository = JdbcCounterRepository(connection)
    override val errors: ErrorMessageRepository = JdbcErrorMessageRepository(connection)
    override val outbox: OutboxEventRepository = JdbcOutboxEventRepository(connection)

    override fun <T> inTransaction(block: () -> T): T {
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            val result = block()
            connection.commit()
            result
        } catch (ex: Exception) {
            connection.rollback()
            throw ex
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    override fun close() {
        connection.close()
    }
}
