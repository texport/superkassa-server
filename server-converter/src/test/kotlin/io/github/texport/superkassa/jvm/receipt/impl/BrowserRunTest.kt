package io.github.texport.superkassa.jvm.receipt.impl

import java.io.File
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier

/**
 * Что делает узел, когда браузер отработал не так, как ждали.
 *
 * Браузер здесь подставной: настоящий Chromium ведёт себя по-разному
 * от машины к машине, а отказные ветки обязаны читаться одинаково
 * везде. Подставной путь узел берёт из той же настройки обслуживания,
 * что и на рабочем месте.
 */
class BrowserRunTest {

    private val directory = File.createTempFile("browser-stub", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    @AfterTest
    fun forget() {
        System.clearProperty(BrowserLocator.PROPERTY)
        directory.deleteRecursively()
    }

    /**
     * Подставной браузер: пишет в поток ошибок [noise] килобайт шума
     * и кладёт [bytes] байт туда, куда его попросили снимком или печатью.
     */
    private fun browser(noise: Int, bytes: Int): File {
        val script = File(directory, "browser.sh")
        script.writeText(
            """
            #!/bin/sh
            for i in ${'$'}(seq 1 $noise); do
              printf '%s\n' "chromium noise line ${'$'}i: device unavailable, falling back to software" >&2
            done
            for arg in "${'$'}@"; do
              case "${'$'}arg" in
                --screenshot=*|--print-to-pdf=*)
                  out="${'$'}{arg#*=}"
                  : > "${'$'}out"
                  if [ $bytes -gt 0 ]; then
                    head -c $bytes /dev/zero | tr '\0' 'x' > "${'$'}out"
                  fi
                  ;;
              esac
            done
            exit 0
            """.trimIndent()
        )
        script.setExecutable(true)
        System.setProperty(BrowserLocator.PROPERTY, script.absolutePath)
        return script
    }

    private val page = """<html><head></head><body>чек</body></html>"""

    /**
     * Разговорчивый браузер не подвешивает узел.
     *
     * Труба между узлом и браузером вмещает десятки килобайт: дальше
     * пишущий в неё останавливается и ждёт, пока прочитают. Узел читал
     * поток ошибок только после конца процесса, а мерку высоты — вовсе
     * не читал, и на машине, где браузер ругается на каждое недоступное
     * устройство, печать вставала насовсем: узел ждал браузера, браузер
     * ждал узла.
     */
    @Test
    fun `браузер, пишущий в поток ошибок, не подвешивает печать`() {
        browser(noise = NOISY_LINES, bytes = PDF_BYTES)

        val pdf: ByteArray = assertTimeoutPreemptively(
            Duration.ofSeconds(WAIT_S),
            ThrowingSupplier { DocumentConvertAdapter().htmlToPdf(page) }
        )
        assertTrue(pdf.isNotEmpty(), "печать не дошла до файла")
    }

    private companion object {
        /** Строк шума: с запасом больше того, что вмещает труба. */
        const val NOISY_LINES = 3000

        /** Столько байт подставной браузер кладёт в файл печати. */
        const val PDF_BYTES = 64

        /** Сколько ждать печати: втрое дольше, чем узел ждёт браузера. */
        const val WAIT_S = 45L
    }
}
