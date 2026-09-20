package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.connector.StorageConnector
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine
import java.sql.Connection

/**
 * JDBC-коннектор для SQLite.
 */
class SqliteConnector : StorageConnector {
    override val engine: StorageEngine = StorageEngine.SQLITE

    override fun connect(config: StorageConfig): Connection {
        if (config.jdbcUrl.lowercase().startsWith("jdbc:sqlite:")) {
            val pathPart = config.jdbcUrl.substring("jdbc:sqlite:".length).substringBefore('?')
            if (pathPart != ":memory:" && pathPart.isNotEmpty()) {
                val file = java.io.File(pathPart)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
            }
        }
        val connection = JdbcSupport.openConnection(withImmediateTransactions(config), "org.sqlite.JDBC")
        prepareForConcurrentUse(connection)
        return connection
    }

    /**
     * Готовит соединение к работе нескольких касс одновременно.
     *
     * По умолчанию SQLite ведёт откатный журнал: пишущий блокирует и читателей,
     * и других пишущих, и при первой же одновременной операции возвращает
     * SQLITE_BUSY. Журнал упреждающей записи снимает блокировку читателей,
     * а ожидание освобождения даёт вторую попытку вместо немедленного отказа.
     */
    /**
     * Просит драйвер начинать транзакции с немедленным взятием блокировки
     * записи.
     *
     * По умолчанию транзакция откладывается: первый оператор читает и берёт
     * снимок, а последующая запись обрывается ошибкой SQLITE_BUSY_SNAPSHOT,
     * если между чтением и записью успел записать кто-то другой. Ожидание
     * освобождения на такой отказ не действует — он приходит сразу. Касса
     * при этом отдаёт ошибку на чеке, хотя данные целы и повтор бы прошёл.
     */
    private fun withImmediateTransactions(config: StorageConfig): StorageConfig =
        config.copy(properties = config.properties + ("transaction_mode" to "IMMEDIATE"))

    private fun prepareForConcurrentUse(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode=WAL")
            statement.execute("PRAGMA busy_timeout=$BUSY_TIMEOUT_MILLIS")
            statement.execute("PRAGMA synchronous=NORMAL")
        }
    }

    private companion object {
        /** Сколько ждать освобождения базы, прежде чем признать её занятой. */
        const val BUSY_TIMEOUT_MILLIS = 30_000
    }
}
