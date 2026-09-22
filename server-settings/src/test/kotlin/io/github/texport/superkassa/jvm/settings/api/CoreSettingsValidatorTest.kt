package io.github.texport.superkassa.jvm.settings.api

import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDomain
import io.github.texport.superkassa.jvm.settings.impl.validation.*
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
import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val ASCII_LIMIT = 128

class CoreSettingsValidatorTest {

    private val validator: CoreSettingsValidator = io.github.texport.superkassa.jvm.settings.impl.DefaultCoreSettingsValidator()

    private val json = Json { ignoreUnknownKeys = true }

    private fun assertValidationFails(jsonStr: String) {
        val settings = json.decodeFromString(CoreSettingsDto.serializer(), jsonStr).toDomain()
        assertFailsWith<IllegalServerConfigurationException> {
            validator.validateSettings(settings)
        }
    }

    private fun assertValidationFails(settings: CoreSettings) {
        assertFailsWith<IllegalServerConfigurationException> {
            validator.validateSettings(settings)
        }
    }

    @Test
    fun `validateNotSQLite with sqlite url throws exception`() {
        assertFailsWith<IllegalServerConfigurationException> {
            validator.validateNotSQLite("jdbc:sqlite:build/test.db")
        }
        assertFailsWith<IllegalServerConfigurationException> {
            validator.validateNotSQLite("JDBC:SQLITE:build/test.db")
        }
    }

    @Test
    fun `validateNotSQLite with other urls passes`() {
        validator.validateNotSQLite(null)
        validator.validateNotSQLite("jdbc:postgresql://localhost/db")
        validator.validateNotSQLite("jdbc:mysql://localhost/db")
    }

