package com.scaleplex.core.model

/**
 * JSON serialization for [LabelElement] lists — the shape used both by the DB
 * `label_template.elements_json` column and by label-template import/export files.
 * Single source of truth so DB storage and exported files stay format-compatible.
 */
object LabelTemplateJson {

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

    /** 序列化元素列表（DB 存储与导入导出文件共用格式）。 */
    fun writeElements(elements: List<LabelElement>): String =
        "[" + elements.joinToString(",") { e ->
            "{\"kind\":\"${e.kind.name}\",\"x\":${e.xMm},\"y\":${e.yMm},\"w\":${e.widthMm},\"h\":${e.heightMm}," +
                "\"text\":\"${esc(e.text)}\",\"font\":\"${esc(e.fontName)}\",\"size\":${e.fontSizePt}," +
                "\"align\":${e.align},\"rot\":${e.rotation}}"
        } + "]"

    /** 反序列化元素列表，未知类型与缺失字段降级为默认值。 */
    fun readElements(json: String): List<LabelElement> {
        if (json.isBlank() || json == "[]") return emptyList()
        return Regex("\\{[^}]*\\}").findAll(json).map { obj ->
            val kind = Regex("\"kind\"\\s*:\\s*\"(\\w+)\"").find(obj.value)?.groupValues?.get(1)
                ?.let { runCatching { LabelElementKind.valueOf(it) }.getOrNull() } ?: LabelElementKind.TEXT
            fun num(key: String) = Regex("\"$key\"\\s*:\\s*(-?[\\d.]+)").find(obj.value)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val text = Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(obj.value)
                ?.groupValues?.get(1)?.let(::unesc) ?: ""
            val font = Regex("\"font\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(obj.value)
                ?.groupValues?.get(1)?.let(::unesc) ?: "SimSun"
            LabelElement(
                kind = kind, xMm = num("x"), yMm = num("y"), widthMm = num("w"), heightMm = num("h"),
                text = text, fontName = font, fontSizePt = num("size").toInt(),
                align = num("align").toInt(), rotation = num("rot").toInt(),
            )
        }.toList()
    }

    /** 整个模板（含尺寸与名称）序列化为 JSON 文件内容。 */
    fun writeTemplate(template: LabelTemplate): String = buildString {
        append("{\"name\":\"").append(esc(template.name)).append("\",")
        append("\"width\":").append(template.widthMm).append(",")
        append("\"height\":").append(template.heightMm).append(",")
        append("\"note\":\"").append(esc(template.note)).append("\",")
        append("\"elements\":").append(writeElements(template.elements))
        append("}")
    }

    /** 从 JSON 文件内容还原模板（id 置 0，保存时按新模板入库）。 */
    fun readTemplate(json: String): LabelTemplate {
        val name = Regex("\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?.groupValues?.get(1)?.let(::unesc) ?: "Imported"
        val width = Regex("\"width\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 40
        val height = Regex("\"height\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 30
        val note = Regex("\"note\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?.groupValues?.get(1)?.let(::unesc) ?: ""
        val elementsJson = Regex("\"elements\"\\s*:\\s*(\\[.*\\])").find(json)?.groupValues?.get(1) ?: "[]"
        return LabelTemplate(
            id = 0, name = name, widthMm = width, heightMm = height,
            elements = readElements(elementsJson), note = note,
        )
    }
}
