package io.github.texport.superkassa.jvm.storage.impl.core

import io.github.texport.superkassa.core.domain.api.model.common.Decimal
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
import io.github.texport.superkassa.core.presentation.api.model.user.UserCreateRequest
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptDocumentTypes
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
import kotlin.test.assertEquals
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
                                            // Тип отчёта задан явно: при его отсутствии
                                            // разбор считает смену открытой, и регистрация
                                            // восстановит её, а тест открывает смену сам.
                                            "reportType", JsonPrimitive("REPORT_Z")
                                        )
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
                ),
                adminPin = "4321"
            )
        )
        val kkmId = init.kkmId

        // Касса рождается с одним администратором: кассира заводит он сам.
        val users = service.listUsers(kkmId, "4321")
        assertEquals(1, users.size)
        assertEquals(UserRole.ADMIN, users.first().role)

        service.createUser(
            kkmId = kkmId,
            pin = "4321",
            request = UserCreateRequest(
                name = "Кассир",
                role = UserRole.CASHIER,
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
                    price = Decimal.parse("10.0"),
                    quantity = Decimal.parse("1.0"),
                    barcode = null,
                    // Касса этого прогона плательщиком НДС не заводится,
                    // и ставка в позиции ей запрещена: ядро отвергает такой
                    // чек до фискального эффекта. Проверяется здесь не налог,
                    // а обратное чтение чека и стойкость причины отказа.
                    vatGroup = null,
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
                    sum = Decimal.parse("10.0")
                )
            ),
            taken = Decimal.parse("10.0"),
            parentTicket = null,
            defaultVatGroup = null,
            customerBin = null
        )
        val result = service.createReceipt(command)
        assertNotNull(result.documentId)

        // Содержимое чека обязано читаться обратно по его документу: из него
        // собирается запрос в ОФД и пересчёт счётчиков смены. Проверка на
        // единственный прежний тип «CHECK» отсекала каждый чек, записанный
        // под именем своей операции: в ОФД он не уходил вовсе, а X-отчёт
        // считал кассу пустой.
        val stored = storage.findFiscalDocumentWithReceiptPayload(result.documentId)
        assertNotNull(stored)
        assertEquals(ReceiptDocumentTypes.SALE, stored.first.docType)
        assertEquals(1, stored.second.items.size)

        val report = service.closeShift(kkmId, "5432")
        assertNotNull(report.documentId)

        // Причина отказа обязана переживать перезапуск. Колонки под неё
        // в схеме не было, и узел помечал документ отвергнутым, но код
        // отказа молча терял: кассир видел «ошибка» без причины.
        storage.updateReceiptStatus(
            documentId = result.documentId,
            fiscalSign = null,
            autonomousSign = null,
            ofdStatus = "FAILED",
            ofdErrorCode = 13,
            deliveredAt = null,
            isAutonomous = false
        )
        assertEquals(13, storage.findFiscalDocumentById(result.documentId)?.ofdErrorCode)
    }
}