    @Test
    fun `validateSettings server mode required checks`() {
        val desktopSettings = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:db.sqlite"),
            allowChanges = true,
            ofdProtocolVersion = "203"
        )
        assertFailsWith<IllegalServerConfigurationException> {
            validator.validateSettings(desktopSettings, requireServerMode = true)
        }
        validator.validateSettings(desktopSettings, requireServerMode = false)
    }

    @Test
    fun `validateSettings storage validation checks`() {
        // missing engine
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203"
            }
        """
        )

        // invalid engine
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "INVALID_DB", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203"
            }
        """
        )

        // missing jdbcUrl
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "" },
                "ofdProtocolVersion": "203"
            }
        """
        )

        // invalid jdbcUrl scheme
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "http://localhost" },
                "ofdProtocolVersion": "203"
            }
        """
        )
    }

    @Test
    fun `validateSettings server mode checks missing nodeId`() {
        assertValidationFails(
            """
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "u", "password": "p" },
                "nodeId": "",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """
        )
    }

    @Test
    fun `validateSettings server mode checks missing storage credentials`() {
        // user blank
        assertValidationFails(
            """
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "", "password": "p" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """
        )

        // password blank
        assertValidationFails(
            """
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "u", "password": "" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """
        )
    }

    @Test
    fun `validateSettings server mode SQLite engine checks`() {
        // SQLite engine
        assertValidationFails(
            """
            {
                "mode": "SERVER",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "u", "password": "p" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """
        )

        // SQLite jdbcUrl
        assertValidationFails(
            """
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:sqlite:db.db", "user": "u", "password": "p" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """
        )
    }

    @Test
    fun `validateSettings ofdProtocolVersion formats`() {
        // blank
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": ""
            }
        """
        )

        // non-digits
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "v203"
            }
        """
        )
    }

    @Test
    fun `validateSettings ofd timeout too short`() {
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "203",
                "ofdTimeoutSeconds": 4
            }
        """
        )
    }

    /**
     * Запись без срока ожидания читается ровно тем же умолчанием, какое
     * объявлено в ядре, и проходит проверку.
     *
     * Умолчание здесь одно на оба места. Пока их было два, одна и та же
     * запись означала у ядра тридцать секунд, а у узла семь: узел писал
     * при первом запуске одно значение, а читал потом другое.
     */
    @Test
    fun `settings record without response wait reads the core default`() {
        val settings = json.decodeFromString(
            CoreSettingsDto.serializer(),
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "203"
            }
        """
        ).toDomain()
        val coreDefaults = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:db.sqlite")
        )
        assertEquals(coreDefaults.ofdTimeoutSeconds, settings.ofdTimeoutSeconds)
        assertEquals(coreDefaults.ofdReconnectIntervalSeconds, settings.ofdReconnectIntervalSeconds)
        assertEquals(coreDefaults.deliveryChannels, settings.deliveryChannels)
        assertEquals(coreDefaults.nodeId, settings.nodeId)
        assertEquals(coreDefaults.defaultAdminPin, settings.defaultAdminPin)
        assertEquals(coreDefaults.defaultCashierPin, settings.defaultCashierPin)
        validator.validateSettings(settings)
    }

    /**
     * Имя канала вне перечисления не проходит проверку настроек.
     *
     * Верхнеуровневый список каналов не проверял никто: «OFD» доходил
     * до сборки узла, адаптеров по нему не собиралось, и доставка
     * отвечала успехом, ничего не доставив.
     */
    @Test
    fun `validateSettings unknown delivery channel name`() {
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "203",
                "deliveryChannels": ["OFD"]
            }
        """
        )
    }

    /** Отказ называет имена каналов, которые узел знает. */
    @Test
    fun `unknown delivery channel names the supported ones`() {
        val settings = json.decodeFromString(
            CoreSettingsDto.serializer(),
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "deliveryChannels": ["OFD"]
            }
        """
        ).toDomain()

        val failure = assertFailsWith<IllegalServerConfigurationException> {
            validator.validateSettings(settings)
        }
        val message = failure.message.orEmpty()
        for (channel in DeliveryChannel.entries) {
            assertTrue(message.contains(channel.name), "отказ не называет канал ${channel.name}")
        }
    }

    /** Известное имя в любом регистре проходит. */
    @Test
    fun `validateSettings accepts known delivery channel names`() {
        val settings = json.decodeFromString(
            CoreSettingsDto.serializer(),
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "deliveryChannels": ["print", "EMAIL"]
            }
        """
        ).toDomain()

        validator.validateSettings(settings)
    }

    @Test
    fun `validateSettings ofd reconnect too short`() {
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "203",
                "ofdReconnectIntervalSeconds": 59
            }
        """
        )
    }

    @Test
    fun `validateSettings channel base validation checks`() {
        // channel blank
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }]
                }
            }
        """
        )

        // unknown channel
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "UNKNOWN", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }]
                }
            }
        """
        )

        // payloadType blank
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "", "documentFormat": "RAW", "destination": "192.168.1.1" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """
        )

        // documentFormat blank
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "", "destination": "192.168.1.1" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """
        )

        // destination blank
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings delivery channel destinations format validation`() {
        // EMAIL channel with invalid destination
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "invalid-email" }],
                    "email": { "host": "smtp.gmail.com", "port": 25, "from": "sender@test.com", "user": "u", "password": "p" }
                }
            }
        """
        )

        // SMS channel with invalid destination
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "abc" }],
                    "sms": { "providerUrl": "http://sms.com", "apiKey": "key" }
                }
            }
        """
        )

        // TELEGRAM channel with invalid destination
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "not-a-number" }],
                    "telegram": { "botToken": "123456:ABC-def0123456789012345678901234567" }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings print channel connection invalid cases`() {
        // print section null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }]
                }
            }
        """
        )

        // paperWidthMm <= 0
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 0, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """
        )

        // blank host
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "", "port": 9100 } }
                }
            }
        """
        )

        // port zero
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 0 } }
                }
            }
        """
        )

        // port out of bounds
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 65536 } }
                }
            }
        """
        )

        // connection null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": null }
                }
            }
        """
        )

        // port null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": null } }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings email channel configuration invalid cases`() {
        // email section null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }]
                }
            }
        """
        )

        // blank host
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }],
                    "email": { "host": "", "port": 25, "from": "test@test.com", "user": "u", "password": "p" }
                }
            }
        """
        )

        // port out of bounds
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }],
                    "email": { "host": "localhost", "port": 99999, "from": "test@test.com", "user": "u", "password": "p" }
                }
            }
        """
        )

        // invalid email sender format
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }],
                    "email": { "host": "localhost", "port": 25, "from": "invalid-email-format", "user": "u", "password": "p" }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings sms channel configuration invalid cases`() {
        // sms section null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }]
                }
            }
        """
        )

        // invalid providerUrl scheme
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "sms": { "providerUrl": "ftp://sms.com", "apiKey": "key" }
                }
            }
        """
        )

        // blank apiKey
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "sms": { "providerUrl": "http://sms.com", "apiKey": "" }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings telegram channel configuration invalid cases`() {
        // telegram section null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "12345" }]
                }
            }
        """
        )

        // invalid botToken format (regex mismatch)
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "12345" }],
                    "telegram": { "botToken": "invalid-token" }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings whatsapp channel configuration invalid cases`() {
        // whatsapp section null
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }]
                }
            }
        """
        )

        // blank accessToken
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "whatsapp": { "accessToken": "", "phoneNumberId": "123" }
                }
            }
        """
        )

        // non-numeric phoneNumberId
        assertValidationFails(
            """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "whatsapp": { "accessToken": "token", "phoneNumberId": "phone-id-abc" }
                }
            }
        """
        )
    }

    @Test
    fun `validateSettings server mode valid settings`() {
        val jsonStr = """
            {
                "mode": "SERVER",
                "storage": {
                    "engine": "POSTGRESQL",
                    "jdbcUrl": "jdbc:postgresql://localhost:5432/db",
                    "user": "postgres",
                    "password": "password"
                },
                "nodeId": "node-123",
                "ofdProtocolVersion": "203",
                "allowChanges": true,
                "ofdTimeoutSeconds": 15,
                "ofdReconnectIntervalSeconds": 60
            }
        """.trimIndent()
        val settings = json.decodeFromString(CoreSettingsDto.serializer(), jsonStr).toDomain()
        validator.validateSettings(settings)
    }

    @Test
    fun `validateSettings with all channels enabled and valid`() {
        val jsonStr = """
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [
                        { "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "192.168.1.1" },
                        { "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "receiver@test.com" },
                        { "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" },
                        { "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "-1001234567" },
                        { "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "87001234567" }
                    ],
                    "print": { "paperWidthMm": 58, "connection": { "host": "192.168.1.100", "port": 9100 } },
                    "email": { "host": "smtp.gmail.com", "port": 587, "from": "no-reply@test.com", "user": "user", "password": "password" },
                    "sms": { "providerUrl": "https://api.sms-provider.com", "apiKey": "secret-key" },
                    "telegram": { "botToken": "123456:ABC-def0123456789012345678901234567" },
                    "whatsapp": { "accessToken": "wa-token", "phoneNumberId": "12345678" }
                }
            }
        """.trimIndent()
        val settings = json.decodeFromString(CoreSettingsDto.serializer(), jsonStr).toDomain()
        validator.validateSettings(settings)
    }

    @Test
    fun `validateSettings additional branch coverage`() {
        val base = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:db.db"),
            ofdProtocolVersion = "203"
        )

        // 1. requireServerMode = true with valid SERVER configuration
        val validServer = base.copy(
            mode = CoreMode.SERVER,
            storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost/db", user = "u", password = "p"),
            nodeId = "node-1"
        )
        validator.validateSettings(validServer, requireServerMode = true)

        // 2. ofdProtocolVersion is empty
        assertValidationFails(base.copy(ofdProtocolVersion = ""))

        // 3. storage engine is empty
        assertValidationFails(base.copy(storage = StorageSettings(engine = "", jdbcUrl = "jdbc:sqlite:db.db")))

        // 4. storage jdbcUrl is empty
        assertValidationFails(base.copy(storage = StorageSettings(engine = "SQLITE", jdbcUrl = "")))

        // 5. storage user is null in SERVER mode
        assertValidationFails(
            base.copy(
                mode = CoreMode.SERVER,
                storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost/db", user = null, password = "p"),
                nodeId = "node-1"
            )
        )

        // 6. storage password is null in SERVER mode
        assertValidationFails(
            base.copy(
                mode = CoreMode.SERVER,
                storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost/db", user = "u", password = null),
                nodeId = "node-1"
            )
        )

        // 7. disabled channel is skipped
        val disabledChannelSettings = base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "PRINT", enabled = false, payloadType = "", documentFormat = "", destination = null)
                )
            )
        )
        validator.validateSettings(disabledChannelSettings)

        // 8. channel name is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "dest")
                    )
                )
            )
        )

        // 9. payloadType is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "", documentFormat = "RAW", destination = "dest")
                    ),
                    print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = "localhost", port = 9100))
                )
            )
        )

        // 10. documentFormat is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "JSON", documentFormat = "", destination = "dest")
                    ),
                    print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = "localhost", port = 9100))
                )
            )
        )

        // 11. destination is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = null)
                    ),
                    print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = "localhost", port = 9100))
                )
            )
        )

        // 12. print connection host is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "dest")
                    ),
                    print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = null, port = 9100))
                )
            )
        )

        // 13. email host is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "", port = 25, from = "test@test.com", user = "u", password = "p")
                )
            )
        )

        // 14. email from is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = 25, from = "", user = "u", password = "p")
                )
            )
        )

        // 15. email user is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = null, password = "p")
                )
            )
        )

        // 15b. email user is empty string
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "", password = "p")
                )
            )
        )

        // 16. email password is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "u", password = null)
                )
            )
        )

        // 16b. email password is empty string
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "u", password = "")
                )
            )
        )

        // 17. sms providerUrl is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                    ),
                    sms = SmsProviderSettings(providerUrl = null, apiKey = "key")
                )
            )
        )

        // 18. sms apiKey is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                    ),
                    sms = SmsProviderSettings(providerUrl = "http://sms.com", apiKey = null)
                )
            )
        )

        // 19. telegram botToken is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "TELEGRAM", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "12345")
                    ),
                    telegram = TelegramProviderSettings(botToken = null)
                )
            )
        )

        // 20. whatsapp accessToken is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                    ),
                    whatsapp = WhatsAppProviderSettings(accessToken = null, phoneNumberId = "123")
                )
            )
        )

        // 21. whatsapp phoneNumberId is null
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                    ),
                    whatsapp = WhatsAppProviderSettings(accessToken = "token", phoneNumberId = null)
                )
            )
        )

        // 21a. email port is invalid
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = -1, from = "test@test.com", user = "u", password = "p")
                )
            )
        )

        // 21b. email from is invalid email format
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                    ),
                    email = EmailProviderSettings(host = "host", port = 25, from = "invalid-email", user = "u", password = "p")
                )
            )
        )

        // 21c. sms providerUrl is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                    ),
                    sms = SmsProviderSettings(providerUrl = " ", apiKey = "key")
                )
            )
        )

        // 21d. sms apiKey is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                    ),
                    sms = SmsProviderSettings(providerUrl = "http://sms.com", apiKey = " ")
                )
            )
        )

        // 21e. telegram botToken is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "TELEGRAM", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "12345")
                    ),
                    telegram = TelegramProviderSettings(botToken = " ")
                )
            )
        )

        // 21f. whatsapp accessToken is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                    ),
                    whatsapp = WhatsAppProviderSettings(accessToken = " ", phoneNumberId = "123")
                )
            )
        )

        // 21g. whatsapp phoneNumberId is blank
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                    ),
                    whatsapp = WhatsAppProviderSettings(accessToken = "token", phoneNumberId = " ")
                )
            )
        )

        // 23. isValidEmail spacing check
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "invalid email@domain.com")
                    ),
                    email = EmailProviderSettings(host = "smtp.gmail.com", port = 25, from = "sender@test.com", user = "u", password = "p")
                )
            )
        )

        // 24. isValidPhoneNumber blank check
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "   ")
                    ),
                    sms = SmsProviderSettings(providerUrl = "http://sms.com", apiKey = "key")
                )
            )
        )

        // 25. isValidTelegramChatId blank check
        assertValidationFails(
            base.copy(
                delivery = DeliverySettings(
                    channels = listOf(
                        DeliveryChannelSettings(channel = "TELEGRAM", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "   ")
                    ),
                    telegram = TelegramProviderSettings(botToken = "123456:ABC-def0123456789012345678901234567")
                )
            )
        )
    }

    @Test
    fun `test ValidationUtils helper methods edge cases`() {
        // isValidEmail
        kotlin.test.assertTrue(isValidEmail("test@example.com"))
        kotlin.test.assertFalse(isValidEmail("test@example")) // no dot
        kotlin.test.assertFalse(isValidEmail("test.com")) // no at
        kotlin.test.assertFalse(isValidEmail("test @example.com")) // space

        // isValidPort
        kotlin.test.assertTrue(isValidPort(80))
        kotlin.test.assertFalse(isValidPort(null))
        kotlin.test.assertFalse(isValidPort(0))
        kotlin.test.assertFalse(isValidPort(65536))

        // isDigitsOnly
        kotlin.test.assertTrue(isDigitsOnly("123"))
        kotlin.test.assertFalse(isDigitsOnly(""))
        kotlin.test.assertFalse(isDigitsOnly("123a"))

        // isValidPhoneNumber
        kotlin.test.assertTrue(isValidPhoneNumber("+1234"))
        kotlin.test.assertTrue(isValidPhoneNumber("1234"))
        kotlin.test.assertFalse(isValidPhoneNumber("+")) // only plus
        kotlin.test.assertFalse(isValidPhoneNumber("")) // empty
        kotlin.test.assertFalse(isValidPhoneNumber("123a")) // non-digit

        // isValidTelegramChatId
        kotlin.test.assertTrue(isValidTelegramChatId("-1234"))
        kotlin.test.assertTrue(isValidTelegramChatId("1234"))
        kotlin.test.assertFalse(isValidTelegramChatId("-")) // only minus
        kotlin.test.assertFalse(isValidTelegramChatId("")) // empty
    }

    @Test
    fun `test object constructors for coverage`() {
        // Initialize objects to trigger class loading and constructor coverage
        kotlin.test.assertNotNull(StorageValidator)
        kotlin.test.assertNotNull(DeliveryValidator)
        kotlin.test.assertNotNull(PrintValidator)
        kotlin.test.assertNotNull(EmailValidator)
        kotlin.test.assertNotNull(SmsValidator)
        kotlin.test.assertNotNull(TelegramValidator)
        kotlin.test.assertNotNull(WhatsAppValidator)
    }

    @Test
    fun `cover constructor`() {
        val instance = io.github.texport.superkassa.jvm.settings.impl.DefaultCoreSettingsValidator()
        kotlin.test.assertNotNull(instance)
    }

    /**
     * Одинаковые умолчания пинов не попадают в записываемые настройки.
     *
     * Пин уникален в пределах кассы: со совпадающими умолчаниями второй
     * пользователь новой кассы не заводится, и владелец узнавал об этом
     * по кассе с одним пользователем вместо двух.
     */
    @Test
    fun `identical default pins are refused when settings are stored`() {
        val failure = assertFailsWith<IllegalServerConfigurationException> {
            validator.validateSettingsToStore(settingsWithDefaultPins(adminPin = "4821", cashierPin = "4821"))
        }

        assertEquals(
            DefaultErrorResolver().resolve(SettingsErrorKey.DEFAULT_PINS_IDENTICAL).toString(),
            failure.message
        )
        assertFalse(failure.message.orEmpty().contains("4821"), "в отказе виден пин")
    }

    @Test
    fun `different default pins are stored`() {
        validator.validateSettingsToStore(settingsWithDefaultPins(adminPin = "4821", cashierPin = "5930"))
    }

    /**
     * Уже сохранённый файл с одинаковыми умолчаниями узел принимает.
     *
     * Умолчания мертвы: ронять из-за них запуск — останавливать кассу
     * из-за настройки, которую никто не читает. Замечание уходит в журнал.
     */
    @Test
    fun `existing settings with identical default pins still load`() {
        val settings = settingsWithDefaultPins(adminPin = "4821", cashierPin = "4821")

        validator.validateSettings(settings)
        validator.reviewStoredSettings(settings)
    }

    @Test
    fun `review of settings without the fault says nothing`() {
        validator.reviewStoredSettings(settingsWithDefaultPins(adminPin = "4821", cashierPin = "5930"))
    }

    /** Замечание в журнале — на английском, называет настройки и молчит о значениях. */
    @Test
    fun `the logged remark names both settings and no value`() {
        assertTrue(IDENTICAL_DEFAULT_PINS_WARNING.contains("defaultAdminPin"))
        assertTrue(IDENTICAL_DEFAULT_PINS_WARNING.contains("defaultCashierPin"))
        assertFalse(IDENTICAL_DEFAULT_PINS_WARNING.any { it.isDigit() }, IDENTICAL_DEFAULT_PINS_WARNING)
        assertTrue(IDENTICAL_DEFAULT_PINS_WARNING.all { it.code < ASCII_LIMIT }, IDENTICAL_DEFAULT_PINS_WARNING)
    }

    /** Умолчания самого кода тоже обязаны различаться. */
    @Test
    fun `code defaults for administrator and cashier pins differ`() {
        val defaults = settingsWithDefaultPins()

        assertNotEquals(defaults.defaultAdminPin, defaults.defaultCashierPin)
    }

    private fun settingsWithDefaultPins(adminPin: String? = null, cashierPin: String? = null): CoreSettings {
        val defaults = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:db.sqlite")
        )
        return defaults.copy(
            defaultAdminPin = adminPin ?: defaults.defaultAdminPin,
            defaultCashierPin = cashierPin ?: defaults.defaultCashierPin
        )
    }
}
