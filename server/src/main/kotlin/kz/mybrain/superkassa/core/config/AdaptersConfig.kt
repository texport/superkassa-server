package kz.mybrain.superkassa.core.config

import kz.mybrain.superkassa.core.data.adapter.DatabaseCoreSettingsRepository
import io.github.texport.superkassa.jvm.settings.FileCoreSettingsRepository
import kz.mybrain.network.OfdTcpNetworkClient
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.model.settings.StorageSettings
import kz.mybrain.superkassa.core.domain.port.CoreSettingsRepositoryPort
import kz.mybrain.superkassa.core.domain.usecase.shift.RecalculateShiftCountersUseCase
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.CloseShiftRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.DeliveryServiceAdapter
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.MoneyPlacementRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.OfdConfigAdapter
import kz.mybrain.superkassa.core.data.adapter.OfdManagerAdapter
import kz.mybrain.superkassa.core.data.adapter.OfflineQueueAdapter
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.ReportRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.ResilienceOfdManagerAdapter
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.ServiceRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedLeaseLockAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedQueueStorageAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageAdapter
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.TicketRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.ofd.OfdProtocolCodec
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.domain.port.*
import io.github.texport.superkassa.delivery.application.service.DeliveryService
import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import kz.mybrain.superkassa.storage.application.health.StorageHealthChecker
import kz.mybrain.superkassa.storage.data.bootstrap.DefaultStorageBootstrap
import kz.mybrain.superkassa.storage.data.jdbc.DefaultStorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths

@Configuration
class AdaptersConfig {

    private val logger = LoggerFactory.getLogger(AdaptersConfig::class.java)

    @Bean
    @Suppress("CanConvertToMultiDollarString")
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
    @Suppress("CanConvertToMultiDollarString")
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
    fun queueStoragePort(storagePort: StoragePort): QueueStoragePort {
        return StorageBackedQueueStorageAdapter(storagePort)
    }

    @Bean
    fun queueLeaseLockPort(storagePort: StoragePort): LeaseLockPort {
        return StorageBackedLeaseLockAdapter(storagePort)
    }

    @Bean
    fun queuePort(
        settings: CoreSettings,
        queueStoragePort: QueueStoragePort,
        queueLeaseLockPort: LeaseLockPort,
        queueCommandHandler: QueueCommandHandler
    ): OfflineQueuePort {
        val ownerId = settings.nodeId
        return OfflineQueueAdapter(queueStoragePort, queueLeaseLockPort, queueCommandHandler, ownerId = ownerId)
    }

    @Bean
    fun deliveryPort(settings: CoreSettings): DeliveryPort {
        val adapters = mutableListOf<DeliveryAdapter>()
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
        return DeliveryServiceAdapter(DeliveryService(adapters))
    }

    private fun createDeliveryAdapter(
        channelName: String,
        delivery: kz.mybrain.superkassa.core.domain.model.settings.DeliverySettings?
    ): DeliveryAdapter? {
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

    private fun createPrintAdapter(print: kz.mybrain.superkassa.core.domain.model.settings.PrintDeliverySettings?): DeliveryAdapter {
        val connection = print?.connection ?: return stubAdapter(DeliveryChannel.PRINT)
        val host = connection.host
        val port = connection.port
        if (host != null && port != null) {
            return io.github.texport.superkassa.jvm.delivery.data.PrintDeliveryAdapter(host, port)
        }
        return stubAdapter(DeliveryChannel.PRINT)
    }

    private fun createEmailAdapter(email: kz.mybrain.superkassa.core.domain.model.settings.EmailProviderSettings?): DeliveryAdapter {
        if (email == null) return stubAdapter(DeliveryChannel.EMAIL)
        return io.github.texport.superkassa.jvm.delivery.data.EmailDeliveryAdapter(
            email.host, email.port, email.user ?: "", email.password ?: "", email.from
        )
    }

    private fun createSmsAdapter(sms: kz.mybrain.superkassa.core.domain.model.settings.SmsProviderSettings?): DeliveryAdapter {
        val url = sms?.providerUrl ?: return stubAdapter(DeliveryChannel.SMS)
        return io.github.texport.superkassa.jvm.delivery.data.SmsDeliveryAdapter(url, sms.apiKey)
    }

    private fun createTelegramAdapter(tg: kz.mybrain.superkassa.core.domain.model.settings.TelegramProviderSettings?): DeliveryAdapter {
        val token = tg?.botToken ?: return stubAdapter(DeliveryChannel.TELEGRAM)
        return io.github.texport.superkassa.jvm.delivery.data.TelegramDeliveryAdapter(token)
    }

    private fun createWhatsAppAdapter(wa: kz.mybrain.superkassa.core.domain.model.settings.WhatsAppProviderSettings?): DeliveryAdapter {
        val token = wa?.accessToken ?: return stubAdapter(DeliveryChannel.WHATSAPP)
        val phoneId = wa.phoneNumberId ?: return stubAdapter(DeliveryChannel.WHATSAPP)
        return io.github.texport.superkassa.jvm.delivery.data.WhatsAppDeliveryAdapter(token, phoneId)
    }

    private fun stubAdapter(channel: DeliveryChannel): DeliveryAdapter = object : DeliveryAdapter {
        override val channel: DeliveryChannel = channel
        override fun send(request: DeliveryRequest): DeliveryResult {
            logger.debug("Delivery stub for channel: {}", channel)
            return DeliveryResult(true)
        }
    }

    @Bean
    fun receiptRenderPort(): ReceiptRenderPort {
        return kz.mybrain.superkassa.core.data.receipt.ReceiptHtmlRenderer(
            qrCodeGenerator = io.github.texport.superkassa.jvm.receipt.data.QrCodeDataUriGenerator
        )
    }

    @Bean
    fun documentConvertPort(): DocumentConvertPort = io.github.texport.superkassa.jvm.receipt.data.DocumentConvertAdapter()

    @Bean
    fun timeValidatorPort(): TimeValidatorPort = kz.mybrain.superkassa.core.data.adapter.SystemTimeGuard

    @Bean
    fun ofdConfigPort(): OfdConfigPort = OfdConfigAdapter()

    @Bean
    fun pinHasherPort(): PinHasherPort = Sha256PinHasherAdapter()

    @Bean
    fun ofdManagerPort(settings: CoreSettings, storage: StoragePort): OfdManagerPort {
        val shiftCountersRecalculator = RecalculateShiftCountersUseCase(storage)
        val requestBuilders = listOf(
            ServiceRequestBuilderStrategy(),
            MoneyPlacementRequestBuilderStrategy(storage),
            ReportRequestBuilderStrategy(storage, shiftCountersRecalculator),
            CloseShiftRequestBuilderStrategy(storage, shiftCountersRecalculator),
            TicketRequestBuilderStrategy(storage)
        )
        val delegate = OfdManagerAdapter(
            OfdConfig(protocolVersion = settings.ofdProtocolVersion),
            OfdProtocolCodec(),
            OfdTcpNetworkClient(),
            requestBuilders = requestBuilders,
            timeoutSeconds = settings.ofdTimeoutSeconds.coerceAtLeast(5L),
            reconnectIntervalSeconds = settings.ofdReconnectIntervalSeconds.coerceAtLeast(60L)
        )
        return ResilienceOfdManagerAdapter(delegate)
    }

    @Bean
    fun tokenCodecPort(): TokenCodecPort =
        kz.mybrain.superkassa.core.data.adapter.Base64TokenCodecAdapter()
}
