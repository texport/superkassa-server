package kz.mybrain.superkassa.core.application.http.validation

import kz.mybrain.superkassa.core.domain.exception.TrilingualMessage
import kz.mybrain.superkassa.core.domain.exception.ValidationException

/**
 * Проверяет соответствие kkmId из path и body запроса.
 * Выбрасывает ValidationException, если значения не совпадают.
 */
fun requireKkmIdMatch(pathId: String, bodyId: String) {
    if (pathId != bodyId) {
        throw ValidationException(
            trilingualMessage = TrilingualMessage(
                ru = "kkmId в пути и теле запроса должны совпадать",
                kk = "Жолдағы және сұраныс денесіндегі kkmId сәйкес келуі керек",
                en = "Path kkmId must match body kkmId"
            )
        )
    }
}
