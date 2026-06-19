package kz.mybrain.superkassa.core.config

import kz.mybrain.network.OfdTcpNetworkClient
import kz.mybrain.superkassa.core.application.model.CoreMode
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.model.StorageSettings
import kz.mybrain.superkassa.core.application.service.FileCoreSettingsRepository
import kz.mybrain.superkassa.core.application.service.ShiftCountersRecalculator
import kz.mybrain.superkassa.core.data.adapter.DeliveryPortAdapter
import kz.mybrain.superkassa.core.data.adapter.CloseShiftRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.MoneyPlacementRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.OfdConfigPortAdapter
import kz.mybrain.superkassa.core.data.adapter.ReportRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.OfdManagerAdapter
import kz.mybrain.superkassa.core.data.adapter.ServiceRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.TicketRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.adapter.ResilienceOfdManagerPortAdapter
import kz.mybrain.superkassa.core.data.adapter.OfflineQueuePortAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedQueueStoragePort
import kz.mybrain.superkassa.core.data.adapter.StorageBackedLeaseLockPort
import kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherPort
import kz.mybrain.superkassa.core.data.adapter.StoragePortAdapter
import kz.mybrain.superkassa.core.data.ofd.OfdCodecService
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.domain.port.*
import kz.mybrain.superkassa.delivery.application.service.DeliveryService
import kz.mybrain.superkassa.delivery.domain.model.DeliveryChannel
import kz.mybrain.superkassa.delivery.domain.model.DeliveryRequest
import kz.mybrain.superkassa.delivery.domain.model.DeliveryResult
import kz.mybrain.superkassa.delivery.domain.port.DeliveryAdapter
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import kz.mybrain.superkassa.storage.application.health.StorageHealthChecker
import kz.mybrain.superkassa.storage.data.bootstrap.DefaultStorageBootstrap
import kz.mybrain.superkassa.storage.data.jdbc.DefaultStorageConnectorRegistry
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths

@Configuration
class AdaptersConfig {

    private val logger = LoggerFactory.getLogger(AdaptersConfig::class.java)

    @Bean
    fun settingsRepository(): FileCoreSettingsRepository {
        return FileCoreSettingsRepository(Paths.get("config/core-settings.json"))
    }

