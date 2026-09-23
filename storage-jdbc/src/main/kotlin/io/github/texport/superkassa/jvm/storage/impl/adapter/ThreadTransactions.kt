package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.JdbcStorageSession
import java.sql.Savepoint

/**
 * Транзакции хранилища, привязанные к потоку.
 *
 * Ядро вкладывает транзакции друг в друга: закрытие смены идёт в своей,
 * а обмен с ОФД внутри него — в своей. Прежде вложенная транзакция
 * открывала второе соединение: SQLite ждал, пока первое отпустит базу,
 * и через полминуты отказывал, а внешняя транзакция теряла своё
 * соединение и не фиксировалась вовсе. Теперь вложенная транзакция
 * идёт в том же соединении точкой сохранения: её откат снимает только
 * её записи, а фиксирует всё внешняя.
 *
 * @property open открывает соединение для внешней транзакции.
 */
internal class ThreadTransactions(private val open: () -> JdbcStorageSession) {
    private val frames = ThreadLocal<Frame?>()

    /** Сессия транзакции этого потока или `null`, если транзакции нет. */
    fun session(): JdbcStorageSession? = frames.get()?.session

    fun begin() {
        val frame = frames.get()
        if (frame == null) {
            val session = open()
            session.connection.autoCommit = false
            frames.set(Frame(session))
        } else {
            frame.savepoints.addLast(frame.session.connection.setSavepoint())
        }
    }

    fun commit() {
        val frame = frames.get() ?: return
        val savepoint = frame.savepoints.removeLastOrNull()
        if (savepoint != null) {
            frame.session.connection.releaseSavepoint(savepoint)
        } else {
            finish(frame) { it.connection.commit() }
        }
    }

    fun rollback() {
        val frame = frames.get() ?: return
        val savepoint = frame.savepoints.removeLastOrNull()
        if (savepoint != null) {
            frame.session.connection.rollback(savepoint)
        } else {
            finish(frame) { it.connection.rollback() }
        }
    }

    private fun finish(frame: Frame, end: (JdbcStorageSession) -> Unit) {
        try {
            end(frame.session)
        } finally {
            frames.remove()
            frame.session.close()
        }
    }

    private class Frame(val session: JdbcStorageSession) {
        val savepoints = ArrayDeque<Savepoint>()
    }
}
