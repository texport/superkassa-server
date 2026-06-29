package kz.mybrain.superkassa.core.config

import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.data.adapter.SystemClock
import kz.mybrain.superkassa.core.data.adapter.UuidGeneratorAdapter
import kz.mybrain.superkassa.core.data.adapter.OfdQueueCommandHandlerAdapter
import kz.mybrain.superkassa.core.domain.helper.KkmCommonHelper
import kz.mybrain.superkassa.core.domain.usecase.auth.AuthorizeUserUseCase
import kz.mybrain.superkassa.core.domain.usecase.ofd.GenerateRequestNumberUseCase
import kz.mybrain.superkassa.core.domain.helper.ofd.OfdCommandRequestFactory
import kz.mybrain.superkassa.core.domain.usecase.ofd.SendFiscalCommandUseCase
import kz.mybrain.superkassa.core.domain.usecase.queue.ListQueueItemsUseCase
import kz.mybrain.superkassa.core.domain.usecase.queue.RetryFailedQueueItemsUseCase
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApiImpl
import kz.mybrain.superkassa.core.domain.port.*
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServicesConfig {

    @Bean
    fun authorizeUserUseCase(storage: StoragePort, pinHasher: PinHasherPort): AuthorizeUserUseCase =
        AuthorizeUserUseCase(storage, pinHasher)

    @Bean
    fun generateRequestNumberUseCase(storage: StoragePort): GenerateRequestNumberUseCase =
        GenerateRequestNumberUseCase(storage)

    @Bean
    fun ofdCommandRequestFactory(ofdConfig: OfdConfigPort): OfdCommandRequestFactory =
        OfdCommandRequestFactory(ofdConfig)

    @Bean
    fun kkmCommonHelper(
        storage: StoragePort,
        timeValidator: TimeValidatorPort,
        tokenCodec: TokenCodecPort,
        generateRequestNumberUseCase: GenerateRequestNumberUseCase,
        ofdCommandRequestFactory: OfdCommandRequestFactory,
        ofd: OfdManagerPort
    ): KkmCommonHelper =
        KkmCommonHelper(
            storage = storage,
            clock = SystemClock,
            timeValidator = timeValidator,
            tokenCodec = tokenCodec,
            generateRequestNumberUseCase = generateRequestNumberUseCase,
            ofdCommandRequestFactory = ofdCommandRequestFactory,
            ofd = ofd
        )

    @Bean
    fun sendFiscalCommandUseCase(
        authorizeUserUseCase: AuthorizeUserUseCase,
        kkmCommonHelper: KkmCommonHelper
    ): SendFiscalCommandUseCase =
        SendFiscalCommandUseCase(authorizeUserUseCase, kkmCommonHelper)

    @Bean
    fun queueCommandHandler(
        sendFiscalCommandUseCase: SendFiscalCommandUseCase,
        storagePort: StoragePort
    ): QueueCommandHandler =
        OfdQueueCommandHandlerAdapter(
            sendFiscalCommand = sendFiscalCommandUseCase,
            storage = storagePort,
            clock = SystemClock
        )

    @Bean
    fun listQueueItemsUseCase(
        queueStorage: QueueStoragePort,
        authorizeUserUseCase: AuthorizeUserUseCase
    ): ListQueueItemsUseCase =
        ListQueueItemsUseCase(queueStorage, authorizeUserUseCase)

    @Bean
    fun retryFailedQueueItemsUseCase(
        storage: StoragePort,
        queueStorage: QueueStoragePort,
        authorizeUserUseCase: AuthorizeUserUseCase
    ): RetryFailedQueueItemsUseCase =
        RetryFailedQueueItemsUseCase(storage, queueStorage, authorizeUserUseCase)

    @Bean
    fun kkmService(
        storage: StoragePort,
        queue: OfflineQueuePort,
        ofd: OfdManagerPort,
        ofdConfig: OfdConfigPort,
        delivery: DeliveryPort,
        tokenCodec: TokenCodecPort,
        pinHasher: PinHasherPort,
        coreSettings: CoreSettings,
        receiptRenderPort: ReceiptRenderPort,
        documentConvertPort: DocumentConvertPort,
        timeValidator: TimeValidatorPort
    ): SuperkassaApi {
        return SuperkassaApiImpl(
            storage = storage,
            queue = queue,
            ofd = ofd,
            ofdConfig = ofdConfig,
            delivery = delivery,
            tokenCodec = tokenCodec,
            idGenerator = UuidGeneratorAdapter,
            clock = SystemClock,
            pinHasher = pinHasher,
            coreSettings = coreSettings,
            receiptRenderPort = receiptRenderPort,
            documentConvertPort = documentConvertPort,
            timeValidator = timeValidator
        )
    }
}
