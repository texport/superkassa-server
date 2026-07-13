package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.QrCodeGeneratorPort
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import io.github.texport.superkassa.delivery.api.DeliveryServiceApi
import io.github.texport.superkassa.delivery.api.createDeliveryServiceApi
import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import io.github.texport.superkassa.jvm.delivery.impl.EmailDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.PrintDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.SmsDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.TelegramDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.WhatsAppDeliveryAdapter
import io.github.texport.superkassa.jvm.receipt.impl.DocumentConvertAdapter
import io.github.texport.superkassa.jvm.receipt.impl.QrCodeDataUriGenerator
import io.github.texport.superkassa.jvm.settings.impl.FileCoreSettingsRepository
import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto
import io.github.texport.superkassa.jvm.storage.impl.adapter.DatabaseCoreSettingsRepository
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.DefaultStorageConnectorRegistry
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.time.impl.SystemClock
import io.github.texport.superkassa.jvm.time.impl.SystemTimeGuard
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths
import io.github.texport.superkassa.delivery.api.port.DeliveryPort as KmpDeliveryPort

@Configuration
class AdaptersConfig {

    private val logger = LoggerFactory.getLogger(AdaptersConfig::class.java)

    @Value("\${app.log-language:ALL}")
    private lateinit var logLanguage: String

    @PostConstruct
    fun init() {
        System.setProperty("superkassa.log.language", logLanguage)
        logger.info("Установлен системный язык логирования: {}", logLanguage)
    }

    @Bean
    fun settingsRepository(
        @Value("\${spring.datasource.url:}") dbUrl: String,
        @Value("\${spring.datasource.username:}") dbUser: String?,
        @Value("\${spring.datasource.password:}") dbPass: String?
    ): CoreSettingsRepositoryPort {
        val urlLower = dbUrl.lowercase()
        return if (urlLower.startsWith("jdbc:postgresql:") || urlLower.startsWith("jdbc:mysql:")) {
            DatabaseCoreSettingsRepository(jdbcUrl = dbUrl, user = dbUser, password = dbPass)
        } else {
            FileCoreSettingsRepository(Paths.get("config/core-settings.json"))
        }
    }

    @Bean
    fun coreSettings(
        repository: CoreSettingsRepositoryPort,
        @Value("\${spring.datasource.url:}") dbUrl: String,
        @Value("\${spring.datasource.username:}") dbUser: String?,
        @Value("\${spring.datasource.password:}") dbPass: String?
    ): CoreSettings {
        val defaults = if (repository is DatabaseCoreSettingsRepository) {
            val engineType = if (dbUrl.lowercase().contains("mysql")) "MYSQL" else "POSTGRESQL"
            CoreSettings(
                mode = CoreMode.SERVER,
                storage = StorageSettings(
                    engine = engineType,
                    jdbcUrl = dbUrl,
                    user = dbUser,
                    password = dbPass
                ),
                allowChanges = true
            )
        } else {
            CoreSettings(
                mode = CoreMode.DESKTOP,
                storage = StorageSettings(
                    engine = "SQLITE",
                    jdbcUrl = dbUrl.ifEmpty { "jdbc:sqlite:data/core.db?busy_timeout=30000" }
                ),
                allowChanges = true
            )
        }
        return repository.loadOrCreate(defaults)
    }

    @Bean
    fun coreSettingsDto(coreSettings: CoreSettings): CoreSettingsDto {
        return coreSettings.toDto()
    }

    @Bean
    fun storageConfig(settings: CoreSettings): StorageConfig {
        return StorageConfig(
            settings.storage.jdbcUrl,
            null,
            settings.storage.user,
            settings.storage.password
        )
    }

    @Bean
    fun storageHealthChecker(): StorageHealthChecker {
        return StorageHealthChecker(DefaultStorageConnectorRegistry())
    }

    @Bean
    fun storagePort(config: StorageConfig): StoragePort {
        val storageBootstrap = DefaultStorageBootstrap()
        logger.info("Connecting to storage: ${config.jdbcUrl}")
        storageBootstrap.migrate(config)
        return StorageAdapter(storageBootstrap, config)
    }

    @Bean
    fun deliveryPort(settings: CoreSettings): DeliveryPort {
        val adapters = mutableListOf<KmpDeliveryPort>()
        val delivery = settings.delivery
        val channelsToUse = if (delivery != null && delivery.channels.isNotEmpty()) {
            delivery.channels.filter { it.enabled }.map { it.channel.uppercase() }
        } else {
            settings.deliveryChannels.map { it.uppercase() }
        }
        channelsToUse.distinct().forEach { channelName ->
            val adapter = createDeliveryAdapter(channelName, delivery)
            if (adapter != null) {
                adapters.add(adapter)
            }
        }
        if (adapters.isEmpty()) {
            adapters.add(stubAdapter(DeliveryChannel.PRINT))
        }
        return ServerDeliveryServiceAdapter(createDeliveryServiceApi(adapters))
    }

