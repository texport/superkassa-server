package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.mockk.mockk
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Границы сумм действуют и для точных чисел.
 *
 * Пока проверяющего не было, запрос с суммой падал внутренней ошибкой:
 * `HV000030: No validator could be found ... validating type Decimal`.
 */
class DecimalValidationConfigTest {

    private val validator = DecimalValidationConfig().validator().apply {
        afterPropertiesSet()
    }

    @Test
    fun refusesAmountBelowTheBound() {
        val problems = validator.validate(Deposit(Decimal.ZERO))

        assertEquals(1, problems.size)
        assertEquals("сумма должна быть положительной", problems.single().message)
    }

    @Test
    fun acceptsAmountOnTheBound() {
        assertTrue(validator.validate(Deposit(Decimal.parse("0.01"))).isEmpty())
        assertTrue(validator.validate(Deposit(Decimal.parse("5000.55"))).isEmpty())
    }

    @Test
    fun refusesAmountAboveTheUpperBound() {
        assertEquals(1, validator.validate(Deposit(Decimal.parse("1000000.00"))).size)
    }

    @Test
    fun leavesTheMissingAmountToOtherChecks() {
        assertTrue(validator.validate(Deposit(null)).isEmpty())
    }

    @Test
    fun refusesAmountOnAnExclusiveBound() {
        // «Строго больше» и «строго меньше» — те же границы без самой границы.
        assertEquals(1, validator.validate(Strict(Decimal.parse("0.01"))).size)
        assertEquals(1, validator.validate(Strict(Decimal.parse("999999.99"))).size)
        assertTrue(validator.validate(Strict(Decimal.parse("0.02"))).isEmpty())
    }

    @Test
    fun leavesAlienConfigurationAlone() {
        // Не у всякой реализации проверок есть отображение ограничений
        // Hibernate: чужую конфигурацию трогать нечем.
        DecimalAwareValidator().postProcessConfiguration(mockk(relaxed = true))
    }

    data class Strict(
        @field:DecimalMin("0.01", inclusive = false)
        @field:DecimalMax("999999.99", inclusive = false)
        val amount: Decimal?
    )

    data class Deposit(
        @field:DecimalMin("0.01", message = "сумма должна быть положительной")
        @field:DecimalMax("999999.99")
        val amount: Decimal?
    )
}
