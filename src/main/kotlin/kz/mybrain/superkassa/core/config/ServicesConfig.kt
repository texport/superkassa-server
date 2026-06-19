package kz.mybrain.superkassa.core.config

import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.application.policy.DefaultCounterUpdater
import kz.mybrain.superkassa.core.application.policy.SystemClock
import kz.mybrain.superkassa.core.application.policy.UuidGenerator
import kz.mybrain.superkassa.core.application.service.AuthorizationService
import kz.mybrain.superkassa.core.application.service.AutonomousModeService
import kz.mybrain.superkassa.core.application.service.FiscalOperationExecutor
import kz.mybrain.superkassa.core.application.service.KkmRegistrationService
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.application.service.KkmUserService
import kz.mybrain.superkassa.core.application.service.OfdCommandRequestBuilder
import kz.mybrain.superkassa.core.application.service.OfdQueueCommandHandler
import kz.mybrain.superkassa.core.application.service.OfdSyncService
import kz.mybrain.superkassa.core.application.service.QueueManagementService
import kz.mybrain.superkassa.core.application.service.ReqNumService
import kz.mybrain.superkassa.core.application.service.ShiftService
import kz.mybrain.superkassa.core.data.adapter.Sha256PinHasherPort
import kz.mybrain.superkassa.core.domain.port.*
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class ServicesConfig {

    @Bean
    fun queueCommandHandler(
        ofdSyncService: OfdSyncService,
        storagePort: StoragePort
    ): QueueCommandHandler =
        OfdQueueCommandHandler(
            ofdSyncService = ofdSyncService,
            storage = storagePort,
            clock = SystemClock
        )

    @Bean
    fun authorizationService(storage: StoragePort, pinHasher: PinHasherPort): AuthorizationService =
        AuthorizationService(storage, pinHasher)

    @Bean
    fun kkmUserService(
        storage: StoragePort,
        pinHasher: PinHasherPort,
        authorization: AuthorizationService
    ): KkmUserService =
        KkmUserService(storage, UuidGenerator, SystemClock, pinHasher, authorization)

    @Bean
    fun ofdCommandRequestBuilder(ofdConfig: OfdConfigPort): OfdCommandRequestBuilder =
        OfdCommandRequestBuilder(ofdConfig)

    @Bean
    fun autonomousModeService(
        storage: StoragePort,
        @Lazy queue: OfflineQueuePort
    ): AutonomousModeService =
        AutonomousModeService(
            storage = storage,
            queue = queue,
            clock = SystemClock
        )

    @Bean
    fun fiscalOperationExecutor(
        storage: StoragePort,
        authorization: AuthorizationService
    ): FiscalOperationExecutor =
        FiscalOperationExecutor(
            storage = storage,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization
        )

    @Bean
    fun reqNumService(storage: StoragePort): ReqNumService =
        ReqNumService(storage = storage)

    @Bean
    fun ofdSyncService(
        storage: StoragePort,
        @Lazy queue: OfflineQueuePort,
        ofd: OfdManagerPort,
        authorization: AuthorizationService,
        ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        tokenCodec: TokenCodecPort,
        autonomousModeService: AutonomousModeService,
        reqNumService: ReqNumService,
        timeValidator: TimeValidatorPort
    ): OfdSyncService =
        OfdSyncService(
            storage = storage,
            queue = queue,
            ofd = ofd,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            tokenCodec = tokenCodec,
            autonomousModeService = autonomousModeService,
            reqNumService = reqNumService,
            timeValidator = timeValidator
        )

    @Bean
    fun shiftService(
        storage: StoragePort,
        queue: OfflineQueuePort,
        ofdSyncService: OfdSyncService,
        authorization: AuthorizationService
    ): ShiftService =
        ShiftService(
            storage = storage,
            queue = queue,
            ofdSyncService = ofdSyncService,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            authorization = authorization
        )

    @Bean
    fun kkmRegistrationService(
        storage: StoragePort,
        ofd: OfdManagerPort,
        ofdConfig: OfdConfigPort,
        tokenCodec: TokenCodecPort,
        authorization: AuthorizationService,
        ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        reqNumService: ReqNumService,
        timeValidator: TimeValidatorPort
    ): KkmRegistrationService =
        KkmRegistrationService(
            storage = storage,
            ofd = ofd,
            ofdConfig = ofdConfig,
            tokenCodec = tokenCodec,
            idGenerator = UuidGenerator,
            clock = SystemClock,
            pinHasher = Sha256PinHasherPort(),
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            reqNumService = reqNumService,
            counters = DefaultCounterUpdater(storage),
            timeValidator = timeValidator
        )

    @Bean
    fun kkmService(
        storage: StoragePort,
        queue: OfflineQueuePort,
        ofd: OfdManagerPort,
        ofdConfig: OfdConfigPort,
        delivery: DeliveryPort,
        kkmUserService: KkmUserService,
        shiftService: ShiftService,
        ofdSyncService: OfdSyncService,
        kkmRegistrationService: KkmRegistrationService,
        tokenCodec: TokenCodecPort,
        autonomousModeService: AutonomousModeService,
        fiscalOperationExecutor: FiscalOperationExecutor,
        reqNumService: ReqNumService,
        pinHasher: PinHasherPort,
        authorization: AuthorizationService,
        ofdCommandRequestBuilder: OfdCommandRequestBuilder,
        coreSettings: CoreSettings,
        receiptRenderPort: ReceiptRenderPort,
        documentConvertPort: DocumentConvertPort,
        timeValidator: TimeValidatorPort
    ): KkmService {
        return KkmService(
            storage = storage,
            queue = queue,
            ofd = ofd,
            ofdConfig = ofdConfig,
            delivery = delivery,
            kkmUserService = kkmUserService,
            shiftService = shiftService,
            ofdSyncService = ofdSyncService,
            kkmRegistrationService = kkmRegistrationService,
            tokenCodec = tokenCodec,
            autonomousModeService = autonomousModeService,
            fiscalOperationExecutor = fiscalOperationExecutor,
            reqNumService = reqNumService,
            counters = DefaultCounterUpdater(storage),
            idGenerator = UuidGenerator,
            clock = SystemClock,
            pinHasher = pinHasher,
            authorization = authorization,
            ofdCommandRequestBuilder = ofdCommandRequestBuilder,
            coreSettings = coreSettings,
            receiptRenderPort = receiptRenderPort,
            documentConvertPort = documentConvertPort,
            timeValidator = timeValidator
        )
    }

    @Bean
    fun queueManagementService(
        storage: StoragePort,
        queuePort: OfflineQueuePort,
        queueStoragePort: QueueStoragePort,
        authorization: AuthorizationService
    ): QueueManagementService =
        QueueManagementService(
            storage = storage,
            queuePort = queuePort,
            queueStorage = queueStoragePort,
            authorization = authorization
        )
}
