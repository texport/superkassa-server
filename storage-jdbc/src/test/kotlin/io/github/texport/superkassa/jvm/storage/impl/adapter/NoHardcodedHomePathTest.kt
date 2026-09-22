package io.github.texport.superkassa.jvm.storage.impl.adapter

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ни один рабочий файл не называет путь внутри чьего-то домашнего каталога.
 *
 * Два кэша хранились по вписанному в код пути в личном каталоге посторонней
 * программы: на другой машине его не существует, а рядом с рабочим местом
 * владельца лежали файлы, которые никто не переносит вместе с базой.
 */
class NoHardcodedHomePathTest {

    private val homePrefixes = listOf("/Users/", "/home/", ":\\Users\\")

    @Test
    fun `production sources name no absolute home path`() {
        val sources = Paths.get("src/main/kotlin")
        assertTrue(Files.isDirectory(sources), "не найден каталог исходников: ${sources.toAbsolutePath()}")

        Files.walk(sources).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.forEach { source ->
                val text = Files.readString(source)
                homePrefixes.forEach { prefix ->
                    assertTrue(!text.contains(prefix), "$source называет путь «$prefix»")
                }
            }
        }
    }
}
