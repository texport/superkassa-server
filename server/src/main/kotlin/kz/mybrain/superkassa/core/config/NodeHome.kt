package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.jvm.storage.impl.domain.config.SqliteJdbcUrl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Каталог рабочего места узла.
 *
 * Всё, что узел хранит между запусками, лежит здесь: настройки
 * `config/core-settings.json` и база `data/core.db`. Прежде оба пути
 * были относительными и считались от каталога, из которого запустили
 * процесс. Тот же узел, поднятый из другого каталога — руками, другим
 * лаунчером, из сборки, — молча заводил рядом новое пустое рабочее место,
 * и кассы, смены и документы владельца выглядели исчезнувшими. Для
 * фискального продукта это опасно: «база не та» и «данные пропали»
 * со стороны неразличимы.
 *
 * Каталог задаётся свойством `superkassa.home` (переменной окружения
 * `SUPERKASSA_HOME`). Без него берётся каталог запуска: так продолжают
 * работать прежние установки и образ Docker, где рабочее место и есть
 * каталог запуска. Относительный путь к базе в настройках считается
 * от рабочего места, а не от процесса, — рабочее место тогда переносится
 * на другую машину целиком, без правки настроек.
 */
class NodeHome(dir: Path) {

    val dir: Path = dir.toAbsolutePath().normalize()

    val settingsFile: Path = this.dir.resolve(SETTINGS_FILE)

    /**
     * Лежали ли настройки на месте к моменту запуска.
     *
     * Снимается один раз, при создании: дальше узел сам запишет файл
     * с умолчаниями, и отличить первый запуск от потерянной базы будет
     * нечем.
     */
    val settingsFound: Boolean = Files.isRegularFile(settingsFile)

    /** JDBC-адрес, в котором относительный путь к файлу SQLite привязан к рабочему месту. */
    fun resolveJdbcUrl(jdbcUrl: String): String {
        val path = SqliteJdbcUrl.filePath(jdbcUrl) ?: return jdbcUrl
        val file = Paths.get(path)
        if (file.isAbsolute) return jdbcUrl
        return SqliteJdbcUrl.withFilePath(jdbcUrl, dir.resolve(file).normalize().toString())
    }

    /**
     * Не даёт молча завести пустую базу там, где ждали существующую.
     *
     * Настройки уже лежали на месте — значит, рабочее место не новое,
     * и базы у него не может не быть. Её отсутствие — перенесённое или
     * неверно указанное рабочее место, а не повод начать с нуля.
     *
     * @throws IllegalStateException если базы нет там, куда указывают настройки.
     */
    fun requireDatabase(resolvedJdbcUrl: String) {
        if (!settingsFound) return
        val path = SqliteJdbcUrl.filePath(resolvedJdbcUrl) ?: return
        check(Files.isRegularFile(Paths.get(path))) {
            "Database file $path named in $settingsFile does not exist. " +
                "The node was started against a workspace whose database is missing: " +
                "point superkassa.home at the directory holding config/ and data/, or restore the file. " +
                "To deliberately start a new empty workspace, remove $settingsFile first."
        }
    }

    companion object {

        /** Настройки рабочего места, относительно его каталога. */
        const val SETTINGS_FILE = "config/core-settings.json"

        /** Свойство, которым запускающий называет рабочее место узлу. */
        const val PROPERTY = "superkassa.home"

        /** Рабочее место по свойству; пустое свойство — каталог запуска. */
        fun of(configured: String?): NodeHome {
            val dir = configured?.takeIf { it.isNotBlank() } ?: System.getProperty("user.dir")
            return NodeHome(Paths.get(dir))
        }
    }
}
