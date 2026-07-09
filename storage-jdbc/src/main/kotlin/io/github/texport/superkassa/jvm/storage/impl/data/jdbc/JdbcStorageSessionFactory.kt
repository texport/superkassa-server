package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSessionFactory
import java.sql.Connection

/**
 * JDBC-фабрика сессий хранения.
 */
class JdbcStorageSessionFactory : StorageSessionFactory {
    override fun open(connection: Connection): StorageSession {
        return JdbcStorageSession(connection)
    }
}
