package io.github.texport.superkassa.jvm.storage.impl.core

import io.github.texport.superkassa.core.data.api.SuperkassaCoreEngine
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandRequest
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandResult
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandStatus
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandType
import io.github.texport.superkassa.core.domain.api.port.internal.OfdManagerPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmInitDirectRequest
import io.github.texport.superkassa.core.presentation.api.model.kkm.OfdServiceInfoResponse
import io.github.texport.superkassa.core.presentation.api.model.user.UserRole
import io.github.texport.superkassa.core.presentation.api.model.user.UserUpdateRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.CreateReceiptCommand
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptItemRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptPaymentRequest
import io.github.texport.superkassa.core.presentation.impl.SuperkassaApiImpl
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.time.impl.SystemClock
import io.github.texport.superkassa.jvm.time.impl.SystemTimeGuard
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

class CoreIntegrationTest {

    @Test
    fun testCoreIntegrationWorkflow() {
        val tempDir = Files.createTempDirectory("core-integration-test")
        val dbFile = tempDir.resolve("test-core.db").toFile()

        val storageConfig = StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}")
        val storageBootstrap = DefaultStorageBootstrap()
        storageBootstrap.migrate(storageConfig)

        val storage = StorageAdapter(storageBootstrap, storageConfig)

        val deliveryPort = object : io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort {
            override fun deliver(request: io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest): Boolean = true
        }

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

        val coreSettings = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(
                engine = "SQLITE",
                jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            ),
            allowChanges = true
        )

        val mockSettingsRepo = object : CoreSettingsRepositoryPort {
            override fun load(): CoreSettings? = coreSettings
            override fun save(settings: CoreSettings): Boolean = true
            override fun loadOrCreate(defaults: CoreSettings): CoreSettings = coreSettings
        }

        val mockQrCodeGenerator = object : io.github.texport.superkassa.core.domain.api.port.integration.QrCodeGeneratorPort {
            override fun generatePngDataUri(text: String, sizePx: Int): String? = null
        }
        val documentConvertPort = object : io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort {
            override fun htmlToPdf(html: String): ByteArray = byteArrayOf(1, 2, 3)
            override fun htmlToImage(html: String): ByteArray = byteArrayOf(1, 2, 3)
            override fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray = byteArrayOf(1, 2, 3)
        }

        val engine = SuperkassaCoreEngine(
            storage = storage,
            settings = mockSettingsRepo,
            delivery = deliveryPort,
            clock = SystemClock,
            timeValidator = SystemTimeGuard,
            qrCode = mockQrCodeGenerator,
            pdfConverter = documentConvertPort
        )
        val service = engine.buildApi(ownerId = "node-1")

        // Внедряем заглушку ofdManager через рефлексию
        val apiImpl = service as SuperkassaApiImpl
        val ofdField = apiImpl.javaClass.getDeclaredField("ofd")
        ofdField.isAccessible = true
        ofdField.set(apiImpl, ofdManager)

        val kkmCommonHelperField = apiImpl.javaClass.getDeclaredField("kkmCommonHelper")
        kkmCommonHelperField.isAccessible = true
        val kkmCommonHelper = kkmCommonHelperField.get(apiImpl)
        val helperOfdField = kkmCommonHelper.javaClass.getDeclaredField("ofd")
        helperOfdField.isAccessible = true
        helperOfdField.set(kkmCommonHelper, ofdManager)

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
                serviceInfo = OfdServiceInfoResponse(
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
        val kkmId = init.kkmId

        // List and change PINs to secure ones
        val users = service.listUsers(kkmId, "0000")
        val admin = users.first { it.role == UserRole.ADMIN }
        val cashier = users.first { it.role == UserRole.CASHIER }

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

        val command = CreateReceiptCommand(
            kkmId = kkmId,
            pin = "5432",
            operation = "SELL",
            idempotencyKey = "idem-1",
            items = listOf(
                ReceiptItemRequest(
                    name = "Item",
                    price = 10.0,
                    quantity = 1.0,
                    barcode = null,
                    vatGroup = "VAT_16",
                    discountPercent = null,
                    discountSum = null,
                    markupPercent = null,
                    markupSum = null,
                    measureUnitCode = "796",
                    listExciseStamp = null,
                    ntin = null,
                    isStorno = false
                )
            ),
            discountPercent = null,
            discountSum = null,
            markupPercent = null,
            markupSum = null,
            payments = listOf(
                ReceiptPaymentRequest(
                    type = "CASH",
                    sum = 10.0
                )
            ),
            taken = 10.0,
            parentTicket = null,
            defaultVatGroup = null,
            customerBin = null
        )
        val result = service.createReceipt(command)
        assertNotNull(result.documentId)

        val report = service.closeShift(kkmId, "5432")
        assertNotNull(report.documentId)
    }
}
