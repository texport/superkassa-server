package io.github.texport.superkassa.jvm.delivery.impl

/**
 * Прячет ключи каналов доставки в тексте, который уходит в журнал или наружу.
 *
 * Ключ канала — такой же ключ, как токен БФД: кто его прочитал, тот шлёт
 * сообщения от имени узла. А живёт он в адресе запроса: Telegram держит токен
 * бота в пути, SMS-провайдеры — в параметре запроса. Адрес попадает в текст
 * исключения HTTP-клиента и в ответ провайдера, а оттуда — в журнал и в ответ
 * API. Поэтому прячется он в одном месте для всех каналов, а не там, где
 * вспомнили.
 */
internal object Secrets {

    private const val HIDDEN = "***"

    /** Токен в пути: `/bot<токен>/sendMessage`. */
    private val inPath = Regex("""/bot[^/\s"']+""", RegexOption.IGNORE_CASE)

    /** Ключ в параметрах запроса: `?api_key=<ключ>&text=…`. */
    private val inQuery = Regex(
        """\b(api[_-]?key|access[_-]?token|auth[_-]?token|token|key|secret|password)=[^&\s"'#]+""",
        RegexOption.IGNORE_CASE
    )

    /** Ключ в заголовке авторизации, если он попал в текст ошибки. */
    private val inHeader = Regex("""\bBearer\s+[^\s"']+""", RegexOption.IGNORE_CASE)

    fun mask(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(inPath, "/bot$HIDDEN")
            .replace(inQuery) { "${it.groupValues[1]}=$HIDDEN" }
            .replace(inHeader, "Bearer $HIDDEN")
    }
}