    private fun createDeliveryAdapter(
        channelName: String,
        delivery: io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings?
    ): KmpDeliveryPort? {
        val channel = runCatching { DeliveryChannel.valueOf(channelName) }
            .onFailure { logger.warn("Unknown delivery channel: $channelName", it) }
            .getOrNull() ?: return null

        return when (channel) {
            DeliveryChannel.PRINT -> createPrintAdapter(delivery?.print)
            DeliveryChannel.EMAIL -> createEmailAdapter(delivery?.email)
            DeliveryChannel.SMS -> createSmsAdapter(delivery?.sms)
            DeliveryChannel.TELEGRAM -> createTelegramAdapter(delivery?.telegram)
            DeliveryChannel.WHATSAPP -> createWhatsAppAdapter(delivery?.whatsapp)
        }
    }

    private fun createPrintAdapter(print: io.github.texport.superkassa.core.domain.api.model.settings.PrintDeliverySettings?): KmpDeliveryPort {
        val connection = print?.connection ?: return stubAdapter(DeliveryChannel.PRINT)
        val host = connection.host
        val port = connection.port
        if (host != null && port != null) {
            return PrintDeliveryAdapter(host, port)
        }
        return stubAdapter(DeliveryChannel.PRINT)
    }

    private fun createEmailAdapter(email: io.github.texport.superkassa.core.domain.api.model.settings.EmailProviderSettings?): KmpDeliveryPort {
        if (email == null) return stubAdapter(DeliveryChannel.EMAIL)
        return EmailDeliveryAdapter(
            email.host,
            email.port,
            email.user ?: "",
            email.password ?: "",
            email.from
        )
    }

    private fun createSmsAdapter(sms: io.github.texport.superkassa.core.domain.api.model.settings.SmsProviderSettings?): KmpDeliveryPort {
        val url = sms?.providerUrl ?: return stubAdapter(DeliveryChannel.SMS)
        return SmsDeliveryAdapter(url, sms.apiKey)
    }

    private fun createTelegramAdapter(tg: io.github.texport.superkassa.core.domain.api.model.settings.TelegramProviderSettings?): KmpDeliveryPort {
        val token = tg?.botToken ?: return stubAdapter(DeliveryChannel.TELEGRAM)
        return TelegramDeliveryAdapter(token)
    }

    private fun createWhatsAppAdapter(wa: io.github.texport.superkassa.core.domain.api.model.settings.WhatsAppProviderSettings?): KmpDeliveryPort {
        val token = wa?.accessToken ?: return stubAdapter(DeliveryChannel.WHATSAPP)
        val phoneId = wa.phoneNumberId ?: return stubAdapter(DeliveryChannel.WHATSAPP)
        return WhatsAppDeliveryAdapter(token, phoneId)
    }

    private fun stubAdapter(channel: DeliveryChannel): KmpDeliveryPort = object : KmpDeliveryPort {
        override val channel: DeliveryChannel = channel
        override fun send(request: DeliveryRequest): DeliveryResult {
            logger.debug("Delivery stub for channel: {}", channel)
            return DeliveryResult(true)
        }
    }

    @Bean
    fun qrCodeGeneratorPort(): QrCodeGeneratorPort = QrCodeDataUriGenerator

    @Bean
    fun documentConvertPort(): DocumentConvertPort = DocumentConvertAdapter()

    @Bean
    fun timeValidatorPort(): TimeValidatorPort = SystemTimeGuard

    @Bean
    fun clockPort(): ClockPort = SystemClock
}

class ServerDeliveryServiceAdapter(
    private val deliveryService: DeliveryServiceApi
) : DeliveryPort {
    private val logger = LoggerFactory.getLogger(ServerDeliveryServiceAdapter::class.java)

    @Suppress("TooGenericExceptionCaught")
    override fun deliver(request: io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest): Boolean {
        return try {
            val channel = try {
                DeliveryChannel.valueOf(request.channel.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown delivery channel: ${request.channel}, using PRINT", e)
                DeliveryChannel.PRINT
            }
            val result = deliveryService.deliver(
                DeliveryRequest(
                    cashboxId = request.kkmId,
                    documentId = request.documentId,
                    channel = channel,
                    destination = request.destination,
                    payloadUrl = request.payloadUrl,
                    payloadBytes = request.payloadBytes
                )
            )
            result.ok
        } catch (ex: Exception) {
            logger.error("Failed to deliver document: ${request.documentId} for KKM: ${request.kkmId}", ex)
            false
        }
    }
}
