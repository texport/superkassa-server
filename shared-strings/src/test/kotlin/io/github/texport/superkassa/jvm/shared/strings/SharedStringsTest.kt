package io.github.texport.superkassa.jvm.shared.strings

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.TrilingualString
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryTemplateKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.TimeDebugKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.TimeErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.WebErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import kotlin.test.*

class SharedStringsTest {

    private val resolver = DefaultErrorResolver()

    @Test
    fun testTrilingualStringFormatting() {
        val string = TrilingualString("Hello {0}", "Привет {0}", "Сәлем {0}")
        assertEquals("Hello world", string.formatArgs("world").format("en"))
        assertEquals("Привет мир", string.formatArgs("мир").format("ru"))
        assertEquals("Сәлем әлем", string.formatArgs("әлем").format("kk"))
        assertEquals("Привет мир", string.formatArgs("мир").format("FR")) // Fallback to Russian
    }

    @Test
    fun testToStringOutput() {
        val string = TrilingualString("A", "B", "C")
        assertEquals("[EN] A / [RU] B / [KK] C", string.toString())
    }

    @Test
    fun testErrorResolverOutputs() {
        val sqliteMsg = resolver.resolve(SettingsErrorKey.SQLITE_NOT_ALLOWED)
        assertNotNull(sqliteMsg)
        assertTrue(sqliteMsg.en.contains("SQLite"))

        val unknownKey = object : ErrorKey {
            override val code = "UNKNOWN"
        }
        val defaultMsg = resolver.resolve(unknownKey)
        assertEquals("Unknown error", defaultMsg.en)
    }

    @Test
    fun testSettingsErrorKeyEnum() {
        assertNotNull(SettingsErrorKey.values())
        for (key in SettingsErrorKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, SettingsErrorKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }

    @Test
    fun testTimeErrorKeyEnum() {
        assertNotNull(TimeErrorKey.values())
        for (key in TimeErrorKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, TimeErrorKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }

    @Test
    fun testDeliveryErrorKeyEnum() {
        assertNotNull(DeliveryErrorKey.values())
        for (key in DeliveryErrorKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, DeliveryErrorKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }

    @Test
    fun testStorageErrorKeyEnum() {
        assertNotNull(StorageErrorKey.values())
        for (key in StorageErrorKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, StorageErrorKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }

    @Test
    fun testTimeDebugKeyEnum() {
        assertNotNull(TimeDebugKey.values())
        for (key in TimeDebugKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, TimeDebugKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }

    @Test
    fun testDeliveryTemplateKeyEnum() {
        assertNotNull(DeliveryTemplateKey.values())
        for (key in DeliveryTemplateKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, DeliveryTemplateKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }

    @Test
    fun testWebErrorKeyEnum() {
        assertNotNull(WebErrorKey.values())
        for (key in WebErrorKey.entries) {
            val resolved = resolver.resolve(key)
            assertNotEquals("Unknown error", resolved.en)
            assertEquals(key, WebErrorKey.valueOf(key.name))
            assertTrue(key.code.isNotEmpty())
        }
    }
}
