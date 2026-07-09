package kz.mybrain.superkassa.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SuperkassaApplication

@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun main(args: Array<String>) {
    // Отключаем логгер JDK для openhtmltopdf, чтобы не спамил предупреждениями о CSS3
    System.setProperty("xr.util-logging.loggingEnabled", "false")
    try {
        Class.forName("com.openhtmltopdf.util.XRLog")
            .getMethod("setLoggingEnabled", Boolean::class.javaPrimitiveType)
            .invoke(null, false)
    } catch (e: Throwable) {
        // ignore
    }
    try {
        java.util.logging.Logger.getLogger("com.openhtmltopdf").level = java.util.logging.Level.OFF
    } catch (e: Throwable) {
        // ignore
    }

    org.springframework.boot.SpringApplication.run(arrayOf(SuperkassaApplication::class.java), args)
}
