package io.github.texport.superkassa.jvm.storage.impl.domain.config

/**
 * Разбор JDBC-адреса SQLite: `jdbc:sqlite:<файл>[?параметры]`.
 *
 * Путь к файлу нужен двум местам: коннектору, чтобы завести каталог под
 * базу, и узлу, чтобы привязать относительный путь к рабочему месту.
 * Разбирать адрес в каждом по-своему — верный способ разойтись на базе
 * в памяти или на параметрах после `?`, поэтому разбор один.
 */
object SqliteJdbcUrl {

    private const val PREFIX = "jdbc:sqlite:"

    /** Так драйвер обозначает базу в памяти: файла у неё нет. */
    private const val MEMORY = ":memory:"

    /** Адрес вида `file:` драйвер разбирает сам; подменять в нём путь нельзя. */
    private const val URI_FORM = "file:"

    fun isSqlite(jdbcUrl: String): Boolean = jdbcUrl.lowercase().startsWith(PREFIX)

    /**
     * Путь к файлу базы.
     *
     * @return `null`, если адрес не SQLite, база в памяти или путь задан
     *   в форме `file:`, которую разбирает драйвер.
     */
    fun filePath(jdbcUrl: String): String? {
        if (!isSqlite(jdbcUrl)) return null
        val path = jdbcUrl.substring(PREFIX.length).substringBefore('?')
        return path.takeUnless { it.isEmpty() || it == MEMORY || it.startsWith(URI_FORM) }
    }

    /** Тот же адрес с другим путём к файлу; параметры после `?` сохраняются. */
    fun withFilePath(jdbcUrl: String, path: String): String {
        val query = jdbcUrl.substringAfter('?', "")
        return if (query.isEmpty()) PREFIX + path else "$PREFIX$path?$query"
    }
}
