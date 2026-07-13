package kz.mybrain.superkassa.core.application.measurement

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.domain.api.model.common.UnitOfMeasurement
import io.github.texport.superkassa.core.presentation.api.model.common.PaginatedResponse
import io.github.texport.superkassa.core.presentation.api.model.common.UnitOfMeasurementResponse
import io.github.texport.superkassa.core.string.api.CoreStrings
import org.springframework.stereotype.Service

@Service
class UnitsApplicationService {

    private val entries = UnitOfMeasurement.entries.filter { it != UnitOfMeasurement.UNKNOWN }

    fun list(limit: Int, offset: Int, search: String?): PaginatedResponse<UnitOfMeasurementResponse> {
        val validLimit = limit.coerceIn(1, 100)
        val validOffset = offset.coerceAtLeast(0)
        val filtered = if (search.isNullOrBlank()) {
            entries
        } else {
            val q = search.trim().lowercase()
            entries.filter {
                it.code == q ||
                    it.shortRus.lowercase().contains(q) ||
                    it.shortKaz.lowercase().contains(q) ||
                    it.nameRus.lowercase().contains(q) ||
                    it.nameKaz.lowercase().contains(q)
            }
        }
        val total = filtered.size
        val page = filtered.drop(validOffset).take(validLimit)
        return PaginatedResponse(
            items = page.map { uom ->
                UnitOfMeasurementResponse(
                    code = uom.code,
                    nameShort = uom.shortRus,
                    nameFull = uom.nameRus
                )
            },
            total = total,
            limit = validLimit,
            offset = validOffset,
            hasMore = validOffset + validLimit < total
        )
    }

    fun getByCode(code: String): UnitOfMeasurementResponse {
        val uom = entries.find { it.code == code.trim() }
            ?: throw NotFoundException(CoreStrings.measureUnitNotFound(code), "UNIT_NOT_FOUND")
        return UnitOfMeasurementResponse(
            code = uom.code,
            nameShort = uom.shortRus,
            nameFull = uom.nameRus
        )
    }
}
