package kz.mybrain.superkassa.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SuperkassaApplication

fun main(args: Array<String>) {
    // Отключаем логгер JDK для openhtmltopdf, чтобы не спамил предупреждениями о CSS3
    System.setProperty("xr.util-logging.loggingEnabled", "false")
    java.util.logging.Logger.getLogger("com.openhtmltopdf").level = java.util.logging.Level.OFF

    val envStart = System.getenv("START_REQ_NUM")?.toLongOrNull()
    if (envStart != null) {
        kz.mybrain.superkassa.core.domain.usecase.ofd.GenerateRequestNumberUseCase.startReqNumOverride = envStart
    }

    org.springframework.boot.SpringApplication.run(arrayOf(SuperkassaApplication::class.java), args)
}