    @Bean
    fun coreSettings(repository: FileCoreSettingsRepository): CoreSettings {
        val defaults = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(
                engine = "SQLITE",
                jdbcUrl = "jdbc:sqlite:build/core.db"
            ),
            allowChanges = true
        )
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
        return StoragePortAdapter(storageBootstrap, config)
    }

    @Bean
    fun queueStoragePort(storagePort: StoragePort): QueueStoragePort {
        return StorageBackedQueueStoragePort(storagePort)
    }

    @Bean
    fun queueLeaseLockPort(storagePort: StoragePort): LeaseLockPort {
        return StorageBackedLeaseLockPort(storagePort)
    }

    @Bean
    fun queuePort(
        settings: CoreSettings,
        queueStoragePort: QueueStoragePort,
        queueLeaseLockPort: LeaseLockPort,
        queueCommandHandler: QueueCommandHandler
    ): OfflineQueuePort {
        val ownerId = settings.nodeId
        return OfflineQueuePortAdapter(queueStoragePort, queueLeaseLockPort, queueCommandHandler, ownerId = ownerId)
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
        return DeliveryPortAdapter(DeliveryService(adapters))
    }

    private fun createDeliveryAdapter(
        channelName: String,
        delivery: kz.mybrain.superkassa.core.application.model.DeliverySettings?
    ): DeliveryAdapter? {
        val channel = try {
            DeliveryChannel.valueOf(channelName)
        } catch (e: IllegalArgumentException) {
            logger.warn("Unknown delivery channel: $channelName", e)
            return null
        }
        return when (channel) {
            DeliveryChannel.PRINT -> {
                val print = delivery?.print
                val conn = print?.connection
                val host = conn?.host
                val port = conn?.port
                if (host != null && port != null) {
                    kz.mybrain.superkassa.delivery.data.adapter.PrintDeliveryAdapter(host, port)
                } else {
                    stubAdapter(DeliveryChannel.PRINT)
                }
            }
            DeliveryChannel.EMAIL -> {
                val e = delivery?.email
                if (e != null) {
                    kz.mybrain.superkassa.delivery.data.adapter.EmailDeliveryAdapter(
                        e.host, e.port, e.user, e.password, e.from
                    )
                } else {
                    stubAdapter(DeliveryChannel.EMAIL)
                }
            }
            DeliveryChannel.SMS -> {
                val s = delivery?.sms
                val url = s?.providerUrl
                if (s != null && url != null) {
                    kz.mybrain.superkassa.delivery.data.adapter.SmsDeliveryAdapter(url, s.apiKey)
                } else {
                    stubAdapter(DeliveryChannel.SMS)
                }
            }
            DeliveryChannel.TELEGRAM -> {
                val t = delivery?.telegram
                val token = t?.botToken
                if (t != null && token != null) {
                    kz.mybrain.superkassa.delivery.data.adapter.TelegramDeliveryAdapter(token)
                } else {
                    stubAdapter(DeliveryChannel.TELEGRAM)
                }
            }
            DeliveryChannel.WHATSAPP -> {
                val w = delivery?.whatsapp
                val token = w?.accessToken
                val phoneId = w?.phoneNumberId
                if (w != null && token != null && phoneId != null) {
                    kz.mybrain.superkassa.delivery.data.adapter.WhatsAppDeliveryAdapter(token, phoneId)
                } else {
                    stubAdapter(DeliveryChannel.WHATSAPP)
                }
            }
        }
    }

    private fun stubAdapter(channel: DeliveryChannel): DeliveryAdapter = object : DeliveryAdapter {
        override val channel: DeliveryChannel = channel
        override fun send(request: DeliveryRequest): DeliveryResult {
            logger.debug("Delivery stub for channel: {}", channel)
            return DeliveryResult(true)
        }
    }

    @Bean
    fun receiptRenderPort(): ReceiptRenderPort = kz.mybrain.superkassa.core.data.receipt.ReceiptHtmlRenderer(kz.mybrain.superkassa.core.data.receipt.QrCodeDataUriGenerator)

    @Bean
    fun documentConvertPort(): DocumentConvertPort = kz.mybrain.superkassa.core.data.receipt.DocumentConvertAdapter()

    @Bean
    fun timeValidatorPort(): TimeValidatorPort = kz.mybrain.superkassa.core.application.policy.SystemTimeGuard

    @Bean
    fun ofdConfigPort(): OfdConfigPort = OfdConfigPortAdapter()

    @Bean
    fun pinHasherPort(): PinHasherPort = Sha256PinHasherPort()

    @Bean
    fun ofdManagerPort(settings: CoreSettings, storage: StoragePort): OfdManagerPort {
        val shiftCountersRecalculator = ShiftCountersRecalculator(storage)
        val requestBuilders = listOf(
            ServiceRequestBuilderStrategy(),
            MoneyPlacementRequestBuilderStrategy(storage),
            ReportRequestBuilderStrategy(storage, shiftCountersRecalculator),
            CloseShiftRequestBuilderStrategy(storage, shiftCountersRecalculator),
            TicketRequestBuilderStrategy()
        )
        val delegate = OfdManagerAdapter(
            OfdConfig(protocolVersion = settings.ofdProtocolVersion),
            OfdCodecService(),
            OfdTcpNetworkClient(),
            requestBuilders = requestBuilders,
            timeoutSeconds = settings.ofdTimeoutSeconds.coerceAtLeast(5L),
            reconnectIntervalSeconds = settings.ofdReconnectIntervalSeconds.coerceAtLeast(60L)
        )
        return ResilienceOfdManagerPortAdapter(delegate)
    }

    @Bean
    fun tokenCodecPort(): TokenCodecPort =
        kz.mybrain.superkassa.core.data.adapter.Base64TokenCodecPort()
}
