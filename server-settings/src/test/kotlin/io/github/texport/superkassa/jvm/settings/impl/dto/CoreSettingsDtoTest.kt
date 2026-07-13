package io.github.texport.superkassa.jvm.settings.impl.dto

import io.github.texport.superkassa.jvm.settings.impl.mapper.*
import kotlinx.serialization.json.Json
import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliveryChannelSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.core.domain.api.model.settings.EmailProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.PrintConnectionSettings
import io.github.texport.superkassa.core.domain.api.model.settings.PrintDeliverySettings
import io.github.texport.superkassa.core.domain.api.model.settings.SmsProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.domain.api.model.settings.TelegramProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.WhatsAppProviderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreSettingsDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> testSerialization(instance: T) {
        val serializer = kotlinx.serialization.serializer<T>()
        val str = json.encodeToString<T>(serializer, instance)
        val decoded = json.decodeFromString<T>(serializer, str)
        assertEquals(instance, decoded)
    }

    private fun <T> testDataClass(instance1: T, instance2: T, copyOfInstance1: T) {
        assertEquals(instance1, instance1)
        assertEquals(instance1, copyOfInstance1)
        assertNotEquals(instance1, instance2)
        assertNotEquals<Any?>(instance1, null)
        assertNotEquals<Any?>(instance1, "string")
        assertEquals(instance1.hashCode(), copyOfInstance1.hashCode())
        assertNotNull(instance1.toString())
    }

    @Test
    fun testModeMapping() {
        assertEquals(CoreModeDto.DESKTOP, CoreMode.DESKTOP.toDto())
        assertEquals(CoreModeDto.SERVER, CoreMode.SERVER.toDto())
        assertEquals(CoreMode.DESKTOP, CoreModeDto.DESKTOP.toDomain())
        assertEquals(CoreMode.SERVER, CoreModeDto.SERVER.toDomain())
    }

    @Test
    fun testStorageSettingsDto() {
        val dto1 = StorageSettingsDto("engine1", "url1", "user1", "pass1")
        val dto2 = StorageSettingsDto("engine2", "url2", "user2", "pass2")
        val dtoCopy = dto1.copy(engine = "engine1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = StorageSettings("engine", "jdbcUrl", "user", "password")
        assertEquals(domain, domain.toDto().toDomain())

        // Exercise component getters
        assertEquals("engine1", dto1.component1())
        assertEquals("url1", dto1.component2())
        assertEquals("user1", dto1.component3())
        assertEquals("pass1", dto1.component4())
    }

    @Test
    fun testPrintConnectionSettingsDto() {
        val dto1 = PrintConnectionSettingsDto("type1", "host1", 1)
        val dto2 = PrintConnectionSettingsDto("type2", "host2", 2)
        val dtoCopy = dto1.copy(type = "type1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = PrintConnectionSettings("type", "host", 9100)
        assertEquals(domain, domain.toDto().toDomain())

        val (t, h, p) = dto1
        assertEquals("type1", t)
        assertEquals("host1", h)
        assertEquals(1, p)
    }

    @Test
    fun testPrintDeliverySettingsDto() {
        val conn1 = PrintConnectionSettingsDto("type1", "host1", 1)
        val conn2 = PrintConnectionSettingsDto("type2", "host2", 2)
        val dto1 = PrintDeliverySettingsDto(true, 80, conn1)
        val dto2 = PrintDeliverySettingsDto(false, 58, conn2)
        val dtoCopy = dto1.copy(enabled = true)

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = PrintDeliverySettings(true, 80, PrintConnectionSettings("type", "host", 9100))
        assertEquals(domain, domain.toDto().toDomain())

        val domainWithNull = PrintDeliverySettings(true, 80, null)
        assertEquals(domainWithNull, domainWithNull.toDto().toDomain())

        assertEquals(true, dto1.component1())
        assertEquals(80, dto1.component2())
        assertEquals(conn1, dto1.component3())
    }

    @Test
    fun testDeliveryChannelSettingsDto() {
        val dto1 = DeliveryChannelSettingsDto("ch1", true, "pay1", "fmt1", "dest1")
        val dto2 = DeliveryChannelSettingsDto("ch2", false, "pay2", "fmt2", "dest2")
        val dtoCopy = dto1.copy(channel = "ch1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = DeliveryChannelSettings("channel", true, "payload", "format", "dest")
        assertEquals(domain, domain.toDto().toDomain())

        assertEquals("ch1", dto1.component1())
        assertEquals(true, dto1.component2())
        assertEquals("pay1", dto1.component3())
        assertEquals("fmt1", dto1.component4())
        assertEquals("dest1", dto1.component5())
    }

    @Test
    fun testEmailProviderSettingsDto() {
        val dto1 = EmailProviderSettingsDto("host1", 1, "user1", "pass1", "from1")
        val dto2 = EmailProviderSettingsDto("host2", 2, "user2", "pass2", "from2")
        val dtoCopy = dto1.copy(host = "host1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = EmailProviderSettings("host", 587, "user", "pass", "from")
        assertEquals(domain, domain.toDto().toDomain())

        assertEquals("host1", dto1.component1())
        assertEquals(1, dto1.component2())
        assertEquals("user1", dto1.component3())
        assertEquals("pass1", dto1.component4())
        assertEquals("from1", dto1.component5())
    }

    @Test
    fun testSmsProviderSettingsDto() {
        val dto1 = SmsProviderSettingsDto("url1", "key1")
        val dto2 = SmsProviderSettingsDto("url2", "key2")
        val dtoCopy = dto1.copy(providerUrl = "url1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = SmsProviderSettings("url", "key")
        assertEquals(domain, domain.toDto().toDomain())

        val (u, k) = dto1
        assertEquals("url1", u)
        assertEquals("key1", k)
    }

    @Test
    fun testTelegramProviderSettingsDto() {
        val dto1 = TelegramProviderSettingsDto("token1")
        val dto2 = TelegramProviderSettingsDto("token2")
        val dtoCopy = dto1.copy(botToken = "token1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = TelegramProviderSettings("token")
        assertEquals(domain, domain.toDto().toDomain())

        val (t) = dto1
        assertEquals("token1", t)
    }

    @Test
    fun testWhatsAppProviderSettingsDto() {
        val dto1 = WhatsAppProviderSettingsDto("token1", "phone1")
        val dto2 = WhatsAppProviderSettingsDto("token2", "phone2")
        val dtoCopy = dto1.copy(accessToken = "token1")

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = WhatsAppProviderSettings("token", "phoneId")
        assertEquals(domain, domain.toDto().toDomain())

        val (t, p) = dto1
        assertEquals("token1", t)
        assertEquals("phone1", p)
    }

    @Test
    fun testDeliverySettingsDto() {
        val print1 = PrintDeliverySettingsDto(true, 80)
        val print2 = PrintDeliverySettingsDto(false, 58)
        val dto1 = DeliverySettingsDto(print1, emptyList(), null, null, null, null)
        val dto2 = DeliverySettingsDto(print2, emptyList(), null, null, null, null)
        val dtoCopy = dto1.copy(print = print1)

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = DeliverySettings(
            print = PrintDeliverySettings(true, 80, PrintConnectionSettings("type", "host", 9100)),
            channels = listOf(DeliveryChannelSettings("channel", true, "payload", "format", "dest")),
            email = EmailProviderSettings("host", 587, "user", "pass", "from"),
            sms = SmsProviderSettings("url", "key"),
            telegram = TelegramProviderSettings("token"),
            whatsapp = WhatsAppProviderSettings("token", "phoneId")
        )
        assertEquals(domain.print, domain.toDto().toDomain().print)
        assertEquals(domain.channels, domain.toDto().toDomain().channels)

        val domainWithNulls = DeliverySettings(
            print = null,
            channels = emptyList(),
            email = null,
            sms = null,
            telegram = null,
            whatsapp = null
        )
        val dtoWithNulls = domainWithNulls.toDto()
        assertEquals(domainWithNulls.print, dtoWithNulls.toDomain().print)
        assertEquals(domainWithNulls.email, dtoWithNulls.toDomain().email)
        assertEquals(domainWithNulls.sms, dtoWithNulls.toDomain().sms)
        assertEquals(domainWithNulls.telegram, dtoWithNulls.toDomain().telegram)
        assertEquals(domainWithNulls.whatsapp, dtoWithNulls.toDomain().whatsapp)

        val dtoWithNonNulls = DeliverySettingsDto(
            print = PrintDeliverySettingsDto(true, 80),
            channels = emptyList(),
            email = EmailProviderSettingsDto("host", 1),
            sms = SmsProviderSettingsDto("url", "key"),
            telegram = TelegramProviderSettingsDto("bot"),
            whatsapp = WhatsAppProviderSettingsDto("wa", "phone")
        )
        val domainFromDto = dtoWithNonNulls.toDomain()
        assertNotNull(domainFromDto.print)
        assertNotNull(domainFromDto.email)
        assertNotNull(domainFromDto.sms)
        assertNotNull(domainFromDto.telegram)
        assertNotNull(domainFromDto.whatsapp)

        assertEquals(print1, dto1.component1())
        assertTrue(dto1.component2().isEmpty())
    }

    @Test
    fun testCoreSettingsDto() {
        val storage = StorageSettingsDto("engine1", "url1")
        val dto1 = CoreSettingsDto(CoreModeDto.DESKTOP, storage, true)
        val dto2 = CoreSettingsDto(CoreModeDto.SERVER, storage, false)
        val dtoCopy = dto1.copy(allowChanges = true)

        testDataClass(dto1, dto2, dtoCopy)
        testSerialization(dto1)

        val domain = CoreSettings(
            mode = CoreMode.SERVER,
            storage = StorageSettings("engine", "jdbcUrl", "user", "password"),
            allowChanges = true,
            nodeId = "node-1",
            ofdProtocolVersion = "2.0.3",
            deliveryChannels = listOf("SMS", "EMAIL"),
            ofdTimeoutSeconds = 30,
            ofdReconnectIntervalSeconds = 60,
            delivery = DeliverySettings(
                print = PrintDeliverySettings(true, 80, PrintConnectionSettings("type", "host", 9100)),
                channels = listOf(DeliveryChannelSettings("channel", true, "payload", "format", "dest")),
                email = EmailProviderSettings("host", 587, "user", "pass", "from"),
                sms = SmsProviderSettings("url", "key"),
                telegram = TelegramProviderSettings("token"),
                whatsapp = WhatsAppProviderSettings("token", "phoneId")
            ),
            defaultAdminPin = "1111",
            defaultAdminName = "Admin",
            defaultCashierPin = "2222",
            defaultCashierName = "Cashier"
        )
        val mapped = domain.toDto().toDomain()
        assertEquals(domain.mode, mapped.mode)
        assertEquals(domain.storage, mapped.storage)
        assertEquals(domain.allowChanges, mapped.allowChanges)

        assertEquals(CoreModeDto.DESKTOP, dto1.component1())
        assertEquals(storage, dto1.component2())
        assertTrue(dto1.component3())
    }
}
