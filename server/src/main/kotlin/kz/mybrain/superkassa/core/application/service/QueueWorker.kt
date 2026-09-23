package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Воркер OFFLINE-очереди. Периодически досылает в ОФД документы всех касс узла.
 *
 * Отказ досылки одной кассы записывается в журнал и не останавливает
 * обход: прежде исключение на одной кассе оставляло очереди всех
 * следующих недосланными до следующего такта.
 */
@Component
class QueueWorker(
    private val kkmService: SuperkassaApi,
    private val kkms: KkmPages
) {
    private val logger = LoggerFactory.getLogger(QueueWorker::class.java)

    @Scheduled(fixedDelayString = "\${offline-queue.worker.interval-ms:5000}", initialDelay = 10000)
    fun processOfflineQueues() {
        kkms.forEach { kkm ->
            runCatching { kkmService.queue.processOfflineBatch(kkm.id, limit = BATCH) }
                .onSuccess { processed ->
                    if (processed > 0) logger.info("Queue worker processed {} commands for cashbox {}", processed, kkm.id)
                }
                .onFailure { logger.warn("Queue worker failed for cashbox {}: {}", kkm.id, it.javaClass.simpleName) }
        }
    }

    private companion object {
        /** Документов кассы за такт: очередь одной кассы не задерживает другие надолго. */
        const val BATCH = 5
    }
}
