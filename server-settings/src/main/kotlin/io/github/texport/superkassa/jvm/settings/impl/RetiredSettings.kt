package io.github.texport.superkassa.jvm.settings.impl

import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Настройки, которые узел больше не читает.
 *
 * Умолчания пинов и имён пользователей разбирались, носились в модели
 * и не использовались: администратора узел заводит пином из запроса на
 * заведение кассы, а кассиров создаёт уже сам администратор. Настройка,
 * которая выглядит рабочей и ничего не делает, — ловушка для того, кто
 * разворачивает кассу на площадке: он задаст пины и решит, что задал.
 *
 * Сами настройки убраны, но файл владельца их ещё содержит, а неизвестные
 * ключи разбор молча пропускает. Поэтому оставшийся ключ называется
 * в журнале — по имени и без значения.
 */
internal object RetiredSettings {

    private val logger = LoggerFactory.getLogger(RetiredSettings::class.java)

    private val retiredKeys = listOf(
        "defaultAdminPin",
        "defaultAdminName",
        "defaultCashierPin",
        "defaultCashierName"
    )

    /** Имена отменённых настроек, которые ещё встречаются в тексте файла. */
    fun retiredKeysIn(text: String): List<String> = retiredKeys.filter { text.contains("\"$it\"") }

    /** Называет отменённые настройки в журнале; значений в нём нет. */
    fun warnOnRetiredKeys(source: Path, text: String) {
        val present = retiredKeysIn(text)
        if (present.isNotEmpty()) {
            logger.warn(
                "Settings file {} names settings the node no longer reads: {}. " +
                    "Their values are ignored; remove the keys. " +
                    "The administrator PIN comes from the cash register registration request.",
                source,
                present.joinToString()
            )
        }
    }
}
