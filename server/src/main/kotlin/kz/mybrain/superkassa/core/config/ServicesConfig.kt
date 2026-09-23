package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.data.api.SuperkassaCoreEngine
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.QrCodeGeneratorPort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import io.github.texport.superkassa.core.presentation.api.DeliveryApi
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.jvm.settings.impl.SettingsApplicationService
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import kz.mybrain.superkassa.core.application.time.ValidateSystemTimeOnStartupUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class ServicesConfig {

    /**
     * Сборка ядра — одна на узел: фасад, доставка и рисовальщик берутся
     * из неё, а не собираются второй раз рядом. Счёт неверных пинов — та же
     * база, что и хранилище: блокировка пина переживает перезапуск узла.
     */
    @Bean
    fun superkassaCoreEngine(
        storage: StorageAdapter,
        settingsRepository: CoreSettingsRepositoryPort,
        delivery: DeliveryPort,
        clock: ClockPort,
        timeValidator: TimeValidatorPort,
        qrCodeGenerator: QrCodeGeneratorPort,
        documentConvertPort: DocumentConvertPort
    ): SuperkassaCoreEngine = SuperkassaCoreEngine(
        storage = storage,
        pinAttempts = storage.pinAttempts,
        settings = settingsRepository,
        delivery = delivery,
        clock = clock,
        timeValidator = timeValidator,
        qrCode = qrCodeGenerator,
        pdfConverter = documentConvertPort
    )

    @Bean
    fun kkmService(engine: SuperkassaCoreEngine, coreSettings: CoreSettings): SuperkassaApi =
        engine.buildApi(
            ownerId = coreSettings.nodeId,
            ofdProviderId = coreSettings.ofdProviderId,
            ofdProtocolVersion = coreSettings.ofdProtocolVersion
        )

    @Bean
    fun validateSystemTimeOnStartupUseCase(
        timeValidator: TimeValidatorPort,
        clock: ClockPort
    ): ValidateSystemTimeOnStartupUseCase =
        ValidateSystemTimeOnStartupUseCase(timeValidator, clock)

    /** Настройки узла по правилам ядра; ОФД и версию протокола задаёт запуск. */
    @Bean
    fun settingsApplicationService(engine: SuperkassaCoreEngine, coreSettings: CoreSettings): SettingsApplicationService =
        SettingsApplicationService(
            engine.buildSettingsApi(
                ofdProviderId = coreSettings.ofdProviderId,
                ofdProtocolVersion = coreSettings.ofdProtocolVersion
            )
        )

    /**
     * Повтор доставки чека: пин проверяется тем же счётом неверных пинов,
     * что и у фасада, — перебор не делится между входами. Фасад строится
     * раньше: доставка читает настройки, которые он заводит.
     */
    @Bean
    @DependsOn("kkmService")
    fun deliveryApi(engine: SuperkassaCoreEngine): DeliveryApi = engine.buildDeliveryApi()
}
