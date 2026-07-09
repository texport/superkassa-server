package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.delivery.application.service.DeliveryService
import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.EmailDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.PrintDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.SmsDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.TelegramDeliveryAdapter
import io.github.texport.superkassa.jvm.delivery.impl.WhatsAppDeliveryAdapter
import io.github.texport.superkassa.jvm.receipt.impl.DocumentConvertAdapter
import io.github.texport.superkassa.jvm.receipt.impl.QrCodeDataUriGenerator
import io.github.texport.superkassa.jvm.settings.impl.FileCoreSettingsRepository
import io.github.texport.superkassa.jvm.storage.impl.adapter.DatabaseCoreSettingsRepository
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.DefaultStorageConnectorRegistry
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.time.impl.SystemClock
import io.github.texport.superkassa.jvm.time.impl.SystemTimeGuard
import kz.mybrain.network.OfdTcpNetworkClient
import kz.mybrain.superkassa.core.data.adapter.DeliveryServiceAdapter
import kz.mybrain.superkassa.core.data.adapter.OfdConfigAdapter
import kz.mybrain.superkassa.core.data.adapter.OfdManagerAdapter
import kz.mybrain.superkassa.core.data.adapter.OfflineQueueAdapter
import kz.mybrain.superkassa.core.data.adapter.ResilienceOfdManagerAdapter
import kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedLeaseLockAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedQueueStorageAdapter
import kz.mybrain.superkassa.core.data.ofd.OfdConfig
import kz.mybrain.superkassa.core.data.ofd.OfdProtocolCodec
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.CloseShiftRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.MoneyPlacementRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.ReportRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.ServiceRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.TicketRequestBuilderStrategy
import kz.mybrain.superkassa.core.data.ofd.builder.strategy.NomenclatureRequestBuilderStrategy
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.model.settings.StorageSettings
import kz.mybrain.superkassa.core.domain.port.*
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.port.CoreSettingsRepositoryPort
import kz.mybrain.superkassa.core.domain.usecase.shift.RecalculateShiftCountersUseCase
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import java.nio.file.Paths

@Configuration
class AdaptersConfig {

    private val logger = LoggerFactory.getLogger(AdaptersConfig::class.java)

    @Value("\${app.log-language:ALL}")
    private lateinit var logLanguage: String

    @Value("\${app.logging.pretty-print-json:true}")
    private var prettyPrintJson: Boolean = true

    @PostConstruct
    fun init() {
        System.setProperty("superkassa.log.language", logLanguage)
        logger.info("Установлен системный язык логирования: {}", logLanguage)
    }

    @Bean
    fun settingsRepository(
        @Value($$"${spring.datasource.url:}") dbUrl: String,
        @Value($$"${spring.datasource.username:}") dbUser: String?,
        @Value($$"${spring.datasource.password:}") dbPass: String?
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
        @Value($$"${spring.datasource.url:}") dbUrl: String,
        @Value($$"${spring.datasource.username:}") dbUser: String?,
        @Value($$"${spring.datasource.password:}") dbPass: String?
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
            return PrintDeliveryAdapter(host, port)
        }
        return stubAdapter(DeliveryChannel.PRINT)
    }

    private fun createEmailAdapter(email: kz.mybrain.superkassa.core.domain.model.settings.EmailProviderSettings?): DeliveryAdapter {
        if (email == null) return stubAdapter(DeliveryChannel.EMAIL)
        return EmailDeliveryAdapter(
            email.host,
            email.port,
            email.user ?: "",
            email.password ?: "",
            email.from
        )
    }

    private fun createSmsAdapter(sms: kz.mybrain.superkassa.core.domain.model.settings.SmsProviderSettings?): DeliveryAdapter {
        val url = sms?.providerUrl ?: return stubAdapter(DeliveryChannel.SMS)
        return SmsDeliveryAdapter(url, sms.apiKey)
    }

    private fun createTelegramAdapter(tg: kz.mybrain.superkassa.core.domain.model.settings.TelegramProviderSettings?): DeliveryAdapter {
        val token = tg?.botToken ?: return stubAdapter(DeliveryChannel.TELEGRAM)
        return TelegramDeliveryAdapter(token)
    }

    private fun createWhatsAppAdapter(wa: kz.mybrain.superkassa.core.domain.model.settings.WhatsAppProviderSettings?): DeliveryAdapter {
        val token = wa?.accessToken ?: return stubAdapter(DeliveryChannel.WHATSAPP)
        val phoneId = wa.phoneNumberId ?: return stubAdapter(DeliveryChannel.WHATSAPP)
        return WhatsAppDeliveryAdapter(token, phoneId)
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
            qrCodeGenerator = QrCodeDataUriGenerator
        )
    }

    @Bean
    fun documentConvertPort(): DocumentConvertPort = DocumentConvertAdapter()

    @Bean
    fun timeValidatorPort(): TimeValidatorPort = SystemTimeGuard

    @Bean
    fun clockPort(): ClockPort = SystemClock

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
            TicketRequestBuilderStrategy(storage),
            NomenclatureRequestBuilderStrategy()
        )
        val delegate = OfdManagerAdapter(
            OfdConfig(
                protocolVersion = settings.ofdProtocolVersion,
                prettyPrintJson = prettyPrintJson
            ),
            OfdProtocolCodec(),
            OfdTcpNetworkClient(),
            requestBuilders = requestBuilders,
            timeoutSeconds = settings.ofdTimeoutSeconds.coerceAtLeast(5L),
            reconnectIntervalSeconds = settings.ofdReconnectIntervalSeconds.coerceAtLeast(60L)
        )
        val resilience = ResilienceOfdManagerAdapter(delegate)
        return InterceptorsOfdManager(resilience)
    }

    @Bean
    fun tokenCodecPort(): TokenCodecPort =
        kz.mybrain.superkassa.core.data.adapter.Base64TokenCodecAdapter()
}

class InterceptorsOfdManager(
    private val delegate: OfdManagerPort
) : OfdManagerPort {
    override fun send(command: OfdCommandRequest): OfdCommandResult {
        val result = delegate.send(command)
        if (result.status == OfdCommandStatus.OK) {
            val responseJson = result.responseJson
            if (responseJson != null) {
                val payload = responseJson["payload"] as? kotlinx.serialization.json.JsonObject
                val ticket = payload?.get("ticket") as? kotlinx.serialization.json.JsonObject
                
                // Extract ticketNumber (fiscalSign) if it was null
                var finalFiscalSign = result.fiscalSign
                if (finalFiscalSign == null && ticket != null) {
                    finalFiscalSign = ticket["ticketNumber"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                    }
                }
                
                // Extract receiptUrl
                var finalReceiptUrl = result.receiptUrl
                if (finalReceiptUrl == null && ticket != null) {
                    val qrCodeBase64 = ticket["qrCodeBase64"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                    }
                    if (qrCodeBase64 != null) {
                        finalReceiptUrl = try {
                            String(java.util.Base64.getDecoder().decode(qrCodeBase64), Charsets.UTF_8)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                
                if (finalReceiptUrl != null) {
                    StorageAdapter.receiptUrlMap[command.payloadRef] = finalReceiptUrl
                }
                
                if (finalFiscalSign != result.fiscalSign || finalReceiptUrl != result.receiptUrl) {
                    return result.copy(
                        fiscalSign = finalFiscalSign,
                        receiptUrl = finalReceiptUrl
                    )
                }
            }
        }
        return result
    }
}
