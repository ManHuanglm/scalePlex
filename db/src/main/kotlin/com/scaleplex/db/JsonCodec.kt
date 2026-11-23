package com.scaleplex.db

import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.PluTextLine

/**
 * Minimal JSON serialization for the nested text-line / label-element lists stored
 * as TEXT columns. Avoids pulling a JSON library into the DB layer; the shapes are
 * simple and fully controlled.
 */
internal object JsonCodec {

    private fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '"' -> append("\\\""); '\\' -> append("\\\\")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
        }
    }

    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '"' -> sb.append('"'); '\\' -> sb.append('\\')
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                    'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    // ---- List<PluTextLine> ----
    fun writeTextLines(lines: List<PluTextLine>): String =
        "[" + lines.joinToString(",") { "{\"fontNo\":${it.fontNo},\"text\":\"${esc(it.text)}\"}" } + "]"

    fun readTextLines(json: String): List<PluTextLine> {
        if (json.isBlank() || json == "[]") return emptyList()
        return Regex("\\{[^}]*\\}").findAll(json).map { obj ->
            val font = Regex("\"fontNo\"\\s*:\\s*(\\d+)").find(obj.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val text = Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(obj.value)
                ?.groupValues?.get(1)?.let(::unesc) ?: ""
            PluTextLine(font, text)
        }.toList()
    }

    // ---- List<LabelElement> ----
    // Element JSON 序列化收敛到 core 的 LabelTemplateJson（与导入导出文件格式同源）
    fun writeElements(elements: List<LabelElement>): String =
        com.scaleplex.core.model.LabelTemplateJson.writeElements(elements)

    fun readElements(json: String): List<LabelElement> =
        com.scaleplex.core.model.LabelTemplateJson.readElements(json)
}
