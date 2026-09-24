package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.presentation.api.DeliveryApi
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Досылка чеков покупателям по расписанию.
 *
 * Чек покупателю ядро ставит задачами в базу узла, когда БФД его принял,
 * а отправляет их этот такт: кассир получает ответ сразу, медленный
 * провайдер держит только фон. Задачи, оставшиеся от прошлого запуска
 * узла, уходят первым же тактом. Задачу, за которую взялись одновременно
 * такт и ручной повтор, отдаёт одному из них хранилище.
 *
 * Отказ такта записывается в журнал родом ошибки — её текст может нести
 * адрес покупателя — и не останавливает следующие такты.
 */
@Component
class ReceiptDeliveryWorker(private val delivery: DeliveryApi) {
    private val logger = LoggerFactory.getLogger(ReceiptDeliveryWorker::class.java)

    /** Отправляет всё, чей срок наступил, заходами по [BATCH]. */
    @Scheduled(fixedDelayString = "\${receipt-delivery.worker.interval-ms:5000}", initialDelay = 15000)
    fun sendDueDeliveries() {
        var total = 0
        do {
            val sent = runCatching { delivery.sendDueDeliveries(BATCH) }
                .onFailure { logger.warn("Receipt delivery pass failed: {}", it.javaClass.simpleName) }
                .getOrDefault(0)
            total += sent
        } while (sent == BATCH)
        if (total > 0) logger.info("Receipt delivery worker sent {} tasks", total)
    }

    private companion object {
        /** Задач за заход: полный заход значит, что ждут ещё, и следующий идёт сразу. */
        const val BATCH = 20
    }
}
