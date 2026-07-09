package io.github.texport.superkassa.jvm.receipt.impl

import java.nio.charset.Charset

/**
 * Утилита для качественного преобразования HTML-кода чека в байты команд ESC/POS
 * с поддержкой кириллицы (CP866) и сохранением структуры текста.
 */
object EscPosConverter {

    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    // Команда инициализации принтера: ESC @
    private val ESC_INIT = byteArrayOf(ESC, 0x40)

    // Выбор кодовой таблицы: ESC t n. Для CP866 (Cyrillic #1) n = 17 (0x11)
    private val ESC_SELECT_CP866 = byteArrayOf(ESC, 0x74, 0x11)

    // Команда отрезки бумаги с прокруткой ленты: GS V A 3
    private val ESC_CUT_FEED = byteArrayOf(GS, 0x56, 0x41, 0x03)

    internal var charsetProvider: (String) -> Charset = { Charset.forName(it) }

    /**
     * Конвертирует строку HTML-кода чека в массив байт команд принтера ESC/POS.
     * Автоматически выполняет верстку табличных строк, перенос длинных строк (word wrap)
     * и кодирование кириллицы в CP866.
     *
     * @param html исходный HTML-код чека.
     * @param paperWidthMm ширина бумажной ленты принтера в миллиметрах (поддерживаются 48, 58, 80).
     * @return массив байт команд принтера ESC/POS, готовый для отправки на печать.
     */
    fun convertHtmlToEscPos(html: String, paperWidthMm: Int): ByteArray {
        val charsPerLine = when (paperWidthMm) {
            48 -> 24
            58 -> 32
            80 -> 48
            else -> 32
        }

        // 1. Предварительная обработка HTML с выделением строк таблиц для форматирования
        val processedHtml = StringBuilder()
        var index = 0
        while (index < html.length) {
            val trStart = html.indexOf("<tr", index, ignoreCase = true)
            if (trStart != -1) {
                val beforeTr = html.substring(index, trStart)
                processedHtml.append(processGeneralHtml(beforeTr))

                val trEnd = html.indexOf("</tr>", trStart, ignoreCase = true)
                if (trEnd != -1) {
                    val trContent = html.substring(trStart, trEnd + 5)
                    processedHtml.append(processTableRow(trContent, charsPerLine)).append("\n")
                    index = trEnd + 5
                } else {
                    processedHtml.append(processGeneralHtml(html.substring(trStart, trStart + 4)))
                    index = trStart + 4
                }
            } else {
                processedHtml.append(processGeneralHtml(html.substring(index)))
                break
            }
        }

        // 2. Разбиваем текст на строки и делаем качественный перенос слов (word wrap)
        val rawLines = processedHtml.toString().split("\n")
        val formattedLines = mutableListOf<String>()

        for (line in rawLines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                formattedLines.add("")
                continue
            }
            formattedLines.addAll(wrapText(trimmedLine, charsPerLine))
        }

        // 3. Формируем поток байт для принтера
        val charset = try {
            charsetProvider("CP866")
        } catch (_: Exception) {
            charsetProvider("IBM866") // fallback для старых JVM
        }

        val out = java.io.ByteArrayOutputStream()
        out.write(ESC_INIT)
        out.write(ESC_SELECT_CP866)

        for (line in formattedLines) {
            out.write(line.toByteArray(charset))
            out.write('\n'.code)
        }

        out.write(ESC_CUT_FEED)
        return out.toByteArray()
    }

    private fun processGeneralHtml(html: String): String {
        val layoutReplaced = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</tr>"), "\n")

        return stripHtmlTags(layoutReplaced)
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun stripHtmlTags(html: String): String {
        val sb = StringBuilder()
        var insideTag = false
        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                insideTag = true
            } else if (c == '>') {
                insideTag = false
            } else if (!insideTag) {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun processTableRow(trHtml: String, width: Int): String {
        val cells = mutableListOf<String>()
        var index = 0
        while (index < trHtml.length) {
            val tdStart = trHtml.indexOf("<td", index, ignoreCase = true)
            val tdEnd = if (tdStart != -1) {
                val contentStart = trHtml.indexOf(">", tdStart) + 1
                trHtml.indexOf("</td>", contentStart, ignoreCase = true)
            } else {
                -1
            }
            if (tdEnd == -1) {
                break
            }
            val contentStart = trHtml.indexOf(">", tdStart) + 1
            val cellText = stripHtmlTags(trHtml.substring(contentStart, tdEnd))
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim()
            cells.add(cellText)
            index = tdEnd + 5
        }

        if (cells.isEmpty()) return ""
        if (cells.size == 1) return cells[0]

        if (cells.size == 2) {
            val left = cells[0]
            val right = cells[1]
            val spaceCount = width - left.length - right.length
            return if (spaceCount > 0) {
                "$left${" ".repeat(spaceCount)}$right"
            } else {
                "$left   $right"
            }
        }

        val name = cells[0]
        val qtyPrice = cells[1]
        val sum = cells[cells.size - 1]

        val sumWidth = 8.coerceAtMost(width / 4)
        val qtyWidth = 10.coerceAtMost(width / 3)
        val nameWidth = (width - sumWidth - qtyWidth - 2).coerceAtLeast(4)

        val namePart = if (name.length > nameWidth) name.substring(0, nameWidth) else name.padEnd(nameWidth)
        val qtyPart = qtyPrice.padStart(qtyWidth)
        val sumPart = sum.padStart(sumWidth)
        return "$namePart $qtyPart $sumPart"
    }

    private fun wrapText(text: String, width: Int): List<String> {
        if (text.length <= width) {
            return listOf(text)
        }
        val result = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))
        var currentLine = StringBuilder()

        for (word in words) {
            if (word.isEmpty()) continue
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= width) {
                currentLine.append(" ").append(word)
            } else {
                result.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            result.add(currentLine.toString())
        }
        return result
    }
}
