package com.txt2epub.app.model

import java.util.UUID

/**
 * 一条章节识别规则。
 * [pattern] 为正则表达式，扫描时按 [order] 升序依次匹配，命中即停止。
 * [level] 为目录层级：1 = 一级（卷/章），2 = 二级（节）。
 */
data class ChapterRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val pattern: String = "",
    val level: Int = 1,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val order: Int = 0,
) {
    /** 编译正则，非法表达式返回 null。 */
    fun compile(): Regex? = try {
        Regex(pattern, RegexOption.IGNORE_CASE)
    } catch (_: Throwable) {
        null
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("pattern", pattern)
        put("level", level)
        put("enabled", enabled)
        put("builtIn", builtIn)
        put("order", order)
    }

    companion object {
        fun fromJson(o: org.json.JSONObject): ChapterRule = ChapterRule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "自定义规则"),
            pattern = o.optString("pattern", ""),
            level = o.optInt("level", 1),
            enabled = o.optBoolean("enabled", true),
            builtIn = o.optBoolean("builtIn", false),
            order = o.optInt("order", 100),
        )
    }
}

/** 识别到的一个目录条目，允许在界面上编辑标题 / 层级 / 是否采用。 */
class TocEntry(
    val lineIndex: Int,
    val level: Int,
    title: String,
    var included: Boolean = true,
) {
    var title: String = title
        set(value) {
            field = value
        }

    var manualLevel: Int? = null

    fun effectiveLevel(): Int = manualLevel ?: level
}

/** 切分后的章节。 */
data class Chapter(
    val id: Int,
    val title: String,
    val level: Int,
    val paragraphs: List<String>,
) {
    val charCount: Int get() = paragraphs.sumOf { it.length }
}

/** 书籍元数据。 */
data class BookMeta(
    var title: String = "",
    var author: String = "佚名",
    var language: String = "zh-CN",
    var publisher: String = "",
    var description: String = "",
    var identifier: String = "urn:uuid:" + UUID.randomUUID().toString(),
    var date: String = "",
)

/** TXT 载入结果。 */
data class LoadedText(
    val text: String,
    val charset: String,
    val lines: List<String>,
    val charCount: Int,
)

enum class Step(val label: String) {
    PICK("选择文件"),
    TOC("识别目录"),
    META("书籍信息"),
    EXPORT("转换导出"),
}
