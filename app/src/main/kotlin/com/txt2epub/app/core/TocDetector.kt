package com.txt2epub.app.core

import com.txt2epub.app.model.Chapter
import com.txt2epub.app.model.ChapterRule
import com.txt2epub.app.model.TocEntry

/**
 * 目录识别 + 章节切分。
 */
object TocDetector {

    data class Options(
        /** 标题行最大长度，超过则不当作标题（避免正文长行误判） */
        val maxTitleLength: Int = 60,
        /** 忽略开头若干行（用于跳过网站生成的目录页 / 版权声明） */
        val skipFirstLines: Int = 0,
        /** 清理正文中的分隔线与广告行 */
        val cleanNoise: Boolean = true,
        /** 小于该字数的章节合并进上一章 */
        val minChapterChars: Int = 80,
        /** 第一个标题之前的内容（书名、作者、内容简介）是否做成「简介」章节放在最前面 */
        val includePreamble: Boolean = true,
        /** 识别不到目录时按多少字自动分章 */
        val fallbackChunkChars: Int = 8000,
    )

    /** 按规则扫描全文，返回目录条目。 */
    fun detect(lines: List<String>, rules: List<ChapterRule>, options: Options): List<TocEntry> {
        val compiled = rules
            .filter { it.enabled }
            .sortedBy { it.order }
            .mapNotNull { rule -> rule.compile()?.let { rule to it } }

        if (compiled.isEmpty()) return emptyList()

        val result = ArrayList<TocEntry>()
        for (i in lines.indices) {
            if (i < options.skipFirstLines) continue
            val line = sanitize(lines[i])
            if (line.isEmpty()) continue
            if (line.length > options.maxTitleLength) continue

            for ((rule, regex) in compiled) {
                val m = regex.find(line) ?: continue
                if (m.range.first != 0) continue
                val title = m.value.trim().ifEmpty { line }
                result.add(TocEntry(lineIndex = i, level = rule.level, title = title))
                break
            }
        }
        return dedupe(result)
    }

    /** 去掉紧邻的重复标题（常见于正文前的目录页）。 */
    private fun dedupe(entries: List<TocEntry>): List<TocEntry> {
        if (entries.isEmpty()) return entries
        val out = ArrayList<TocEntry>()
        var last: TocEntry? = null
        for (e in entries) {
            val prev = last
            if (prev != null && prev.title == e.title && e.lineIndex - prev.lineIndex <= 3) continue
            out.add(e)
            last = e
        }
        return out
    }

