package io.github.texport.superkassa.jvm.storage.impl.application.connector

import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSessionFactory
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import org.slf4j.LoggerFactory

/**
 * Точка входа для открытия сессии хранения.
 *
 * Зачем это нужно:
 * - выбрать нужный коннектор по движку БД,
 * - создать StorageSession через фабрику,
 * - скрыть детали подключения от бизнес-логики.
 */
class StorageManager(
    private val registry: StorageConnectorRegistry,
    private val sessionFactory: StorageSessionFactory
) {
    private val logger = LoggerFactory.getLogger(StorageManager::class.java)

    /**
     * Открывает новую сессию хранения.
     * Возвращает фасад, через который работают репозитории.
     */
    fun openSession(config: StorageConfig): StorageSession {
        val engine = config.resolvedEngine()
        logger.debug("Opening storage session. engine={}", engine)
        val connection = registry.connectorFor(engine).connect(config)
        logger.debug("Storage session connection opened. engine={}", engine)
        return sessionFactory.open(connection)
    }
}
