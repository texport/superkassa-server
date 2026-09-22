package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.exception.ConflictException
import io.github.texport.superkassa.core.domain.api.exception.StorageException
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import java.sql.SQLException

/**
 * Переводит отказ драйвера в отказ, который читает человек.
 *
 * Текст драйвера сюда входит, но дальше не идёт ни в ответе, ни в журнале:
 * в нём имена таблиц и колонок. Кассир, ошибившийся пином, читал устройство
 * хранилища вместо объяснения, а узел заодно рассказывал его всякому,
 * кто до этого экрана дошёл.
 */
internal object StorageFailures {

    private const val POSTGRES_UNIQUE_VIOLATION = "23505"

    private val resolver = DefaultErrorResolver()

    /** Отказ вызван тем, что такое значение в хранилище уже лежит. */
    fun isUniqueViolation(failure: Throwable): Boolean =
        sqlCauses(failure).any { it.tellsUniqueViolation() }

    /** Пин на этой кассе уже принадлежит другому пользователю. */
    fun userPinTaken(): ConflictException =
        ConflictException(message(StorageErrorKey.USER_PIN_TAKEN), StorageErrorKey.USER_PIN_TAKEN.code)

    /** Совпало что-то другое; что именно — наружу не идёт. */
    fun duplicateRecord(): ConflictException =
        ConflictException(message(StorageErrorKey.DUPLICATE_RECORD), StorageErrorKey.DUPLICATE_RECORD.code)

    /** Прочий сбой хранилища, без единого слова драйвера. */
    fun storageFailure(cause: Throwable): StorageException =
        StorageException(message(StorageErrorKey.DATABASE_ERROR), cause = cause)

    /** Что можно записать в журнал: тип отказа и коды драйвера, но не его текст. */
    fun describe(failure: Throwable): String {
        val sql = sqlCauses(failure).firstOrNull()
        return "${failure::class.java.name} sqlState=${sql?.sqlState ?: "-"} vendorCode=${sql?.errorCode ?: "-"}"
    }

    private fun message(key: ErrorKey): TrilingualMessage {
        val text = resolver.resolve(key)
        return TrilingualMessage(ru = text.ru, kk = text.kk, en = text.en)
    }

    private fun sqlCauses(failure: Throwable): Sequence<SQLException> =
        generateSequence(failure) { current -> current.cause?.takeIf { it !== current } }
            .filterIsInstance<SQLException>()

    private fun SQLException.tellsUniqueViolation(): Boolean {
        if (sqlState == POSTGRES_UNIQUE_VIOLATION) return true
        val text = message?.lowercase() ?: return false
        return (text.contains("unique") && text.contains("constraint")) || text.contains("duplicate entry")
    }
}
