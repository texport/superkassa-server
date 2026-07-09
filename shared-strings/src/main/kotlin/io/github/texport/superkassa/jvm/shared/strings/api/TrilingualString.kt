package io.github.texport.superkassa.jvm.shared.strings.api

import kotlinx.serialization.Serializable

/**
 * Представляет локализованное пользовательское сообщение, поддерживающее английский, русский и казахский языки.
 * Этот класс сериализуем для возможности передачи в JSON-ответах между модулями по HTTP.
 *
 * Потокобезопасность: Этот класс является неизменяемым (immutable) и полностью потокобезопасным.
 *
 * @property en Английская версия сообщения.
 * @property ru Русская версия сообщения.
 * @property kk Казахская версия сообщения.
 */
@Serializable
data class TrilingualString(
    val en: String,
    val ru: String,
    val kk: String
) {
    /**
     * Возвращает текст перевода для запрошенного кода языка.
     *
     * @param lang Целевой код языка (например, "en", "ru", "kk"). Регистронезависимый.
     * @return Строка перевода, соответствующая запрошенному языку, либо русская версия по умолчанию, если язык не поддерживается.
     */
    fun format(lang: String): String = when (lang.lowercase()) {
        "kk" -> kk
        "en" -> en
        else -> ru
    }

    /**
     * Заменяет плейсхолдеры формата `{index}` строковым представлением предоставленных аргументов.
     * Возвращает новый экземпляр [TrilingualString] с замененными значениями для всех трех языков.
     *
     * @param args Аргументы для заполнения плейсхолдеров по порядку (например, `{0}`, `{1}`).
     * @return Новый экземпляр [TrilingualString], содержащий отформатированные тексты.
     */
    fun formatArgs(vararg args: Any): TrilingualString {
        var formattedEn = en
        var formattedRu = ru
        var formattedKk = kk
        args.forEachIndexed { index, arg ->
            val placeholder = "{$index}"
            formattedEn = formattedEn.replace(placeholder, arg.toString())
            formattedRu = formattedRu.replace(placeholder, arg.toString())
            formattedKk = formattedKk.replace(placeholder, arg.toString())
        }
        return TrilingualString(formattedEn, formattedRu, formattedKk)
    }

    /**
     * Возвращает составное представление всех трех переводов.
     * Формат: `[EN] {en} / [RU] {ru} / [KK] {kk}`
     *
     * @return Строка, объединяющая все три локализованных текста.
     */
    override fun toString(): String = "[EN] $en / [RU] $ru / [KK] $kk"
}
