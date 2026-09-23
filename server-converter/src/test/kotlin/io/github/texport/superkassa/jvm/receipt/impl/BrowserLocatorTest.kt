package io.github.texport.superkassa.jvm.receipt.impl

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Поиск браузера для образов документов.
 *
 * Прежде узел знал одно имя — `chromium-browser`, — и на Linux с пакетом
 * `chromium`, как и на Windows, просмотр и печать не работали. Здесь
 * проверяется, что каждая система получает свои места поиска, явный
 * путь идёт первым, а отсутствие браузера названо своим кодом.
 */
class BrowserLocatorTest {

    private val home = Files.createTempDirectory("home").toFile()

    /** Корень файловой системы поиска: браузер машины проверки в неё не попадает. */
    private val root = Files.createTempDirectory("root").toFile()

    private fun locator(
        os: String,
        env: Map<String, String> = emptyMap(),
        setting: String? = null
    ) = BrowserLocator(os = os, home = home.absolutePath, env = { env[it] }, setting = { setting }, root = root.absolutePath)

    @Test
    fun `на macOS ищется в Applications системы и владельца`() {
        val places = locator("Mac OS X").candidates().map { it.path }
        assertTrue(places.any { it == "${root.absolutePath}/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" }, places.toString())
        assertTrue(places.any { it.startsWith(home.absolutePath) && it.endsWith("Microsoft Edge") }, places.toString())
    }

    @Test
    fun `на Windows ищется в Program Files и у пользователя`() {
        val env = mapOf("ProgramFiles" to "C:\\Program Files", "LOCALAPPDATA" to "C:\\Users\\me\\AppData\\Local")
        val places = locator("Windows 11", env).candidates().map { it.path }
        assertTrue(places.any { it.endsWith("Microsoft\\Edge\\Application\\msedge.exe") }, places.toString())
        assertTrue(places.any { it.startsWith("C:\\Users\\me") && it.endsWith("chrome.exe") }, places.toString())
    }

    @Test
    fun `на Linux ищется и по известным файлам, и по командам из PATH`() {
        val env = mapOf("PATH" to "/usr/bin${File.pathSeparator}/usr/local/bin")
        val places = locator("Linux", env).candidates().map { it.path }
        assertTrue("${root.absolutePath}/snap/bin/chromium" in places, places.toString())
        assertTrue("/usr/bin/chromium" in places, places.toString())
        assertTrue("/usr/local/bin/google-chrome" in places, places.toString())
    }

    @Test
    fun `явный путь из настройки или окружения идёт первым`() {
        val browser = Files.createTempFile("browser-", ".bin").toFile()
        assertEquals(browser.absolutePath, locator("Linux", setting = browser.absolutePath).path())
        assertEquals(browser.name, locator("Linux", mapOf(BrowserLocator.VARIABLE to browser.absolutePath)).name())
        // Пустая и несуществующая настройка не считаются указанием.
        assertNull(locator("Linux", setting = " ").find())
        assertNull(locator("Linux", setting = File(home, "missing").absolutePath).find())
    }

    @Test
    fun `найденное помнится, пока файл на месте`() {
        val bin = File(home, "bin").apply { mkdirs() }
        val browser = File(bin, "chromium").apply { writeText("") }
        val located = locator("Linux", mapOf("PATH" to bin.absolutePath))
        assertEquals(browser.absolutePath, located.path())
        assertEquals(browser.absolutePath, located.path(), "второй раз ищется заново")
        browser.delete()
        assertNull(located.find(), "удалённый браузер остался в памяти")
    }

    /** Отказ носит свой код: кассиру нужно знать, что поставить, а не читать исключение. */
    @Test
    fun `без браузера отказ носит свой код`() {
        assertNull(locator("Linux").name())
        val failure = assertFailsWith<RendererMissingException> { locator("Linux").path() }
        assertEquals("RENDERER_MISSING", failure.code)
        assertEquals(503, failure.status)
        assertTrue(failure.trilingualMessage.ru.contains(BrowserLocator.PROPERTY))
    }

    /**
     * Поиск на самой машине: результат зависит от того, что на ней стоит,
     * поэтому проверяется лишь согласие пути и имени между собой.
     */
    @Test
    fun `поиск на этой машине отвечает согласованно`() {
        val here = BrowserLocator.local
        assertEquals(here.find()?.name, here.name())
    }
}
