package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.hibernate.validator.HibernateValidatorConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import jakarta.validation.Configuration as ValidationConfiguration

/**
 * Границы сумм проверяются и для точных чисел.
 *
 * `@DecimalMin` и `@DecimalMax` штатно умеют работать со строками и с числами
 * платформы, но не с [Decimal]. Без этих проверяющих запрос с суммой падал бы
 * внутренней ошибкой вместо понятного отказа.
 */
@Configuration
class DecimalValidationConfig {

    @Bean
    fun validator(): LocalValidatorFactoryBean = DecimalAwareValidator()
}

/** Проверяющий, знающий про точные числа. */
class DecimalAwareValidator : LocalValidatorFactoryBean() {
    public override fun postProcessConfiguration(configuration: ValidationConfiguration<*>) {
        val hibernate = configuration as? HibernateValidatorConfiguration ?: return
        val mapping = hibernate.createConstraintMapping()
        mapping.constraintDefinition(DecimalMin::class.java)
            .includeExistingValidators(true)
            .validatedBy(DecimalMinForDecimal::class.java)
        mapping.constraintDefinition(DecimalMax::class.java)
            .includeExistingValidators(true)
            .validatedBy(DecimalMaxForDecimal::class.java)
        hibernate.addMapping(mapping)
    }
}

/** Сумма не меньше объявленной границы. */
class DecimalMinForDecimal : ConstraintValidator<DecimalMin, Decimal> {

    private var bound: Decimal = Decimal.ZERO
    private var inclusive: Boolean = true

    override fun initialize(annotation: DecimalMin) {
        bound = Decimal.parse(annotation.value)
        inclusive = annotation.inclusive
    }

    override fun isValid(value: Decimal?, context: ConstraintValidatorContext): Boolean {
        val actual = value ?: return true
        return if (inclusive) actual >= bound else actual > bound
    }
}

/** Сумма не больше объявленной границы. */
class DecimalMaxForDecimal : ConstraintValidator<DecimalMax, Decimal> {

    private var bound: Decimal = Decimal.ZERO
    private var inclusive: Boolean = true

    override fun initialize(annotation: DecimalMax) {
        bound = Decimal.parse(annotation.value)
        inclusive = annotation.inclusive
    }

    override fun isValid(value: Decimal?, context: ConstraintValidatorContext): Boolean {
        val actual = value ?: return true
        return if (inclusive) actual <= bound else actual < bound
    }
}
