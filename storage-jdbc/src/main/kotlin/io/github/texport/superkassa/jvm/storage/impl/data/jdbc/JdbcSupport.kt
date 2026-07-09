package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties

/**
 * Низкоуровневая работа с JDBC, без бизнес-логики.
 */
internal object JdbcSupport {
    /**
     * Открывает JDBC-соединение, регистрируя драйвер.
     */
    fun openConnection(config: StorageConfig, driverClassName: String): Connection {
        Class.forName(driverClassName)
        val props = Properties()
        config.user?.let { props["user"] = it }
        config.password?.let { props["password"] = it }
        for ((key, value) in config.properties) {
            props[key] = value
        }
        return if (props.isEmpty) {
            DriverManager.getConnection(config.jdbcUrl)
        } else {
            DriverManager.getConnection(config.jdbcUrl, props)
        }
    }
}

/**
 * Удобное связывание параметров PreparedStatement.
 */
fun PreparedStatement.bind(vararg params: Any?) {
    for (i in params.indices) {
        val value = params[i]
        val index = i + 1
        when (value) {
            null -> this.setObject(index, null)
            is String -> this.setString(index, value)
            is Long -> this.setLong(index, value)
            is Int -> this.setInt(index, value)
            is Double -> this.setDouble(index, value)
            is Boolean -> this.setBoolean(index, value)
            is ByteArray -> this.setBytes(index, value)
            else -> this.setObject(index, value)
        }
    }
}

/**
 * Маппинг ResultSet в список.
 */
fun <T> ResultSet.mapList(mapper: (ResultSet) -> T): List<T> {
    val list = mutableListOf<T>()
    while (this.next()) {
        list.add(mapper(this))
    }
    return list
}

/**
 * Маппинг ResultSet в один элемент.
 */
@Suppress("unused")
fun <T> ResultSet.mapSingle(mapper: (ResultSet) -> T): T? {
    return if (this.next()) {
        mapper(this)
    } else {
        null
    }
}
