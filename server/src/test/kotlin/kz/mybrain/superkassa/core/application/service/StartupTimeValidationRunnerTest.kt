package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.time.SystemTimeStartupValidationException
import kz.mybrain.superkassa.core.application.time.ValidateSystemTimeOnStartupUseCase
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.github.texport.superkassa.core.domain.api.model.common.TimeValidationResult
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import org.springframework.boot.ApplicationArguments
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StartupTimeValidationRunnerTest {

    private val args = object : ApplicationArguments {
        override fun getSourceArgs(): Array<String> = emptyArray()
        override fun getOptionNames(): MutableSet<String> = mutableSetOf()
        override fun containsOption(name: String): Boolean = false
        override fun getOptionValues(name: String): MutableList<String>? = null
        override fun getNonOptionArgs(): MutableList<String> = mutableListOf()
    }

    private val clock = object : ClockPort {
        override fun now(): Long = 1_700_000_000_000L
        override fun currentYear(): Int = 2023
        override fun parseDateTimeToMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
            1_700_000_000_000L
    }

    @Test
    fun `run completes when startup time validation passes`() {
        val runner = StartupTimeValidationRunner(
            ValidateSystemTimeOnStartupUseCase(
                timeValidator = validatorReturning(TimeValidationResult(ok = true, reason = null)),
                clock = clock
            )
        )

        runner.run(args)
    }

    @Test
    fun `run propagates startup validation failure`() {
        val runner = StartupTimeValidationRunner(
            ValidateSystemTimeOnStartupUseCase(
                timeValidator = validatorReturning(
                    TimeValidationResult(
                        ok = false,
                        reason = "REFERENCE_SKEW",
                        trilingualMessage = TrilingualMessage(
                            ru = "Время рассинхронизировано с эталонным",
                            kk = "Уақыт эталонды уақытпен синхрондалмаған",
                            en = "Time is desynchronized from reference source"
                        )
                    )
                ),
                clock = clock
            )
        )

        assertFailsWith<SystemTimeStartupValidationException> {
            runner.run(args)
        }
    }

    private fun validatorReturning(result: TimeValidationResult): TimeValidatorPort =
        object : TimeValidatorPort {
            override fun validate(clock: ClockPort): TimeValidationResult = result
        }
}
