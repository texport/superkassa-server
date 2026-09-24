package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.domain.api.model.settings.withDeploymentOwned
import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.QrCodeGeneratorPort
import io.github.texport.superkassa.core.domain.api.port.integration.TimeValidatorPort
import io.github.texport.superkassa.jvm.receipt.impl.DocumentConvertAdapter
import io.github.texport.superkassa.jvm.receipt.impl.QrCodeDataUriGenerator
import io.github.texport.superkassa.jvm.settings.impl.FileCoreSettingsRepository
import io.github.texport.superkassa.jvm.settings.impl.dto.CoreSettingsDto
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto
import io.github.texport.superkassa.jvm.storage.impl.adapter.DatabaseCoreSettingsRepository
import io.github.texport.superkassa.jvm.storage.impl.adapter.StorageAdapter
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.DefaultStorageConnectorRegistry
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.jvm.time.impl.SystemClock
import io.github.texport.superkassa.jvm.time.impl.SystemTimeGuard
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdaptersConfig {

    private val logger = LoggerFactory.getLogger(AdaptersConfig::class.java)

    @Value("\${app.log-language:ALL}")
    private lateinit var logLanguage: String

    @PostConstruct
    fun init() {
        System.setProperty("superkassa.log.language", logLanguage)
        logger.info("Установлен системный язык логирования: {}", logLanguage)
    }

    /**
     * Рабочее место узла создаётся раньше хранилища настроек: оно запоминает,
     * лежали ли настройки на месте до того, как узел запишет умолчания.
     */
    @Bean
    fun nodeHome(@Value("\${superkassa.home:}") configured: String): NodeHome {
        val home = NodeHome.of(configured)
        logger.info(
            "Node workspace: {} (settings {})",
            home.dir,
            if (home.settingsFound) "found" else "absent, first start"
        )
        return home
    }

    @Bean
    fun settingsRepository(
        home: NodeHome,
        @Value("\${spring.datasource.url:}") dbUrl: String,
        @Value("\${spring.datasource.username:}") dbUser: String?,
        @Value("\${spring.datasource.password:}") dbPass: String?
    ): CoreSettingsRepositoryPort {
        val urlLower = dbUrl.lowercase()
        return if (urlLower.startsWith("jdbc:postgresql:") || urlLower.startsWith("jdbc:mysql:")) {
            DatabaseCoreSettingsRepository(jdbcUrl = dbUrl, user = dbUser, password = dbPass)
        } else {
            FileCoreSettingsRepository(home.settingsFile)
        }
    }

    @Bean
    fun coreSettings(
        repository: CoreSettingsRepositoryPort,
        @Value("\${spring.datasource.url:}") dbUrl: String,
        @Value("\${spring.datasource.username:}") dbUser: String?,
        @Value("\${spring.datasource.password:}") dbPass: String?,
        @Value("\${superkassa.ofd-provider-id:KAZAKHTELECOM}") ofdProviderId: String,
        // Умолчание — 2.0.4: это действующая версия CPCR, по которой узел
        // и сервис приёма разговаривают. Прежнее 203 осталось со времён,
        // когда кодека 2.0.4 для БФД ещё не было, и узел молча поднимался
        // на прошлой версии, пока запуск не передавал свойство.
        @Value("\${superkassa.ofd-protocol-version:204}") ofdProtocolVersion: String
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
                allowChanges = true,
                ofdProviderId = ofdProviderId,
                ofdProtocolVersion = ofdProtocolVersion
            )
        } else {
            CoreSettings(
                mode = CoreMode.DESKTOP,
                storage = StorageSettings(
                    engine = "SQLITE",
                    jdbcUrl = dbUrl.ifEmpty { "jdbc:sqlite:data/core.db?busy_timeout=30000" }
                ),
                allowChanges = true,
                ofdProviderId = ofdProviderId,
                ofdProtocolVersion = ofdProtocolVersion
            )
        }
        // Какой ОФД обслуживает узел — решает развёртывание, а не сохранённая
        // запись: сменить провайдера правкой базы нельзя, а перезапуском с другим
        // свойством нужно. Перечень таких полей объявлен один раз.
        return repository.loadOrCreate(defaults).withDeploymentOwned(defaults)
    }

    @Bean
    fun coreSettingsDto(coreSettings: CoreSettings): CoreSettingsDto {
        return coreSettings.toDto()
    }

    /**
     * Путь к базе привязывается к рабочему месту здесь, один раз: дальше
     * коннектор, миграции и диагностика видят уже абсолютный адрес.
     */
    @Bean
    fun storageConfig(settings: CoreSettings, home: NodeHome): StorageConfig {
        val jdbcUrl = home.resolveJdbcUrl(settings.storage.jdbcUrl)
        runCatching { home.requireDatabase(jdbcUrl) }
            .onFailure { logger.error("Storage check failed: {}", it.message) }
            .getOrThrow()
        return StorageConfig(
            jdbcUrl,
            null,
            settings.storage.user,
            settings.storage.password
        )
    }

    @Bean
    fun storageHealthChecker(): StorageHealthChecker {
        return StorageHealthChecker(DefaultStorageConnectorRegistry())
    }

    /** Хранилище узла; оно же ведёт счёт неверных пинов в своей базе. */
    @Bean
    fun storagePort(config: StorageConfig): StorageAdapter {
        val storageBootstrap = DefaultStorageBootstrap()
        logger.info("Connecting to storage: ${config.jdbcUrl}")
        storageBootstrap.migrate(config)
        return StorageAdapter(storageBootstrap, config)
    }

    @Bean
    fun qrCodeGeneratorPort(): QrCodeGeneratorPort = QrCodeDataUriGenerator

    @Bean
    fun documentConvertPort(): DocumentConvertPort = DocumentConvertAdapter()

    @Bean
    fun timeValidatorPort(): TimeValidatorPort = SystemTimeGuard

    @Bean
    fun clockPort(): ClockPort = SystemClock
}
