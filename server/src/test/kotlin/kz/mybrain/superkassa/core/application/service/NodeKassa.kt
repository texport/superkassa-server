package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.data.api.SuperkassaCoreEngine
import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.github.texport.superkassa.core.domain.api.model.common.TimeValidationResult
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliveryChannelSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import io.github.texport.superkassa.core.presentation.api.model.delivery.ReceiptDeliveryResponse
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmInitSimpleRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.CustomerContactRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptItemRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptPaymentRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.github.texport.superkassa.delivery.api.createDeliveryServiceApi
import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import io.github.texport.superkassa.delivery.api.port.DeliveryPort
import io.github.texport.superkassa.jvm.receipt.impl.DocumentConvertAdapter
import io.github.texport.superkassa.jvm.receipt.impl.QrCodeDataUriGenerator
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.testing.api.bfd.FakeBfd
import io.github.texport.superkassa.testing.api.clock.MovableClock
import kz.mybrain.superkassa.core.config.ServerDeliveryServiceAdapter
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Узел с одной кассой, собранный как в запуске: ядро на хранилище JDBC
 * (SQLite в каталоге [dir]), доставка — порт узла поверх сервиса каналов,
 * досылка — [ReceiptDeliveryWorker]. Внешнее подменено: БФД — [FakeBfd]
 * ядра, SMS — [sms], который запоминает отправленное.
 *
 * Второй узел на том же каталоге с теми же [bfd] и [settings] — перезапуск.
 */
internal class NodeKassa(
    val dir: Path,
    val sms: RecordingSms = RecordingSms(),
    val bfd: FakeBfd = FakeBfd(),
    val clock: MovableClock = MovableClock(),
    private val settings: NodeSettings = NodeSettings()
) {
    private val storage = openStorage(dir)
    private val engine = SuperkassaCoreEngine(
        storage = storage,
        pinAttempts = storage.pinAttempts,
        settings = settings,
        delivery = ServerDeliveryServiceAdapter(createDeliveryServiceApi(listOf(sms))),
        clock = clock,
        timeValidator = TrustedTime,
        qrCode = QrCodeDataUriGenerator,
        pdfConverter = DocumentConvertAdapter(),
        ofdTransport = bfd
    )
    val api = engine.buildApi()
    private val delivery = engine.buildDeliveryApi()
    val worker = ReceiptDeliveryWorker(delivery)

    init {
        settings.save(checkNotNull(settings.load()).copy(delivery = SMS_RECEIPT))
    }

    /** Заводит кассу по номеру и токену БФД и открывает смену; возвращает её идентификатор. */
    fun registerAndOpenShift(): String {
        val kkm = api.initKkmSimple(
            KkmInitSimpleRequest(
                ofdId = "KAZAKHTELECOM", ofdEnvironment = "TEST", ofdSystemId = SYSTEM_ID,
                ofdToken = bfd.firstToken.toString(), adminPin = ADMIN_PIN
            )
        )
        api.openShift(kkm.kkmId, ADMIN_PIN)
        return kkm.kkmId
    }

    /** Продажа на 500 ₸ наличными; [buyer] — контакт покупателя из чека. */
    fun sell(kkmId: String, buyer: CustomerContactRequest?, key: String = "sale-1"): ReceiptResponse {
        val sum = Decimal.parse("500.00")
        val item = ReceiptItemRequest(name = "Нан бидайлы", price = sum, quantity = Decimal.parse("1"))
        val request = ReceiptSellRequest(
            idempotencyKey = key, items = listOf(item), payments = listOf(ReceiptPaymentRequest("CASH", sum)),
            customerContact = buyer
        )
        return api.createSellReceipt(kkmId, ADMIN_PIN, request)
    }

    /** Доставка чека по каналам, как её видит журнал кассира. */
    fun deliveries(kkmId: String, receipt: ReceiptResponse): List<ReceiptDeliveryResponse> =
        delivery.receiptDeliveries(kkmId, receipt.documentId, ADMIN_PIN)

    /** Настройки узла в памяти: переживают перезапуск, если передать тот же экземпляр. */
    class NodeSettings : CoreSettingsRepositoryPort {
        @Volatile private var current: CoreSettings? = null
        override fun load(): CoreSettings? = current
        override fun save(settings: CoreSettings): Boolean {
            current = settings
            return true
        }
        override fun loadOrCreate(defaults: CoreSettings): CoreSettings = current ?: defaults.also { current = it }
    }

    private object TrustedTime : TimeValidatorPort {
        override fun validate(clock: ClockPort) = TimeValidationResult(ok = true)
    }

    companion object {
        const val ADMIN_PIN = "7391"
        const val SYSTEM_ID = "100600"

        /** Покупатель, оставивший телефон: чек ему уходит по SMS. */
        val BUYER = CustomerContactRequest(phone = "+77017654321")

        /** Чек покупателю по SMS страницей чека. */
        private val SMS_RECEIPT = DeliverySettings(channels = listOf(DeliveryChannelSettings("SMS", documentFormat = "HTML")))

        private fun openStorage(dir: Path): StorageAdapter {
            val config = StorageConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("node.db")}")
            val bootstrap = DefaultStorageBootstrap()
            bootstrap.migrate(config)
            return StorageAdapter(bootstrap, config)
        }
    }
}

/** Подменный канал SMS: запоминает отправленное и отвечает [answer], а с [failure] — падает. */
internal class RecordingSms(
    @Volatile var answer: DeliveryResult = DeliveryResult(ok = true),
    @Volatile var failure: RuntimeException? = null
) : DeliveryPort {
    override val channel: DeliveryChannel = DeliveryChannel.SMS
    val sent: MutableList<DeliveryRequest> = CopyOnWriteArrayList()

    override fun send(request: DeliveryRequest): DeliveryResult {
        failure?.let { throw it }
        sent += request
        return answer
    }
}
