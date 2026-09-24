package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.exception.SuperkassaException
import io.github.texport.superkassa.jvm.storage.impl.application.bootstrap.StorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.JdbcStorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * Сессии базы узла для потока: внутри транзакции — её сессия, вне её —
 * своя на вызов.
 *
 * Отказ базы выходит отсюда отказом ядра: занятый ключ — «запись уже
 * есть», прочее — «хранилище недоступно». Временный сбой открытия
 * соединения повторяется, прежде чем стать отказом.
 */
internal class JdbcSessions(private val bootstrap: StorageBootstrap, private val config: StorageConfig) {
    private val logger = LoggerFactory.getLogger(JdbcSessions::class.java)
    private val sessionHolder = ThreadLocal<StorageSession?>()

    /** Транзакции ядра, вложенные друг в друга в одном соединении потока. */
    val transactions = ThreadTransactions { open() as JdbcStorageSession }

    /** Сессия, в которой идёт вызов этого потока. */
    fun current(): StorageSession = transactions.session() ?: sessionHolder.get() ?: error("No active transaction or session")

    /** Выполняет [block] в сессии транзакции потока, а без неё — в своей. */
    fun <T> withSession(block: (StorageSession) -> T): T = translated {
        val existing = transactions.session() ?: sessionHolder.get()
        if (existing != null) return@translated block(existing)
        open().use { session ->
            sessionHolder.set(session)
            try {
                block(session)
            } finally {
                sessionHolder.remove()
            }
        }
    }

    /** Выполняет [block] одной транзакцией: либо все его записи, либо ни одной. */
    fun <T> inTransaction(block: (StorageSession) -> T): T = translated {
        transactions.begin()
        runCatching { block(current()) }
            .onSuccess { transactions.commit() }
            .onFailure { transactions.rollback() }
            .getOrThrow()
    }

    private fun <T> translated(block: () -> T): T = runCatching(block).getOrElse { throw translate(it) }

    /** Отказ базы — отказом ядра; отказ ядра и ошибка машины проходят как есть. */
    private fun translate(failure: Throwable): Throwable {
        if (failure is SuperkassaException || failure !is Exception) return failure
        logger.error("Storage operation failed: {}", StorageFailures.describe(failure))
        return if (StorageFailures.isUniqueViolation(failure)) {
            StorageFailures.duplicateRecord()
        } else {
            StorageFailures.storageFailure(failure)
        }
    }

    /** Открывает сессию; временный сбой базы повторяется, прежде чем стать отказом. */
    private fun open(): StorageSession {
        var attempt = 1
        while (true) {
            val opened = runCatching { bootstrap.openSession(config) }
            val failure = opened.exceptionOrNull() ?: return opened.getOrThrow()
            if (attempt == OPEN_ATTEMPTS || !isTransientDbFailure(failure)) throw failure
            logger.warn(
                "Storage session open failed (attempt {}/{}), retrying in {}ms: {}",
                attempt,
                OPEN_ATTEMPTS,
                OPEN_RETRY_DELAY_MILLIS,
                StorageFailures.describe(failure)
            )
            Thread.sleep(OPEN_RETRY_DELAY_MILLIS)
            attempt++
        }
    }

    private fun isTransientDbFailure(e: Throwable): Boolean {
        if (e is SQLException) return true
        val msg = e.message?.lowercase() ?: return false
        return TRANSIENT_MARKERS.any { msg.contains(it) }
    }

    private companion object {
        const val OPEN_ATTEMPTS = 3
        const val OPEN_RETRY_DELAY_MILLIS = 200L
        val TRANSIENT_MARKERS = listOf("connection", "timeout", "unavailable", "refused", "network")
    }
}
