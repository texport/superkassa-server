package kz.mybrain.superkassa.core.application.common

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Logback Converter для фильтрации трехъязычных сообщений логов.
 * Если задана системная переменная `superkassa.log.language` (RU, KK, EN),
 * конвертер заменяет подстроки формата "RU: текст | KK: текст | EN: текст"
 * на текст на выбранном языке.
 */
class TrilingualLogConverter : ClassicConverter() {
    private val trilingualRegex = Regex("""RU:\s*(.*?)\s*\|\s*KK:\s*(.*?)\s*\|\s*EN:\s*(.*?)(?=\||$)""")

    override fun convert(event: ILoggingEvent): String {
        val msg = event.formattedMessage ?: return ""
        val logLang = System.getProperty("superkassa.log.language", "ALL").trim().uppercase()
        if (logLang == "ALL" || logLang == "MIXED") {
            return msg
        }
        return trilingualRegex.replace(msg) { matchResult ->
            when (logLang) {
                "RU" -> matchResult.groupValues[1]
                "KK" -> matchResult.groupValues[2]
                "EN" -> matchResult.groupValues[3]
                else -> matchResult.value
            }
        }
    }
}