    /** 规则调试：返回该正则命中的前若干行。 */
    fun testPattern(lines: List<String>, pattern: String, limit: Int = 30): List<Pair<Int, String>> {
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Throwable) {
            return emptyList()
        }
        val out = ArrayList<Pair<Int, String>>()
        for (i in lines.indices) {
            val line = sanitize(lines[i])
            if (line.isEmpty()) continue
            val m = regex.find(line) ?: continue
            if (m.range.first != 0) continue
            out.add(i to (m.value.trim().ifEmpty { line }))
            if (out.size >= limit) break
        }
        return out
    }

    fun sanitize(line: String): String = line
        .replace('\u3000', ' ')
        .replace('\u00A0', ' ')
        .trim()

    /** 把正文行按目录位置切分为章节。 */
    fun split(lines: List<String>, entries: List<TocEntry>, options: Options): List<Chapter> {
        val marks = entries.filter { it.included }.sortedBy { it.lineIndex }
        if (marks.isEmpty()) return splitByLength(lines, options)

        val segs = ArrayList<Seg>()

        // 首个标题之前的内容（书名、作者、内容简介等）单独成章，放在第一章前面
        val firstIndex = marks.first().lineIndex
        if (options.includePreamble && firstIndex > 0) {
            val pre = collect(lines, 0, firstIndex, options.cleanNoise)
            if (pre.isNotEmpty()) {
                segs.add(Seg("简介", 1, pre.toMutableList(), noMerge = true))
            }
        }

        for (i in marks.indices) {
            val m = marks[i]
            val start = m.lineIndex + 1
            val end = if (i + 1 < marks.size) marks[i + 1].lineIndex else lines.size
            val paras = collect(lines, start, end, options.cleanNoise).toMutableList()
            segs.add(Seg(m.title, m.effectiveLevel(), paras))
        }

        val merged = mergeShort(segs, options.minChapterChars)

        // 保护：如果把整本书压得只剩一两章，说明阈值对这本书不合适，保留原始切分
        val finalSegs = if (segs.size >= 3 && merged.size < 3) segs else merged

        return finalSegs
            .filter { it.paras.isNotEmpty() }
            .mapIndexed { i, s -> Chapter(i + 1, s.title, s.level, s.paras) }
    }

    /**
     * 合并过短的章节到上一章。
     * 用户给的 [minChapterChars] 作为上限，实际阈值再参考全书章节长度的中位数自适应：
     * 长篇小说不会被误并，短章合集也不会被压成一整章。
     */
    private fun mergeShort(segs: List<Seg>, minChapterChars: Int): List<Seg> {
        if (segs.size < 2) return segs
        val sizes = segs.map { it.paras.sumOf { p -> p.length } }.sorted()
        val median = sizes[sizes.size / 2]
        val threshold = minOf(minChapterChars, (median * 0.4f).toInt())

        val merged = ArrayList<Seg>()
        for (s in segs) {
            val last = merged.lastOrNull()
            if (last != null && !last.noMerge && s.paras.sumOf { it.length } < threshold) {
                last.paras.add(s.title)
                last.paras.addAll(s.paras)
            } else {
                merged.add(s)
            }
        }
        return merged
    }

    private data class Seg(
        val title: String,
        val level: Int,
        val paras: MutableList<String>,
        /** true 表示这一章不吸收后续短章节（例如「简介」） */
        val noMerge: Boolean = false,
    )

    private fun collect(lines: List<String>, from: Int, to: Int, clean: Boolean): List<String> {
        val end = to.coerceAtMost(lines.size)
        val out = ArrayList<String>()
        for (i in from until end) {
            if (i < 0) continue
            val line = sanitize(lines[i])
            if (line.isEmpty()) continue
            if (clean && isNoise(line)) continue
            out.add(line)
        }
        return out
    }

    private val noiseRegex = Regex("""^[\s\-=_*·•◆■□※～~#\$%^&+|/\\<>【】\[\]（）()「」『』""'。，、；：？！,.!?;:]*$""")

    private val adRegex = listOf(
        Regex("""^.*(笔趣阁|顶点小说|起点中文网|晋江文学|纵横中文|小说下载|txt下载|电子书下载|手机阅读|wap\.|www\.).*$""", RegexOption.IGNORE_CASE),
        Regex("""^.*(请记住本站|记住我们的网址|欢迎广大书友|更多精彩小说|本章免费|免费小说|书友群|加群|公众号|敬请关注).*$"""),
        Regex("""^(ps|PS|Ps)\s*[:：].{0,60}$"""),
    )

    private fun isNoise(line: String): Boolean {
        if (noiseRegex.matches(line)) return true
        if (line.length <= 45 && adRegex.any { it.matches(line) }) return true
        return false
    }

    /** 没有识别到任何目录时的兜底：按字数均分。 */
    private fun splitByLength(lines: List<String>, options: Options): List<Chapter> {
        val paras = collect(lines, 0, lines.size, options.cleanNoise)
        val chapters = ArrayList<Chapter>()
        var buf = ArrayList<String>()
        var size = 0
        var id = 1
        for (p in paras) {
            buf.add(p)
            size += p.length
            if (size >= options.fallbackChunkChars) {
                chapters.add(Chapter(id++, "第 $id 部分", 1, buf.toList()))
                buf = ArrayList()
                size = 0
            }
        }
        if (buf.isNotEmpty()) chapters.add(Chapter(id, "第 $id 部分", 1, buf.toList()))
        return chapters
    }
}
