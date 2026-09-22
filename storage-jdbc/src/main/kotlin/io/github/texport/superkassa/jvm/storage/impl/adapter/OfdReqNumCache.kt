package io.github.texport.superkassa.jvm.storage.impl.adapter

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Отладочный кэш номера обмена с БФД, лежащий в каталоге узла.
 *
 * Файл жил по вписанному в код пути внутри личного каталога посторонней
 * программы. Состояние протокола оказывалось вне дома узла: на другой
 * машине его не было вовсе, а рядом с рабочим местом владельца лежал
 * файл, который никто не переносит и не сохраняет вместе с базой.
 * Отказы чтения и записи при этом проглатывались молча, поэтому
 * потерянный номер обмена ничем себя не выдавал.
 *
 * Кэш включается свойством `superkassa.debug-cache` и к работе кассы
 * не обязателен: его отсутствие — не отказ, но оно должно быть видно.
 */
internal class OfdReqNumCache(val file: Path = defaultFile()) {

    private val logger = LoggerFactory.getLogger(OfdReqNumCache::class.java)

    /** Номер обмена из кэша или null, если его нет или файл нечитаем. */
    fun read(): Long? {
        if (!Files.isRegularFile(file)) return null
        return try {
            val value = Files.readString(file).trim().toLongOrNull()
            if (value == null) {
                logger.warn("BFD reqNum cache at {} holds no number, ignoring it", file)
            }
            value
        } catch (e: IOException) {
            logger.warn("Cannot read BFD reqNum cache at {}: {}", file, e.message)
            null
        }
    }

    /** Складывает номер обмена в кэш; неудачу называет, а не прячет. */
    fun write(value: Long) {
        try {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, "$value\n")
        } catch (e: IOException) {
            logger.warn("Cannot write BFD reqNum cache at {}: {}", file, e.message)
        }
    }

    companion object {

        /** Свойство, которым запускающий называет каталог узла. */
        const val HOME_PROPERTY = "superkassa.home"

        /** Кэш лежит рядом с базой, внутри каталога узла. */
        const val CACHE_FILE = "data/ofd-req-num.txt"

        /** Каталог узла по свойству; без него — каталог запуска, как и у базы. */
        fun defaultFile(): Path {
            val home = System.getProperty(HOME_PROPERTY)?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.dir")
            return Paths.get(home).toAbsolutePath().normalize().resolve(CACHE_FILE)
        }
    }
}
