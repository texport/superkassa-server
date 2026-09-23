package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import org.springframework.stereotype.Component

/**
 * Все кассы узла, страница за страницей.
 *
 * Фоновые работы узла обходят все кассы, а не первую страницу: досылка
 * очереди брала первые сто касс, и чеки сто первой до ОФД не доходили.
 * Порядок — от старых к новым: касса, заведённая во время обхода,
 * ложится в конец и не сдвигает ещё не пройденные страницы.
 */
@Component
class KkmPages(private val storage: StoragePort) {

    /** Вызывает [action] для каждой кассы; отказ на одной кассе обход не прерывает — это забота [action]. */
    fun forEach(action: (KkmInfo) -> Unit) {
        var offset = 0
        do {
            val page = storage.listKkms(
                limit = PAGE_SIZE,
                offset = offset,
                state = null,
                search = null,
                sortBy = "createdAt",
                sortOrder = "ASC"
            )
            page.forEach(action)
            offset += page.size
        } while (page.size == PAGE_SIZE)
    }

    internal companion object {
        /** Предел страницы касс у ядра — 1000; обход идёт страницами поменьше. */
        const val PAGE_SIZE = 100
    }
}
