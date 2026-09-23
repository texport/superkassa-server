package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.auth.PinAttempts
import io.github.texport.superkassa.core.domain.api.port.integration.PinAttemptsPort
import io.github.texport.superkassa.core.domain.api.port.integration.inTransaction

/**
 * Счёт неверных пинов в базе узла.
 *
 * Идёт через сессию хранилища: внутри операции кассы — в её же транзакции,
 * вне её — в своей. Прежде счёт жил в памяти процесса, и перезапуск узла
 * снимал блокировку, заработанную перебором пина.
 */
internal class JdbcPinAttempts(private val storage: StorageAdapter) : PinAttemptsPort {

    /** Счёт меняется в транзакции: строка кассы заперта от чтения до записи. */
    override fun getAndUpdate(kkmId: String, change: (PinAttempts) -> PinAttempts): PinAttempts =
        storage.inTransaction {
            storage.withSession { session ->
                val previous = session.pinAttempts.lock(kkmId)
                session.pinAttempts.save(kkmId, change(previous))
                previous
            }
        }

    override fun clear(kkmId: String) = storage.withSession { it.pinAttempts.delete(kkmId) }
}
