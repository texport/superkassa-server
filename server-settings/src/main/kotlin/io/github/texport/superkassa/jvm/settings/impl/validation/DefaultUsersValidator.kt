package io.github.texport.superkassa.jvm.settings.impl.validation

import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.jvm.settings.api.IllegalServerConfigurationException
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import org.slf4j.LoggerFactory

/** Замечание к одинаковым умолчаниям пинов; значений в нём нет. */
internal const val IDENTICAL_DEFAULT_PINS_WARNING =
    "Settings defaultAdminPin and defaultCashierPin hold the same value, " +
        "so a cash register would get one user instead of two. Set different values."

/**
 * Проверяет умолчания, из которых узел заводит пользователей новой кассы.
 *
 * Пин уникален в пределах кассы: с одинаковыми умолчаниями второй
 * пользователь упирается в эту уникальность. Значения пинов не попадают
 * ни в отказ, ни в журнал.
 */
internal object DefaultUsersValidator {

    private val logger = LoggerFactory.getLogger(DefaultUsersValidator::class.java)

    private val resolver = DefaultErrorResolver()

    /** Записывать настройки с одинаковыми умолчаниями пинов нельзя. */
    fun validateDefaultPins(settings: CoreSettings) {
        if (identicalDefaultPins(settings)) {
            throw IllegalServerConfigurationException(
                resolver.resolve(SettingsErrorKey.DEFAULT_PINS_IDENTICAL).toString()
            )
        }
    }

    /**
     * Уже сохранённые настройки узел принимает: касса дороже аккуратности
     * файла, а поднять узел она обязана. Замечание уходит в журнал.
     */
    fun warnOnIdenticalDefaultPins(settings: CoreSettings) {
        if (identicalDefaultPins(settings)) {
            logger.warn(IDENTICAL_DEFAULT_PINS_WARNING)
        }
    }

    private fun identicalDefaultPins(settings: CoreSettings): Boolean =
        settings.defaultAdminPin == settings.defaultCashierPin
}
