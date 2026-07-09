package io.github.texport.superkassa.jvm.storage.impl.core

import io.github.texport.superkassa.delivery.application.service.DeliveryService
import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.time.impl.SystemClock
import io.github.texport.superkassa.jvm.time.impl.SystemTimeGuard
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kz.mybrain.superkassa.core.data.adapter.DeliveryServiceAdapter
import kz.mybrain.superkassa.core.data.adapter.OfflineQueueAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedLeaseLockAdapter
import kz.mybrain.superkassa.core.data.adapter.StorageBackedQueueStorageAdapter
import kz.mybrain.superkassa.core.data.adapter.UuidGeneratorAdapter
import kz.mybrain.superkassa.core.domain.model.common.Money
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandRequest
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandType
import kz.mybrain.superkassa.core.domain.model.receipt.PaymentType
import kz.mybrain.superkassa.core.domain.model.receipt.ReceiptItem
import kz.mybrain.superkassa.core.domain.model.receipt.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.receipt.ReceiptPayment
import kz.mybrain.superkassa.core.domain.model.receipt.ReceiptRequest
import kz.mybrain.superkassa.core.domain.port.OfdManagerPort
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApiImpl
import kz.mybrain.superkassa.core.presentation.model.KkmInitDirectRequest
import kz.mybrain.superkassa.core.presentation.model.OfdServiceInfoDto
import kz.mybrain.superkassa.core.presentation.model.UserRoleDto
import kz.mybrain.superkassa.core.presentation.model.UserUpdateRequest
import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

class CoreIntegrationTest {
    @Test
    fun shouldRunFullFlowWithCodec() {
        val dbFile = Files.createTempFile("core-integration", ".db").toFile()
        val storageConfig = StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}")
        val storageBootstrap = DefaultStorageBootstrap()
        storageBootstrap.migrate(storageConfig)

        val storage = StorageAdapter(storageBootstrap, storageConfig)
        val queueStorage = StorageBackedQueueStorageAdapter(storage)
        val queueLocks = StorageBackedLeaseLockAdapter(storage)
        val queueHandler = QueueCommandHandler { _, _ -> DispatchResult(QueueStatus.SENT) }
        val queuePort = OfflineQueueAdapter(queueStorage, queueLocks, queueHandler, ownerId = "node-1")

        val deliveryService = DeliveryService(
            listOf(
                object : DeliveryAdapter {
                    override val channel: DeliveryChannel = DeliveryChannel.PRINT
                    override fun send(request: DeliveryRequest): DeliveryResult = DeliveryResult(true)
                }
            )
        )
        val deliveryPort = DeliveryServiceAdapter(deliveryService)

