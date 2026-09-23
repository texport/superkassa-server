package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import java.sql.Connection

/**
 * Различия диалектов SQL, которые узлу приходится называть явно.
 *
 * Их два: вставка, которая молча пропускает уже существующую строку,
 * и запирание прочитанной строки до конца транзакции. Остальной SQL
 * хранилища одинаков для SQLite, PostgreSQL и MySQL.
 */
internal class SqlDialect(connection: Connection) {
    private val product = connection.metaData.databaseProductName.lowercase()

    /**
     * Вставка без ошибки на занятом ключе [key]: повтор ничего не меняет.
     *
     * Ошибка вставки в PostgreSQL обрывает всю транзакцию, поэтому занятый
     * ключ нельзя ловить исключением — вставка обязана его пропустить сама.
     *
     * @param insert оператор `INSERT INTO ...`.
     */
    fun insertIfAbsent(insert: String, key: String): String =
        if (product.contains(MYSQL)) {
            insert.replaceFirst("INSERT INTO", "INSERT IGNORE INTO")
        } else {
            "$insert ON CONFLICT ($key) DO NOTHING"
        }

    /**
     * Окончание чтения, запирающее строку до конца транзакции.
     *
     * SQLite запирает базу на запись с начала транзакции целиком, и
     * `FOR UPDATE` у него нет.
     */
    fun lockingRead(): String = if (product.contains(SQLITE)) "" else " FOR UPDATE"

    private companion object {
        const val SQLITE = "sqlite"
        const val MYSQL = "mysql"
    }
}
