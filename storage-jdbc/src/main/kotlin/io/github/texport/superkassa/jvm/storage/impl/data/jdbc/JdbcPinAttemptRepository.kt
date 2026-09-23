package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.core.domain.api.model.auth.PinAttempts
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.PinAttemptRepository
import java.sql.Connection

/**
 * JDBC-реализация счёта неверных пинов (pin_attempts).
 *
 * Строка берётся под запись первым же оператором транзакции — вставкой
 * пустого счёта, если его нет. Так вторая попытка той же кассы ждёт
 * первую и читает уже её итог: SQLite запирает базу на запись,
 * PostgreSQL и MySQL — строку, а чтение `FOR UPDATE` держит её до конца.
 */
class JdbcPinAttemptRepository(private val connection: Connection) : PinAttemptRepository {
    private val product = connection.metaData.databaseProductName.lowercase()

    override fun lock(cashboxId: String): PinAttempts {
        connection.prepareStatement(insertIfAbsent()).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
        }
        val lockClause = if (product.contains(SQLITE)) "" else " FOR UPDATE"
        val sql = "SELECT failures, locked_until FROM pin_attempts WHERE cashbox_id = ?$lockClause"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                rs.next()
                return PinAttempts(failures = rs.getInt("failures"), lockedUntil = rs.getLong("locked_until"))
            }
        }
    }

    override fun save(cashboxId: String, attempts: PinAttempts) {
        val sql = "UPDATE pin_attempts SET failures = ?, locked_until = ? WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, attempts.failures)
            stmt.setLong(2, attempts.lockedUntil)
            stmt.setString(3, cashboxId)
            stmt.executeUpdate()
        }
    }

    override fun delete(cashboxId: String) {
        connection.prepareStatement("DELETE FROM pin_attempts WHERE cashbox_id = ?").use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
        }
    }

    private fun insertIfAbsent(): String {
        val columns = "pin_attempts (cashbox_id, failures, locked_until) VALUES (?, 0, 0)"
        return if (product.contains(MYSQL)) {
            "INSERT IGNORE INTO $columns"
        } else {
            "INSERT INTO $columns ON CONFLICT (cashbox_id) DO NOTHING"
        }
    }

    private companion object {
        const val SQLITE = "sqlite"
        const val MYSQL = "mysql"
    }
}
