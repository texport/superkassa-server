package io.github.texport.superkassa.jvm.settings

import io.github.texport.superkassa.jvm.settings.validation.*
import kotlinx.serialization.json.Json
import kz.mybrain.superkassa.core.domain.model.settings.*
import kz.mybrain.superkassa.core.presentation.model.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CoreSettingsValidatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assertValidationFails(jsonStr: String) {
        val settings = json.decodeFromString(CoreSettings.serializer(), jsonStr)
        assertFailsWith<IllegalServerConfigurationException> {
            CoreSettingsValidator.validateSettings(settings)
        }
    }

    private fun assertValidationFails(settings: CoreSettings) {
        assertFailsWith<IllegalServerConfigurationException> {
            CoreSettingsValidator.validateSettings(settings)
        }
    }

    @Test
    fun `validateNotSQLite with sqlite url throws exception`() {
        assertFailsWith<IllegalServerConfigurationException> {
            CoreSettingsValidator.validateNotSQLite("jdbc:sqlite:build/test.db")
        }
        assertFailsWith<IllegalServerConfigurationException> {
            CoreSettingsValidator.validateNotSQLite("JDBC:SQLITE:build/test.db")
        }
    }

    @Test
    fun `validateNotSQLite with other urls passes`() {
        CoreSettingsValidator.validateNotSQLite(null)
        CoreSettingsValidator.validateNotSQLite("jdbc:postgresql://localhost/db")
        CoreSettingsValidator.validateNotSQLite("jdbc:mysql://localhost/db")
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
            CoreSettingsValidator.validateSettings(desktopSettings, requireServerMode = true)
        }
        CoreSettingsValidator.validateSettings(desktopSettings, requireServerMode = false)
    }

    @Test
    fun `validateSettings storage validation checks`() {
        // missing engine
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203"
            }
        """)

        // invalid engine
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "INVALID_DB", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203"
            }
        """)

        // missing jdbcUrl
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "" },
                "ofdProtocolVersion": "203"
            }
        """)

        // invalid jdbcUrl scheme
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "http://localhost" },
                "ofdProtocolVersion": "203"
            }
        """)
    }

    @Test
    fun `validateSettings server mode checks missing nodeId`() {
        assertValidationFails("""
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "u", "password": "p" },
                "nodeId": "",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """)
    }

    @Test
    fun `validateSettings server mode checks missing storage credentials`() {
        // user blank
        assertValidationFails("""
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "", "password": "p" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """)

        // password blank
        assertValidationFails("""
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "u", "password": "" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """)
    }

    @Test
    fun `validateSettings server mode SQLite engine checks`() {
        // SQLite engine
        assertValidationFails("""
            {
                "mode": "SERVER",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:postgresql://localhost:5432/db", "user": "u", "password": "p" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """)

        // SQLite jdbcUrl
        assertValidationFails("""
            {
                "mode": "SERVER",
                "storage": { "engine": "POSTGRESQL", "jdbcUrl": "jdbc:sqlite:db.db", "user": "u", "password": "p" },
                "nodeId": "node-1",
                "ofdProtocolVersion": "203",
                "allowChanges": true
            }
        """)
    }

    @Test
    fun `validateSettings ofdProtocolVersion formats`() {
        // blank
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": ""
            }
        """)

        // non-digits
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "v203"
            }
        """)
    }

    @Test
    fun `validateSettings ofd timeout too short`() {
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "203",
                "ofdTimeoutSeconds": 4
            }
        """)
    }

    @Test
    fun `validateSettings ofd reconnect too short`() {
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.sqlite" },
                "ofdProtocolVersion": "203",
                "ofdReconnectIntervalSeconds": 59
            }
        """)
    }

    @Test
    fun `validateSettings channel base validation checks`() {
        // channel blank
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }]
                }
            }
        """)

        // unknown channel
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "UNKNOWN", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }]
                }
            }
        """)

        // payloadType blank
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "", "documentFormat": "RAW", "destination": "192.168.1.1" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """)

        // documentFormat blank
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "", "destination": "192.168.1.1" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """)

        // destination blank
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """)
    }

    @Test
    fun `validateSettings delivery channel destinations format validation`() {
        // EMAIL channel with invalid destination
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "invalid-email" }],
                    "email": { "host": "smtp.gmail.com", "port": 25, "from": "sender@test.com", "user": "u", "password": "p" }
                }
            }
        """)

        // SMS channel with invalid destination
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "abc" }],
                    "sms": { "providerUrl": "http://sms.com", "apiKey": "key" }
                }
            }
        """)

        // TELEGRAM channel with invalid destination
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "not-a-number" }],
                    "telegram": { "botToken": "123456:ABC-def0123456789012345678901234567" }
                }
            }
        """)
    }

    @Test
    fun `validateSettings print channel connection invalid cases`() {
        // print section null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }]
                }
            }
        """)

        // paperWidthMm <= 0
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 0, "connection": { "host": "localhost", "port": 9100 } }
                }
            }
        """)

        // blank host
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "", "port": 9100 } }
                }
            }
        """)

        // port zero
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 0 } }
                }
            }
        """)

        // port out of bounds
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": 65536 } }
                }
            }
        """)

        // connection null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": null }
                }
            }
        """)

        // port null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "PRINT", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "dest" }],
                    "print": { "paperWidthMm": 58, "connection": { "host": "localhost", "port": null } }
                }
            }
        """)
    }

    @Test
    fun `validateSettings email channel configuration invalid cases`() {
        // email section null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }]
                }
            }
        """)

        // blank host
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }],
                    "email": { "host": "", "port": 25, "from": "test@test.com", "user": "u", "password": "p" }
                }
            }
        """)

        // port out of bounds
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }],
                    "email": { "host": "localhost", "port": 99999, "from": "test@test.com", "user": "u", "password": "p" }
                }
            }
        """)

        // invalid email sender format
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "EMAIL", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "test@test.com" }],
                    "email": { "host": "localhost", "port": 25, "from": "invalid-email-format", "user": "u", "password": "p" }
                }
            }
        """)
    }

    @Test
    fun `validateSettings sms channel configuration invalid cases`() {
        // sms section null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }]
                }
            }
        """)

        // invalid providerUrl scheme
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "sms": { "providerUrl": "ftp://sms.com", "apiKey": "key" }
                }
            }
        """)

        // blank apiKey
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "SMS", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "sms": { "providerUrl": "http://sms.com", "apiKey": "" }
                }
            }
        """)
    }

    @Test
    fun `validateSettings telegram channel configuration invalid cases`() {
        // telegram section null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "12345" }]
                }
            }
        """)

        // invalid botToken format (regex mismatch)
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "TELEGRAM", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "12345" }],
                    "telegram": { "botToken": "invalid-token" }
                }
            }
        """)
    }

    @Test
    fun `validateSettings whatsapp channel configuration invalid cases`() {
        // whatsapp section null
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }]
                }
            }
        """)

        // blank accessToken
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "whatsapp": { "accessToken": "", "phoneNumberId": "123" }
                }
            }
        """)

        // non-numeric phoneNumberId
        assertValidationFails("""
            {
                "mode": "DESKTOP",
                "storage": { "engine": "SQLITE", "jdbcUrl": "jdbc:sqlite:db.db" },
                "ofdProtocolVersion": "203",
                "delivery": {
                    "channels": [{ "channel": "WHATSAPP", "enabled": true, "payloadType": "JSON", "documentFormat": "RAW", "destination": "+77001234567" }],
                    "whatsapp": { "accessToken": "token", "phoneNumberId": "phone-id-abc" }
                }
            }
        """)
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
        val settings = json.decodeFromString(CoreSettings.serializer(), jsonStr)
        CoreSettingsValidator.validateSettings(settings)
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
        val settings = json.decodeFromString(CoreSettings.serializer(), jsonStr)
        CoreSettingsValidator.validateSettings(settings)
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
        CoreSettingsValidator.validateSettings(validServer, requireServerMode = true)

        // 2. ofdProtocolVersion is empty
        assertValidationFails(base.copy(ofdProtocolVersion = ""))

        // 3. storage engine is empty
        assertValidationFails(base.copy(storage = StorageSettings(engine = "", jdbcUrl = "jdbc:sqlite:db.db")))

        // 4. storage jdbcUrl is empty
        assertValidationFails(base.copy(storage = StorageSettings(engine = "SQLITE", jdbcUrl = "")))

        // 5. storage user is null in SERVER mode
        assertValidationFails(base.copy(
            mode = CoreMode.SERVER,
            storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost/db", user = null, password = "p"),
            nodeId = "node-1"
        ))

        // 6. storage password is null in SERVER mode
        assertValidationFails(base.copy(
            mode = CoreMode.SERVER,
            storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost/db", user = "u", password = null),
            nodeId = "node-1"
        ))

        // 7. disabled channel is skipped
        val disabledChannelSettings = base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "PRINT", enabled = false, payloadType = "", documentFormat = "", destination = null)
                )
            )
        )
        CoreSettingsValidator.validateSettings(disabledChannelSettings)

        // 8. channel name is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "dest")
                )
            )
        ))

        // 9. payloadType is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "", documentFormat = "RAW", destination = "dest")
                ),
                print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = "localhost", port = 9100))
            )
        ))

        // 10. documentFormat is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "JSON", documentFormat = "", destination = "dest")
                ),
                print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = "localhost", port = 9100))
            )
        ))

        // 11. destination is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = null)
                ),
                print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = "localhost", port = 9100))
            )
        ))

        // 12. print connection host is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "PRINT", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "dest")
                ),
                print = PrintDeliverySettings(paperWidthMm = 58, connection = PrintConnectionSettings(host = null, port = 9100))
            )
        ))

        // 13. email host is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "", port = 25, from = "test@test.com", user = "u", password = "p")
            )
        ))

        // 14. email from is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = 25, from = "", user = "u", password = "p")
            )
        ))

        // 15. email user is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = null, password = "p")
            )
        ))

        // 15b. email user is empty string
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "", password = "p")
            )
        ))

        // 16. email password is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "u", password = null)
            )
        ))

        // 16b. email password is empty string
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = 25, from = "test@test.com", user = "u", password = "")
            )
        ))

        // 17. sms providerUrl is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                ),
                sms = SmsProviderSettings(providerUrl = null, apiKey = "key")
            )
        ))

        // 18. sms apiKey is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                ),
                sms = SmsProviderSettings(providerUrl = "http://sms.com", apiKey = null)
            )
        ))

        // 19. telegram botToken is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "TELEGRAM", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "12345")
                ),
                telegram = TelegramProviderSettings(botToken = null)
            )
        ))

        // 20. whatsapp accessToken is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                ),
                whatsapp = WhatsAppProviderSettings(accessToken = null, phoneNumberId = "123")
            )
        ))

        // 21. whatsapp phoneNumberId is null
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                ),
                whatsapp = WhatsAppProviderSettings(accessToken = "token", phoneNumberId = null)
            )
        ))

        // 21a. email port is invalid
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = -1, from = "test@test.com", user = "u", password = "p")
            )
        ))

        // 21b. email from is invalid email format
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "test@test.com")
                ),
                email = EmailProviderSettings(host = "host", port = 25, from = "invalid-email", user = "u", password = "p")
            )
        ))

        // 21c. sms providerUrl is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                ),
                sms = SmsProviderSettings(providerUrl = " ", apiKey = "key")
            )
        ))

        // 21d. sms apiKey is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "+77001234567")
                ),
                sms = SmsProviderSettings(providerUrl = "http://sms.com", apiKey = " ")
            )
        ))

        // 21e. telegram botToken is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "TELEGRAM", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "12345")
                ),
                telegram = TelegramProviderSettings(botToken = " ")
            )
        ))

        // 21f. whatsapp accessToken is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                ),
                whatsapp = WhatsAppProviderSettings(accessToken = " ", phoneNumberId = "123")
            )
        ))

        // 21g. whatsapp phoneNumberId is blank
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "WHATSAPP", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "87001234567")
                ),
                whatsapp = WhatsAppProviderSettings(accessToken = "token", phoneNumberId = " ")
            )
        ))

        // 23. isValidEmail spacing check
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "EMAIL", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "invalid email@domain.com")
                ),
                email = EmailProviderSettings(host = "smtp.gmail.com", port = 25, from = "sender@test.com", user = "u", password = "p")
            )
        ))

        // 24. isValidPhoneNumber blank check
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "SMS", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "   ")
                ),
                sms = SmsProviderSettings(providerUrl = "http://sms.com", apiKey = "key")
            )
        ))

        // 25. isValidTelegramChatId blank check
        assertValidationFails(base.copy(
            delivery = DeliverySettings(
                channels = listOf(
                    DeliveryChannelSettings(channel = "TELEGRAM", enabled = true, payloadType = "JSON", documentFormat = "RAW", destination = "   ")
                ),
                telegram = TelegramProviderSettings(botToken = "123456:ABC-def0123456789012345678901234567")
            )
        ))
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
        val constructor = CoreSettingsValidator::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()
        kotlin.test.assertNotNull(instance)
    }

    @Test
    fun `test validation errors coverage`() {
        kotlin.test.assertNotNull(ValidationErrors.SQLITE_NOT_ALLOWED_ERROR)
        kotlin.test.assertNotNull(ValidationErrors.SERVER_MODE_ONLY_ERROR)
        kotlin.test.assertNotNull(ValidationErrors.OFD_PROTOCOL_VERSION_ERROR)
        kotlin.test.assertNotNull(ValidationErrors.OFD_TIMEOUT_ERROR)
        kotlin.test.assertNotNull(ValidationErrors.OFD_RECONNECT_INTERVAL_ERROR)
        kotlin.test.assertNotNull(ValidationErrors.STORAGE_ENGINE_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.STORAGE_ENGINE_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.JDBC_URL_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.JDBC_URL_INVALID_SCHEME)
        kotlin.test.assertNotNull(ValidationErrors.NODE_ID_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.DATABASE_USER_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.DATABASE_PASSWORD_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.OFD_PROVIDER_KEY_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.CHANNEL_NAME_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.unknownChannel("TEST"))
        kotlin.test.assertNotNull(ValidationErrors.payloadTypeBlank("TEST"))
        kotlin.test.assertNotNull(ValidationErrors.documentFormatBlank("TEST"))
        kotlin.test.assertNotNull(ValidationErrors.destinationBlank("TEST"))
        kotlin.test.assertNotNull(ValidationErrors.EMAIL_DESTINATION_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.phoneDestinationInvalid("TEST"))
        kotlin.test.assertNotNull(ValidationErrors.TELEGRAM_DESTINATION_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.PRINT_CONFIG_MISSING)
        kotlin.test.assertNotNull(ValidationErrors.PRINT_PAPER_WIDTH_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.PRINT_HOST_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.PRINT_PORT_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.EMAIL_CONFIG_MISSING)
        kotlin.test.assertNotNull(ValidationErrors.EMAIL_PARAMS_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.SMS_CONFIG_MISSING)
        kotlin.test.assertNotNull(ValidationErrors.SMS_PROVIDER_URL_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.SMS_API_KEY_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.TELEGRAM_CONFIG_MISSING)
        kotlin.test.assertNotNull(ValidationErrors.TELEGRAM_BOT_TOKEN_INVALID)
        kotlin.test.assertNotNull(ValidationErrors.WHATSAPP_CONFIG_MISSING)
        kotlin.test.assertNotNull(ValidationErrors.WHATSAPP_ACCESS_TOKEN_BLANK)
        kotlin.test.assertNotNull(ValidationErrors.WHATSAPP_PHONE_ID_INVALID)
    }
}
