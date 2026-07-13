package kz.mybrain.superkassa.core.application.http.utils

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupRequest
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupResponse
import io.github.texport.superkassa.core.string.api.CoreStrings

/**
 * Вспомогательный класс для работы с номенклатурой, вынесенный из слоя контроллеров
 * для строгого соблюдения архитектурного разделения и предотвращения прямых
 * зависимостей контроллеров от доменного слоя.
 */
object NomenclatureHelper {
    /**
     * Выполняет поиск номенклатуры и выбрасывает [NotFoundException] при отсутствии.
     */
    fun lookupNomenclature(
        service: SuperkassaApi,
        pin: String,
        request: NomenclatureLookupRequest
    ): NomenclatureLookupResponse {
        val response = service.lookupNomenclature(pin, request)
        if (!response.found) {
            throw NotFoundException(CoreStrings.nomenclatureNotFound(request.barcode))
        }
        return response
    }
}
