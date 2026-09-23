package kz.mybrain.superkassa.core.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Файлы настроек в репозитории не несут пинов.
 *
 * Репозиторий открыт: пин, записанный в образце настроек, известен всем,
 * кто его прочтёт, а узел его всё равно не читает — администратор новой
 * кассы задаёт пин сам, при её заведении.
 */
class ShippedSettingsTest {

    @Test
    fun `образцы настроек узла пинов не содержат`() {
        for (file in listOf(Path.of("config/core-settings.json"), Path.of("../config/core-settings.json"))) {
            val text = Files.readString(file)
            assertFalse(text.contains("pin", ignoreCase = true), "$file names a PIN")
        }
    }
}