        val ofdManager = object : OfdManagerPort {
            override fun send(command: OfdCommandRequest): OfdCommandResult {
                val responseJson = buildJsonObject {
                    put(
                        "header",
                        buildJsonObject {
                            put("token", JsonPrimitive(12345))
                            put("reqNum", JsonPrimitive(command.reqNum))
                        }
                    )
                    put(
                        "payload",
                        buildJsonObject {
                            put(
                                "result",
                                buildJsonObject {
                                    put("resultCode", JsonPrimitive(0))
                                }
                            )
                            if (command.commandType == OfdCommandType.INFO) {
                                put(
                                    "report",
                                    buildJsonObject {
                                        put(
                                            "zxReport",
                                            buildJsonObject {
                                                put("shiftNumber", JsonPrimitive(1))
                                                put(
                                                    "nonNullableSums",
                                                    kotlinx.serialization.json.buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("operation", JsonPrimitive("OPERATION_SELL"))
                                                                put(
                                                                    "sum",
                                                                    buildJsonObject {
                                                                        put("bills", JsonPrimitive(1000))
                                                                        put("coins", JsonPrimitive(0))
                                                                    }
                                                                )
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
                return OfdCommandResult(
                    status = OfdCommandStatus.OK,
                    responseJson = responseJson,
                    responseToken = 12345,
                    responseReqNum = command.reqNum,
                    resultCode = 0
                )
            }
        }

        val ofdConfigPort = kz.mybrain.superkassa.core.data.adapter.OfdConfigAdapter()
        val pinHasher = kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherAdapter()
        val tokenCodec = kz.mybrain.superkassa.core.data.adapter.Base64TokenCodecAdapter()
        val coreSettings = kz.mybrain.superkassa.core.domain.model.settings.CoreSettings(
            mode = kz.mybrain.superkassa.core.domain.model.settings.CoreMode.DESKTOP,
            storage = kz.mybrain.superkassa.core.domain.model.settings.StorageSettings(
                engine = "SQLITE",
                jdbcUrl = "jdbc:sqlite:build/test-core.db"
            ),
            allowChanges = true
        )
        val mockQrCodeGenerator = object : kz.mybrain.superkassa.core.domain.port.QrCodeGeneratorPort {
            override fun generatePngDataUri(text: String, sizePx: Int): String? = null
        }
        val receiptRenderPort = kz.mybrain.superkassa.core.data.receipt.ReceiptHtmlRenderer(mockQrCodeGenerator)
        val documentConvertPort = object : kz.mybrain.superkassa.core.domain.port.DocumentConvertPort {
            override fun htmlToPdf(html: String): ByteArray = byteArrayOf(1, 2, 3)
            override fun htmlToImage(html: String): ByteArray = byteArrayOf(1, 2, 3)
            override fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray = byteArrayOf(1, 2, 3)
        }
        val service = SuperkassaApiImpl(
            storage = storage,
            queue = queuePort,
            ofd = ofdManager,
            ofdConfig = ofdConfigPort,
            delivery = deliveryPort,
            tokenCodec = tokenCodec,
            idGenerator = UuidGeneratorAdapter,
            clock = SystemClock,
            pinHasher = pinHasher,
            coreSettings = coreSettings,
            receiptRenderPort = receiptRenderPort,
            documentConvertPort = documentConvertPort,
            timeValidator = SystemTimeGuard
        )

        val init = service.initKkm(
            "0000",
            KkmInitDirectRequest(
                ofdId = "KAZAKHTELECOM",
                ofdEnvironment = "TEST",
                ofdSystemId = "12345",
                ofdToken = "12345",
                kkmKgdId = "RN-1",
                factoryNumber = "FN-1",
                manufactureYear = 2024,
                serviceInfo = OfdServiceInfoDto(
                    orgTitle = "Test Org",
                    orgAddress = "Test Address",
                    orgAddressKz = "Test Address KZ",
                    orgInn = "123456789012",
                    orgOkved = "47301",
                    geoLatitude = 1,
                    geoLongitude = 1,
                    geoSource = "TEST"
                )
            )
        )
        val kkmId = init.id

        // List and change PINs to secure ones
        val users = service.listUsers(kkmId, "0000")
        val admin = users.first { it.role == UserRoleDto.ADMIN }
        val cashier = users.first { it.role == UserRoleDto.CASHIER }

        service.updateUser(
            kkmId = kkmId,
            userId = admin.userId,
            pin = "0000",
            request = UserUpdateRequest(
                userPin = "4321"
            )
        )
        service.updateUser(
            kkmId = kkmId,
            userId = cashier.userId,
            pin = "4321",
            request = UserUpdateRequest(
                userPin = "5432"
            )
        )

        val shift = service.openShift(kkmId, "4321")
        assertNotNull(shift)

        val receipt = ReceiptRequest(
            kkmId = kkmId,
            pin = "5432",
            operation = ReceiptOperationType.SELL,
            items = listOf(ReceiptItem("Item", "1", 1, Money(1000, 0), Money(1000, 0))),
            payments = listOf(ReceiptPayment(PaymentType.CASH, Money(1000, 0))),
            total = Money(1000, 0),
            idempotencyKey = "idem-1"
        )
        val result = service.createReceipt(receipt)
        assertNotNull(result.documentId)

        val report = service.closeShift(kkmId, "5432")
        assertNotNull(report.documentId)
    }
}
