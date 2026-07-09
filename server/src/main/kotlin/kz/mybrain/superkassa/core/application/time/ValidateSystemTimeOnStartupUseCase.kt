package kz.mybrain.superkassa.core.application.time

import kz.mybrain.superkassa.core.domain.model.common.TimeValidationResult
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.TimeValidatorPort

class ValidateSystemTimeOnStartupUseCase(
    private val timeValidator: TimeValidatorPort,
    private val clock: ClockPort
) {
    fun execute(): TimeValidationResult {
        val result = timeValidator.validate(clock)
        if (!result.ok) {
            throw SystemTimeStartupValidationException(result)
        }
        return result
    }
}

class SystemTimeStartupValidationException(
    val validationResult: TimeValidationResult
) : IllegalStateException(
    startupValidationMessage(validationResult)
)

private fun startupValidationMessage(result: TimeValidationResult): String {
    val reason = result.reason ?: "UNKNOWN"
    val ru = result.messageRu ?: "Проверка системного времени при запуске не пройдена"
    val kk = result.messageKk ?: "Іске қосу кезінде жүйелік уақыт тексерісі өтпеді"
    val en = result.messageEn ?: "System time validation failed on startup"

    return "RU: $ru (reason=$reason) | KK: $kk (reason=$reason) | EN: $en (reason=$reason)"
}
