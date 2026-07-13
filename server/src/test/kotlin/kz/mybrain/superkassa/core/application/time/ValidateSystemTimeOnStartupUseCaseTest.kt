package kz.mybrain.superkassa.core.application.time

import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.github.texport.superkassa.core.domain.api.model.common.TimeValidationResult
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ValidateSystemTimeOnStartupUseCaseTest {

    private val clock = object : ClockPort {
        override fun now(): Long = 1_700_000_000_000L
        override fun currentYear(): Int = 2023
        override fun parseDateTimeToMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
            1_700_000_000_000L
    }

    @Test
    fun `execute returns successful validation result`() {
        val expected = TimeValidationResult(ok = true, reason = null)
        val useCase = ValidateSystemTimeOnStartupUseCase(
            timeValidator = validatorReturning(expected),
            clock = clock
        )

        val result = useCase.execute()

        assertSame(expected, result)
    }

    @Test
    fun `execute fails startup when validator rejects time`() {
        val failed = TimeValidationResult(
            ok = false,
            reason = "RANGE",
            trilingualMessage = TrilingualMessage(
                ru = "Системное время вне допустимого диапазона",
                kk = "Жүйелік уақыт рұқсат етілген ауқымнан тыс",
                en = "System time is out of allowed range"
            )
        )
        val useCase = ValidateSystemTimeOnStartupUseCase(
            timeValidator = validatorReturning(failed),
            clock = clock
        )

        val exception = assertFailsWith<SystemTimeStartupValidationException> {
            useCase.execute()
        }

        assertSame(failed, exception.validationResult)
        assertTrue(exception.message.orEmpty().contains("RANGE"))
        assertTrue(exception.message.orEmpty().contains("Системное время вне допустимого диапазона"))
        assertTrue(exception.message.orEmpty().contains("Жүйелік уақыт рұқсат етілген ауқымнан тыс"))
        assertTrue(exception.message.orEmpty().contains("System time is out of allowed range"))
    }

    @Test
    fun `execute uses fallback details when validation failure has no reason or message`() {
        val failed = TimeValidationResult(ok = false, reason = null)
        val useCase = ValidateSystemTimeOnStartupUseCase(
            timeValidator = validatorReturning(failed),
            clock = clock
        )

        val exception = assertFailsWith<SystemTimeStartupValidationException> {
            useCase.execute()
        }

        assertTrue(exception.message.orEmpty().contains("UNKNOWN"))
        assertTrue(exception.message.orEmpty().contains("Проверка системного времени при запуске не пройдена"))
        assertTrue(exception.message.orEmpty().contains("Іске қосу кезінде жүйелік уақыт тексерісі өтпеді"))
        assertTrue(exception.message.orEmpty().contains("System time validation failed on startup"))
    }

    @Test
    fun `execute passes injected clock to validator`() {
        var clockReceivedByValidator: ClockPort? = null
        val useCase = ValidateSystemTimeOnStartupUseCase(
            timeValidator = object : TimeValidatorPort {
                override fun validate(clock: ClockPort): TimeValidationResult {
                    clockReceivedByValidator = clock
                    return TimeValidationResult(ok = true, reason = null)
                }
            },
            clock = clock
        )

        useCase.execute()

        assertEquals(clock, clockReceivedByValidator)
    }

    private fun validatorReturning(result: TimeValidationResult): TimeValidatorPort =
        object : TimeValidatorPort {
            override fun validate(clock: ClockPort): TimeValidationResult = result
        }
}
