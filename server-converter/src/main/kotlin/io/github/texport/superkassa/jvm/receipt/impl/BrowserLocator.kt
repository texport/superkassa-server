package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.core.domain.api.exception.SuperkassaException
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import java.io.File

/**
 * Где на этой машине браузер семейства Chromium.
 *
 * Образ документа — снимок страницы, и делает его безголовый браузер:
 * своего движка вёрстки у узла нет, а печатная форма живёт на CSS,
 * которого чистые Java-рисовальщики не понимают. Прежде узел знал одно
 * имя — `chromium-browser` — и на Linux, где пакет зовётся `chromium`,
 * и на Windows, где браузер лежит в Program Files, просмотр и печать
 * документов не работали вовсе, а кассир читал текст исключения.
 *
 * Порядок поиска: явный путь из настройки, затем известные места
 * установки для этой системы, затем команды из `PATH`. Найденное
 * запоминается, пока файл на месте; явный путь впереди запомненного —
 * настройка есть слово обслуживания, и найденный раньше браузер его
 * не перекрывает.
 *
 * Система, домашний каталог и окружение подставляются: так каждая
 * ветка поиска проверяется на одной машине.
 */
class BrowserLocator(
    private val os: String = System.getProperty("os.name", ""),
    private val home: String = System.getProperty("user.home", ""),
    private val env: (String) -> String? = System::getenv,
    private val setting: () -> String? = { System.getProperty(PROPERTY) }
) {
    @Volatile
    private var found: File? = null

    /** Путь к браузеру или отказ с объяснением, что поставить. */
    fun path(): String = find()?.absolutePath ?: throw RendererMissingException()

    /** Имя найденного браузера для сведений об узле: файл без пути. */
    fun name(): String? = find()?.name

    /** Найденный браузер; `null` — на машине его нет. */
    fun find(): File? {
        explicit()?.let { return it }
        found?.takeIf { it.isFile }?.let { return it }
        val located = candidates().firstOrNull { it.isFile }
        found = located
        return located
    }

    /** Где браузер может лежать на этой системе — в порядке предпочтения. */
    fun candidates(): List<File> {
        val name = os.lowercase()
        return when {
            name.contains("mac") -> MAC_APPS.flatMap { app ->
                listOf(File("/Applications", app), File(home, "Applications/$app"))
            }
            name.contains("win") -> WINDOWS_ROOTS.mapNotNull(env).flatMap { root ->
                WINDOWS_APPS.map { File(root, it) }
            }
            else -> LINUX_FILES.map(::File) + onPath()
        }
    }

    private fun explicit(): File? =
        (setting() ?: env(VARIABLE))
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }

    private fun onPath(): List<File> {
        val dirs = env("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        return LINUX_COMMANDS.flatMap { command -> dirs.map { File(it, command) } }
    }

    companion object {
        /** Свойство JVM и переменная окружения с путём к браузеру. */
        const val PROPERTY: String = "superkassa.browser"
        const val VARIABLE: String = "SUPERKASSA_BROWSER"

        /** Поиск на этой машине: один на узел, найденное помнится. */
        val local: BrowserLocator = BrowserLocator()

        private val MAC_APPS = listOf(
            "Google Chrome.app/Contents/MacOS/Google Chrome",
            "Chromium.app/Contents/MacOS/Chromium",
            "Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "Brave Browser.app/Contents/MacOS/Brave Browser"
        )

        private val WINDOWS_ROOTS = listOf("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")

        private val WINDOWS_APPS = listOf(
            "Google\\Chrome\\Application\\chrome.exe",
            "Microsoft\\Edge\\Application\\msedge.exe",
            "Chromium\\Application\\chrome.exe",
            "BraveSoftware\\Brave-Browser\\Application\\brave.exe"
        )

        private val LINUX_COMMANDS = listOf(
            "chromium",
            "chromium-browser",
            "google-chrome",
            "google-chrome-stable",
            "microsoft-edge",
            "microsoft-edge-stable",
            "brave-browser"
        )

        private val LINUX_FILES = listOf(
            "/snap/bin/chromium",
            "/usr/lib/chromium/chromium",
            "/usr/lib/chromium-browser/chromium-browser",
            "/opt/google/chrome/chrome",
            "/opt/microsoft/msedge/msedge"
        )
    }
}

/**
 * Браузера для образов документов на машине нет.
 *
 * Отдельный код и статус 503: это не отказ по документу и не ошибка
 * запроса, а недостающая часть рабочего места — просмотр и печать
 * вернутся, как только браузер поставят или укажут путь к нему.
 */
class RendererMissingException : SuperkassaException(
    code = "RENDERER_MISSING",
    status = 503,
    trilingualMessage = TrilingualMessage(
        ru = "Для просмотра и печати документов нужен браузер Chrome, Chromium или Edge на этой машине: " +
            "установите его или укажите путь в настройке ${BrowserLocator.PROPERTY}",
        kk = "Құжаттарды қарау мен басып шығару үшін осы машинада Chrome, Chromium немесе Edge браузері керек: " +
            "оны орнатыңыз немесе жолын ${BrowserLocator.PROPERTY} баптауында көрсетіңіз",
        en = "Viewing and printing documents needs a Chrome, Chromium or Edge browser on this machine: " +
            "install one or set its path in the ${BrowserLocator.PROPERTY} setting"
    )
)
