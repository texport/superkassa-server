package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Автозакрытие смен по часам.
 *
 * Когда закрывать, решает ядро ([SuperkassaApi.autoCloseShift]): касса
 * с включённым автозакрытием, открытая смена у предела суток, Z-отчёт
 * этой смены БФД не отклонял. Узел только зовёт его для каждой кассы —
 * тем же планировщиком, что и досылку очереди, без своих потоков.
 * Без этого вызова настройка «автозакрытие» на узле ничего не делала.
 */
@Component
class ShiftAutoCloser(
    private val kkmService: SuperkassaApi,
    private val kkms: KkmPages
) {
    private val logger = LoggerFactory.getLogger(ShiftAutoCloser::class.java)

    @Scheduled(fixedDelayString = "\${shift.auto-close.interval-ms:60000}", initialDelay = 30000)
    fun closeDueShifts() {
        kkms.forEach { kkm ->
            runCatching { kkmService.autoCloseShift(kkm.id) }
                .onSuccess { report ->
                    if (report != null) {
                        logger.info("Shift of cashbox {} closed automatically, delivery {}", kkm.id, report.deliveryStatus)
                    }
                }
                .onFailure { logger.warn("Automatic shift close failed for cashbox {}: {}", kkm.id, it.javaClass.simpleName) }
        }
    }
}
