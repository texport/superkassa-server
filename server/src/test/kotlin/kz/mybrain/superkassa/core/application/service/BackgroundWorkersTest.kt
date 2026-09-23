package kz.mybrain.superkassa.core.application.service

import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.core.presentation.api.OfflineQueueApi
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryStatus
import io.github.texport.superkassa.core.presentation.api.model.shift.ReportResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Фоновые работы узла обходят все кассы страницами, и отказ на одной
 * кассе не оставляет без обработки следующие.
 */
class BackgroundWorkersTest {
    private val storage = mockk<StoragePort>()
    private val api = mockk<SuperkassaApi>()
    private val pages = KkmPages(storage)

    @Test
    fun `обход проходит все страницы касс от старых к новым`() {
        registered(KkmPages.PAGE_SIZE * 2 + 1)
        val seen = mutableListOf<String>()

        pages.forEach { seen += it.id }

        assertEquals((0..KkmPages.PAGE_SIZE * 2).map { "kkm-$it" }, seen)
    }

    @Test
    fun `ровно полная страница касс дочитывается пустой`() {
        registered(KkmPages.PAGE_SIZE)
        var count = 0

        pages.forEach { count++ }

        assertEquals(KkmPages.PAGE_SIZE, count)
        verify(exactly = 1) { storage.listKkms(KkmPages.PAGE_SIZE, KkmPages.PAGE_SIZE, null, null, "createdAt", "ASC") }
    }

    @Test
    fun `досылка доходит до сто первой кассы и переживает отказ одной`() {
        registered(KkmPages.PAGE_SIZE + 1)
        val queue = mockk<OfflineQueueApi>()
        every { api.queue } returns queue
        every { queue.processOfflineBatch(any(), any()) } returns 1
        every { queue.processOfflineBatch("kkm-3", any()) } throws IllegalStateException("BFD is silent")

        QueueWorker(api, pages).processOfflineQueues()

        verify(exactly = 1) { queue.processOfflineBatch("kkm-${KkmPages.PAGE_SIZE}", 5) }
        verify(exactly = KkmPages.PAGE_SIZE + 1) { queue.processOfflineBatch(any(), 5) }
    }

    @Test
    fun `автозакрытие спрашивает ядро о каждой кассе и переживает отказ одной`() {
        registered(3)
        every { api.autoCloseShift(any()) } returns null
        every { api.autoCloseShift("kkm-0") } throws IllegalStateException("storage failure")
        every { api.autoCloseShift("kkm-1") } returns report()

        ShiftAutoCloser(api, pages).closeDueShifts()

        verify(exactly = 1) { api.autoCloseShift("kkm-2") }
        verify(exactly = 3) { api.autoCloseShift(any()) }
    }

    private fun registered(total: Int) {
        val all = (0 until total).map { KkmInfo(id = "kkm-$it", createdAt = it.toLong(), updatedAt = 0L, mode = "", state = "ACTIVE") }
        every { storage.listKkms(any(), any(), null, null, "createdAt", "ASC") } answers {
            val limit = firstArg<Int>()
            val offset = secondArg<Int>()
            all.drop(offset).take(limit)
        }
    }

    private fun report() = mockk<ReportResponse> { every { deliveryStatus } returns DeliveryStatus.ONLINE_OK }
}
