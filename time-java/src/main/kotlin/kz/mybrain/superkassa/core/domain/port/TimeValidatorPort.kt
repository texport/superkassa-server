package kz.mybrain.superkassa.core.domain.port

/**
 * Результат валидации системного времени.
 */
data class TimeValidationResult(
    val ok: Boolean,
    val reason: String? = null,
    val messageRu: String? = null,
    val messageKk: String? = null,
    val messageEn: String? = null
) {
    fun trilingualMessage(): String? {
        if (messageRu == null || messageKk == null || messageEn == null) return null
        return "RU: $messageRu | KK: $messageKk | EN: $messageEn"
    }
}

/**
 * Порт для проверки корректности системного времени ККМ.
 */
interface TimeValidatorPort {
    /**
     * Выполняет валидацию времени по часам ККМ.
     */
    fun validate(clock: ClockPort): TimeValidationResult
}
