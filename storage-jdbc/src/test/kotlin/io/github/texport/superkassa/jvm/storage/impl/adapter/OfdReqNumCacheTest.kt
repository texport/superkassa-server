package io.github.texport.superkassa.jvm.storage.impl.adapter

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Кэш номера обмена с БФД лежит в каталоге узла, а не по вписанному пути.
 *
 * Прежде файл жил в личном каталоге посторонней программы: состояние
 * протокола оказывалось вне дома узла, на другой машине его не было вовсе,
 * а любой отказ чтения и записи исчезал молча.
 */
class OfdReqNumCacheTest {

    @Test
    fun `cache lives inside the node home`() {
        val home = Files.createTempDirectory("node-home")
        val previous = System.getProperty(OfdReqNumCache.HOME_PROPERTY)
        System.setProperty(OfdReqNumCache.HOME_PROPERTY, home.toString())
        try {
            assertEquals(home.resolve(OfdReqNumCache.CACHE_FILE), OfdReqNumCache().file)
        } finally {
            if (previous == null) {
                System.clearProperty(OfdReqNumCache.HOME_PROPERTY)
            } else {
                System.setProperty(OfdReqNumCache.HOME_PROPERTY, previous)
            }
        }
    }

    @Test
    fun `written number comes back`() {
        val cache = OfdReqNumCache(Files.createTempDirectory("req-num").resolve("data/ofd-req-num.txt"))

        cache.write(1274L)

        assertEquals(1274L, cache.read())
    }

    @Test
    fun `missing and unreadable cache answer with no number`() {
        val dir = Files.createTempDirectory("req-num-empty")
        assertNull(OfdReqNumCache(dir.resolve("data/ofd-req-num.txt")).read())

        val garbled = dir.resolve("garbled.txt")
        Files.writeString(garbled, "не число\n")
        assertNull(OfdReqNumCache(garbled).read())
    }

    /** Неудачная запись не срывает операцию кассы, но и не исчезает молча. */
    @Test
    fun `write onto an impossible path does not break the operation`() {
        val blocker = Files.createTempDirectory("req-num-blocked").resolve("data")
        Files.writeString(blocker, "это файл, а не каталог\n")

        OfdReqNumCache(blocker.resolve("ofd-req-num.txt")).write(7L)
    }
}
