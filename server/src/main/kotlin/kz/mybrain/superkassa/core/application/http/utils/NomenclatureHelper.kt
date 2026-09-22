package kz.mybrain.superkassa.core.application.http.utils

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.domain.api.exception.SuperkassaException
import io.github.texport.superkassa.core.domain.api.exception.ValidationException
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupRequest
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupResponse
import io.github.texport.superkassa.core.string.api.CoreStrings
import io.github.texport.superkassa.core.string.api.TrilingualMessage

/**
 * Вспомогательный класс для работы с номенклатурой, вынесенный из слоя контроллеров
 * для строгого соблюдения архитектурного разделения и предотвращения прямых
 * зависимостей контроллеров от доменного слоя.
 */
object NomenclatureHelper {
    /**
     * Ищет позицию в справочнике и различает три исхода.
     *
     * Прежде исход был один: любое «не нашли» уходило кассе как 404, и она
     * писала кассиру «в справочнике нет такого штрихкода». Заблокированная
     * касса и молчащий БФД выглядели так же, как честное отсутствие товара,
     * и кассир шёл искать несуществующую беду с товаром вместо настоящей.
     *
     * Различие берётся из ответа каталога: нулевой код результата означает,
     * что каталог ответил и позиции у него нет, а ненулевой — что спросить
     * не удалось. Блокировка кассы видна ещё до обращения.
     *
     * @throws ValidationException касса заблокирована.
     * @throws NomenclatureUnavailableException справочник спросить не удалось.
     * @throws NotFoundException каталог ответил, и позиции в нём нет.
     */
    fun lookupNomenclature(
        service: SuperkassaApi,
        pin: String,
        request: NomenclatureLookupRequest
    ): NomenclatureLookupResponse {
        val kkm = service.getKkm(request.kkmId)
        if (kkm.state == BLOCKED_STATE) {
            throw ValidationException(CoreStrings.kkmBlocked(kkm.blockReasonCode), KKM_BLOCKED)
        }
        val response = service.lookupNomenclature(pin, request)
        if (response.found) {
            return response
        }
        if (response.resultCode != CATALOGUE_ANSWERED) {
            throw NomenclatureUnavailableException()
        }
        throw NotFoundException(CoreStrings.nomenclatureNotFound(request.barcode), NOMENCLATURE_NOT_FOUND)
    }
}

/**
 * Справочник спросить не удалось: связи нет, БФД молчит или отвечает отказом.
 *
 * Отдельный код, а не 404: по нему касса говорит кассиру, что товар мог
 * быть заведён, и предлагает ввести позицию руками, вместо того чтобы
 * уверять, будто такого штрихкода не существует.
 */
class NomenclatureUnavailableException : SuperkassaException(
    code = NOMENCLATURE_UNAVAILABLE,
    status = SERVICE_UNAVAILABLE,
    trilingualMessage = CATALOGUE_SILENT
)

private val CATALOGUE_SILENT = TrilingualMessage(
    ru = "Справочник сейчас недоступен: ответа от БФД нет",
    kk = "Анықтамалық қазір қолжетімсіз: БФД жауап бермеді",
    en = "The catalogue is unavailable right now: the BFD gave no answer"
)

/** Каталог ответил по существу: код результата нулевой. */
private const val CATALOGUE_ANSWERED = 0

private const val BLOCKED_STATE = "BLOCKED"
private const val KKM_BLOCKED = "KKM_BLOCKED"
private const val NOMENCLATURE_NOT_FOUND = "NOMENCLATURE_NOT_FOUND"
private const val NOMENCLATURE_UNAVAILABLE = "NOMENCLATURE_UNAVAILABLE"
private const val SERVICE_UNAVAILABLE = 503
