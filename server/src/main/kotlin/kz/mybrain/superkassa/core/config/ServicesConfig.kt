package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.data.api.SuperkassaCoreEngine
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.QrCodeGeneratorPort
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import io.github.texport.superkassa.core.domain.api.port.internal.PinHasherPort
import io.github.texport.superkassa.core.domain.api.port.internal.ReceiptRenderPort
import io.github.texport.superkassa.core.presentation.api.DeliveryApi
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.impl.DeliveryApiImpl
import io.github.texport.superkassa.core.presentation.impl.SuperkassaApiImpl
import io.github.texport.superkassa.jvm.settings.impl.SettingsApplicationService
import io.github.texport.superkassa.jvm.settings.impl.UpdateSettingsUseCase
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import kz.mybrain.superkassa.core.application.time.ValidateSystemTimeOnStartupUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServicesConfig {

    @Bean
    fun kkmService(
        storage: StoragePort,
        settingsRepository: CoreSettingsRepositoryPort,
        delivery: DeliveryPort,
        clock: ClockPort,
        timeValidator: TimeValidatorPort,
        qrCodeGenerator: QrCodeGeneratorPort,
        documentConvertPort: DocumentConvertPort,
        coreSettings: CoreSettings
    ): SuperkassaApi {
        val engine = SuperkassaCoreEngine(
            storage = storage,
            settings = settingsRepository,
            delivery = delivery,
            clock = clock,
            timeValidator = timeValidator,
            qrCode = qrCodeGenerator,
            pdfConverter = documentConvertPort
        )
        val originalApi = engine.buildApi(ownerId = coreSettings.nodeId)
        return decorateSuperkassaApi(originalApi)
    }

    @Bean
    fun validateSystemTimeOnStartupUseCase(
        timeValidator: TimeValidatorPort,
        clock: ClockPort
    ): ValidateSystemTimeOnStartupUseCase =
        ValidateSystemTimeOnStartupUseCase(timeValidator, clock)

    @Bean
    fun updateSettingsUseCase(
        settingsRepository: CoreSettingsRepositoryPort,
        coreSettings: CoreSettings
    ): UpdateSettingsUseCase =
        UpdateSettingsUseCase(settingsRepository, coreSettings)

    @Bean
    fun settingsApplicationService(
        settingsRepository: CoreSettingsRepositoryPort,
        coreSettings: CoreSettings,
        updateSettingsUseCase: UpdateSettingsUseCase
    ): SettingsApplicationService =
        SettingsApplicationService(settingsRepository, coreSettings, updateSettingsUseCase)

    @Bean
    fun deliveryApi(
        kkmService: SuperkassaApi,
        storage: StoragePort,
        delivery: DeliveryPort,
        coreSettings: CoreSettings,
        documentConvertPort: DocumentConvertPort
    ): DeliveryApi {
        val apiImpl = kkmService as SuperkassaApiImpl
        val field = apiImpl.javaClass.getDeclaredField("receiptRenderPort")
        field.isAccessible = true
        val receiptRenderPort = field.get(apiImpl) as ReceiptRenderPort

        return DeliveryApiImpl(
            storage = storage,
            pinHasher = ServerPinHasherAdapter(),
            delivery = delivery,
            coreSettings = coreSettings,
            documentConvertPort = documentConvertPort,
            receiptRenderPort = receiptRenderPort
        )
    }

    private fun decorateSuperkassaApi(api: SuperkassaApi): SuperkassaApi {
        val apiImpl = api as? SuperkassaApiImpl ?: return api

        // 1. Оборачиваем оригинальный ofdManager в InterceptorsOfdManager
        val ofdField = apiImpl.javaClass.getDeclaredField("ofd")
        ofdField.isAccessible = true
        val originalOfd =
            ofdField.get(apiImpl) as io.github.texport.superkassa.core.domain.api.port.internal.OfdManagerPort
        val decoratedOfd = InterceptorsOfdManager(originalOfd)

        // 2. Устанавливаем поле `ofd` в SuperkassaApiImpl
        ofdField.set(apiImpl, decoratedOfd)

        // 3. Устанавливаем поле `ofd` в KkmCommonHelper
        val kkmCommonHelperField = apiImpl.javaClass.getDeclaredField("kkmCommonHelper")
        kkmCommonHelperField.isAccessible = true
        val kkmCommonHelper = kkmCommonHelperField.get(apiImpl)
        val helperOfdField = kkmCommonHelper.javaClass.getDeclaredField("ofd")
        helperOfdField.isAccessible = true
        helperOfdField.set(kkmCommonHelper, decoratedOfd)

        return apiImpl
    }
}

class ServerPinHasherAdapter : PinHasherPort {
    override fun hash(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

class InterceptorsOfdManager(
    private val delegate: io.github.texport.superkassa.core.domain.api.port.internal.OfdManagerPort
) : io.github.texport.superkassa.core.domain.api.port.internal.OfdManagerPort {
    override fun send(
        command: io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandRequest
    ): io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandResult {
        val result = delegate.send(command)
        if (result.status != io.github.texport.superkassa.core.domain.api.model.ofd.OfdCommandStatus.OK) {
            return result
        }
        val responseJson = result.responseJson ?: return result
        val payload = responseJson["payload"] as? kotlinx.serialization.json.JsonObject ?: return result
        val ticket = payload["ticket"] as? kotlinx.serialization.json.JsonObject ?: return result

        // Извлекаем ticketNumber (fiscalSign), если он равен null
        var finalFiscalSign = result.fiscalSign
        if (finalFiscalSign == null) {
            finalFiscalSign = ticket["ticketNumber"]?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
            }
        }

        // Извлекаем receiptUrl
        var finalReceiptUrl = result.receiptUrl
        if (finalReceiptUrl == null) {
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

        if (finalReceiptUrl != null && (finalFiscalSign != result.fiscalSign || finalReceiptUrl != result.receiptUrl)) {
            return result.copy(
                fiscalSign = finalFiscalSign,
                receiptUrl = finalReceiptUrl
            )
        }
        return result
    }
}
