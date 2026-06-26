package io.github.texport.superkassa.jvm.settings.validation

internal const val MIN_PORT = 1
internal const val MAX_PORT = 65535
internal const val MIN_OFD_TIMEOUT_SECONDS = 5
internal const val MIN_OFD_RECONNECT_INTERVAL_SECONDS = 60

private val telegramBotTokenRegex = Regex("""^\d+:[A-Za-z0-9_-]{35}$""")

internal fun errorMsg(en: String, ru: String, kk: String): String {
    return "[EN] $en / [RU] $ru / [KK] $kk"
}

internal fun isValidEmail(email: String): Boolean {
    return email.contains("@") && email.contains(".") && !email.contains(" ")
}

internal fun isValidUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://")
}

internal fun isValidPort(port: Int?): Boolean {
    return port != null && port in MIN_PORT..MAX_PORT
}

internal fun isDigitsOnly(s: String): Boolean {
    return s.isNotEmpty() && s.all { it.isDigit() }
}

internal fun isValidPhoneNumber(s: String): Boolean {
    if (s.isBlank()) return false
    val clean = if (s.startsWith("+")) s.substring(1) else s
    return clean.isNotEmpty() && clean.all { it.isDigit() }
}

internal fun isValidTelegramChatId(s: String): Boolean {
    if (s.isBlank()) return false
    val clean = if (s.startsWith("-")) s.substring(1) else s
    return clean.isNotEmpty() && clean.all { it.isDigit() }
}

internal fun isValidTelegramBotToken(token: String): Boolean {
    return telegramBotTokenRegex.matches(token)
}
