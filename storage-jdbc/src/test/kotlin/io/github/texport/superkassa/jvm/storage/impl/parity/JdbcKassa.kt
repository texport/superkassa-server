package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.core.data.api.SuperkassaCoreEngine
import io.github.texport.superkassa.core.domain.api.model.auth.UserRole
import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.github.texport.superkassa.core.domain.api.model.common.TimeValidationResult
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest
import io.github.texport.superkassa.core.domain.api.model.kkm.FiscalDocumentSnapshot
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmMode
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmState
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdServiceInfo
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.QrCodeGeneratorPort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptItemRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptPaymentRequest
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.time.impl.SystemClock
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Касса на ядре с хранилищем узла: сборка та же, что у узла, база — SQLite
 * во временном каталоге, сеть — [FakeBfd], доставка покупателю — [DeliveryLog].
 * Касса зарегистрирована, у неё администратор и кассир.
 *
 * Та же касса, что проверяется в ядре на Room: сценарии здесь те же, и
 * расхождение хранилищ видно по одинаковым проверкам.
 *
 * @param dir каталог базы; вторая касса на том же каталоге — перезапуск узла.
 */
internal class JdbcKassa(
    val dir: Path = Files.createTempDirectory("jdbc-kassa"),
    val clock: MovableClock = MovableClock(),
    register: Boolean = true
) {
    val bfd = FakeBfd(TOKEN)
    val deliveries = DeliveryLog()
    val storage = openStorage(dir)
    val api: SuperkassaApi = SuperkassaCoreEngine(
        storage = storage,
        pinAttempts = storage.pinAttempts,
        settings = MemorySettings(),
        delivery = deliveries,
        clock = clock,
        timeValidator = TrustedClock,
        qrCode = NoQrCodes,
        pdfConverter = NoDocuments,
        ofdTransport = bfd
    ).buildApi()

    init {
        if (register) register()
    }

    /** Документы открытой смены, от первого к последнему. */
    fun shiftDocuments(): List<FiscalDocumentSnapshot> {
        val shift = checkNotNull(storage.findOpenShift(KKM)) { "no open shift" }
        return storage.listFiscalDocumentsByShift(KKM, shift.id, limit = 100, offset = 0).sortedBy { it.createdAt }
    }

    fun document(id: String): FiscalDocumentSnapshot = checkNotNull(storage.findFiscalDocumentById(id)) { "no document $id" }

    /** Настройки кассы, как их правит администратор: через режим программирования. */
    fun settings(autoCloseShift: Boolean = false, autoCashout: Boolean = false) {
        api.enterProgramming(KKM, ADMIN_PIN)
        api.updateKkmSettings(KKM, ADMIN_PIN, autoCloseShift = autoCloseShift, autoCashout = autoCashout)
        api.exitProgramming(KKM, ADMIN_PIN)
    }

    /** Задачи очереди досылки по документу. */
    fun queueTasks(documentId: String) =
        storage.listQueueTasksByCashbox(KKM, "OFFLINE", limit = 100, offset = 0).filter { it.payloadRef == documentId }

    /** Пауза восстановления связи прошла, и очередь досылается. */
    fun reconnectAndResend(): Int {
        clock.move(RECONNECT_MILLIS)
        return api.queue.processOfflineBatch(KKM, limit = 100)
    }

    private fun register() {
        val now = clock.now()
        storage.createKkm(
            KkmInfo(
                id = KKM, createdAt = now, updatedAt = now,
                mode = KkmMode.REGISTRATION.name, state = KkmState.ACTIVE.name,
                ofdProvider = "KAZAKHTELECOM:TEST", registrationNumber = KGD_NUMBER, factoryNumber = "KZT0000001",
                systemId = "100500", ofdServiceInfo = SERVICE_INFO,
                // Токен хранится так, как его пишет ядро: десятичная запись в Base64.
                tokenEncryptedBase64 = Base64.getEncoder().encodeToString(TOKEN.toString().toByteArray()),
                tokenUpdatedAt = now
            )
        )
        storage.createUser(KKM, "admin-1", "Айгерим", UserRole.ADMIN, pinHash(ADMIN_PIN), now)
        storage.createUser(KKM, "cashier-1", "Нурлан", UserRole.CASHIER, pinHash(CASHIER_PIN), now)
    }

    private class MemorySettings : CoreSettingsRepositoryPort {
        private var current: CoreSettings? = null
        override fun load(): CoreSettings? = current
        override fun save(settings: CoreSettings): Boolean {
            current = settings
            return true
        }
        override fun loadOrCreate(defaults: CoreSettings): CoreSettings = current ?: defaults.also { current = it }
    }

    private object TrustedClock : TimeValidatorPort {
        override fun validate(clock: ClockPort) = TimeValidationResult(ok = true)
    }

    private object NoQrCodes : QrCodeGeneratorPort {
        override fun generatePngDataUri(text: String, sizePx: Int): String? = null
    }

    private object NoDocuments : DocumentConvertPort {
        override fun htmlToPdf(html: String): ByteArray = error("not used")
        override fun htmlToImage(html: String): ByteArray = error("not used")
        override fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray = error("not used")
    }

    companion object {
        const val KKM = "kkm-jdbc-1"
        const val KGD_NUMBER = "010101012345"
        const val ADMIN_PIN = "8765"
        const val CASHIER_PIN = "4321"
        const val TOKEN = 123_456_789L

        /** Больше интервала восстановления связи (не менее 60 с по протоколу). */
        private const val RECONNECT_MILLIS = 61_000L

        private val SERVICE_INFO = OfdServiceInfo(
            orgTitle = "ТОО Дала", orgAddress = "Алматы", orgAddressKz = "Алматы", orgIinOrBin = "123456789012",
            orgOked = "47111", geoLatitude = 43_250_000, geoLongitude = 76_900_000, geoSource = "MANUAL"
        )

        /** Хранилище узла на базе в каталоге [dir], со схемой, доведённой миграциями. */
        fun openStorage(dir: Path): StorageAdapter {
            val config = StorageConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("core.db")}")
            val bootstrap = DefaultStorageBootstrap()
            bootstrap.migrate(config)
            return StorageAdapter(bootstrap, config)
        }

        /** Хеш пина, как его пишет ядро: SHA-256 шестнадцатеричной строкой. */
        fun pinHash(pin: String): String =
            MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Одна позиция на всю сумму и оплата наличными. */
        fun item(sum: String) = ReceiptItemRequest(name = "Нан", price = Decimal.parse(sum), quantity = Decimal.parse("1"))

        fun cash(sum: String) = ReceiptPaymentRequest(type = "CASH", sum = Decimal.parse(sum))
    }
}

/** Доставка чека покупателю, записанная вместо отправки. */
internal class DeliveryLog : DeliveryPort {
    private val delivered = CopyOnWriteArrayList<DeliveryRequest>()

    fun of(documentId: String): List<DeliveryRequest> = delivered.filter { it.documentId == documentId }

    override fun deliver(request: DeliveryRequest): Boolean {
        delivered += request
        return true
    }
}

/** Остановленные часы, которые проверка переводит вперёд и назад. */
internal class MovableClock : ClockPort by SystemClock {
    @Volatile
    private var nowMs = System.currentTimeMillis()

    override fun now(): Long = nowMs

    fun move(ms: Long) {
        nowMs += ms
    }
}
