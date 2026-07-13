package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmListParams
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.OfdCommandStatus
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_OK
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для диагностики системы.
 * Предоставляет эндпоинты для проверки работоспособности компонентов системы.
 */
@RestController
@RequestMapping("/")
@Tag(name = "Диагностика", description = "Проверка работоспособности и состояния Superkassa.")
class DiagnosticsController(
    private val storageHealthChecker: StorageHealthChecker,
    private val storageConfig: StorageConfig,
    private val kkmService: SuperkassaApi? = null
) {

    /**
     * Проверка работоспособности сервиса (Health Check).
     *
     * Проверяет:
     * - Доступность базы данных (обязательно)
     * - Доступность ОФД (опционально, если указан параметр checkOfd=true и есть зарегистрированные ККМ)
     *
     * Возвращает:
     * - HTTP 200 OK, если все проверки прошли успешно
     * - HTTP 503 Service Unavailable, если хотя бы одна проверка не прошла
     */
    @GetMapping("/health")
    @Operation(
        summary = "Проверка здоровья сервиса",
        description = """
            Проверяет работоспособность компонентов системы Superkassa Core.

            Обязательные проверки:
            - Доступность базы данных (storage) - проверяется всегда

            Опциональные проверки (если указан параметр checkOfd=true):
            - Доступность ОФД - проверяется для всех уникальных комбинаций ОФД провайдер + окружение
               Например: "KAZAKHTELECOM:TEST", "KAZAKHTELECOM:PROD", "OTHER_OFD:PROD"
            - Для каждой уникальной комбинации выбирает одну активную ККМ (если есть)
            - Отправляет команду COMMAND_SYSTEM в соответствующий ОФД через метод checkOfdConnection()
            - Команда отправляется через HTTP-запрос к эндпоинту ОФД с использованием настроек
               кассы (ofdId, ofdToken, ofdSystemId) и протокола версии ofdProtocolVersion
            - Результаты проверки группируются по комбинациям ОФД+окружение:
               - "OK" - если ОФД успешно ответил (статус OfdCommandStatus.OK)
               - "DEGRADED: <описание ошибки>" - если ОФД недоступен или вернул ошибку
               - "SKIPPED: no active KKM" - если нет активных ККМ для данной комбинации
               - "ERROR: <сообщение>" - если произошла исключительная ситуация
        """
    )
    @KkmApiResponses(ok = MSG_200_OK)
    fun health(
        @RequestParam(required = false) checkOfd: Boolean = false,
        @RequestParam(required = false) ofdEnvironment: String? = null,
        @RequestParam(required = false) ofdProvider: String? = null
    ): ResponseEntity<Map<String, Any>> {
        val status = mutableMapOf<String, Any>()
        val storageStatus = storageHealthChecker.check(storageConfig, timeoutSeconds = 3)
        status["storage"] = storageStatus.message
        var isHealthy = storageStatus.ok

        if (checkOfd && kkmService != null) {
            val ofdHealthy = checkOfdHealth(kkmService, ofdEnvironment, ofdProvider, status)
            if (!ofdHealthy) {
                isHealthy = false
            }
        }

        return if (isHealthy) {
            status["status"] = "OK"
            ResponseEntity.ok(status)
        } else {
            status["status"] = "DEGRADED"
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status)
        }
    }

    private fun checkOfdHealth(
        kkmService: SuperkassaApi,
        ofdEnvironment: String?,
        ofdProvider: String?,
        status: MutableMap<String, Any>
    ): Boolean {
        val kkms = try {
            kkmService.listKkms(
                KkmListParams(
                    limit = 1000,
                    offset = 0
                )
            )
        } catch (ex: Exception) {
            status["ofd"] = "ERROR: ${ex.message}"
            return false
        }

        if (kkms.items.isEmpty()) {
            status["ofd"] = "SKIPPED: no KKM registered"
            return true
        }

        val ofdGroups = getOfdGroupsToCheck(kkms.items, ofdProvider, ofdEnvironment)
        val ofdResults = mutableMapOf<String, String>()
        var allOfdHealthy = true

        for ((providerTag, kkm) in ofdGroups) {
            val groupHealthy = checkSingleGroupOfdConnection(kkmService, providerTag, kkm, ofdResults)
            if (!groupHealthy) {
                allOfdHealthy = false
            }
        }

        if (ofdGroups.isEmpty()) {
            status["ofd"] = "SKIPPED: no matching KKM found for filters"
        } else {
            status["ofd"] = ofdResults
        }
        return allOfdHealthy
    }

    private fun getOfdGroupsToCheck(
        items: List<KkmResponse>,
        ofdProvider: String?,
        ofdEnvironment: String?
    ): List<Pair<String, KkmResponse>> {
        return items
            .filter { it.ofdId != null }
            .groupBy { "${it.ofdId}:${it.ofdEnvironment ?: ""}" }
            .mapNotNull { (providerTag, kkmList) ->
                val (ofdId, ofdEnv) = splitOfdTag(providerTag)
                val matchesProvider = ofdProvider == null ||
                    (ofdId != null && ofdId.equals(ofdProvider, ignoreCase = true))
                val matchesEnvironment = ofdEnvironment == null ||
                    (ofdEnv != null && ofdEnv.equals(ofdEnvironment, ignoreCase = true))
                if (matchesProvider && matchesEnvironment) {
                    val activeKkm = kkmList.firstOrNull { it.state == "ACTIVE" } ?: kkmList.first()
                    Pair(providerTag, activeKkm)
                } else {
                    null
                }
            }
    }

    private fun checkSingleGroupOfdConnection(
        kkmService: SuperkassaApi,
        providerTag: String,
        kkm: KkmResponse,
        ofdResults: MutableMap<String, String>
    ): Boolean {
        return try {
            val ofdResult = kkmService.checkOfdConnection(kkm.kkmId)
            val isOk = ofdResult.status == OfdCommandStatus.OK
            ofdResults[providerTag] = if (isOk) {
                "OK"
            } else {
                "DEGRADED: ${ofdResult.errorMessage ?: "Unknown error"}"
            }
            isOk
        } catch (ex: Exception) {
            ofdResults[providerTag] = "ERROR: ${ex.message ?: ex.toString()}"
            false
        }
    }

    private fun splitOfdTag(tag: String?): Pair<String?, String?> {
        if (tag == null) return null to null
        val parts = tag.split(":")
        return if (parts.size == 2) parts[0] to parts[1] else tag to null
    }
}
