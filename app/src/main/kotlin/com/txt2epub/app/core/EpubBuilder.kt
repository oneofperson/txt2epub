package com.txt2epub.app.core

import com.txt2epub.app.model.BookMeta
import com.txt2epub.app.model.Chapter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 极简 EPUB 3.0 生成器（同时输出 toc.ncx 以兼容 EPUB2 阅读器）。
 * 不依赖任何第三方库，直接用 java.util.zip 产出标准 epub 容器。
 */
object EpubBuilder {

    data class Cover(val bytes: ByteArray, val mime: String) {
        val ext: String get() = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    fun build(
        out: OutputStream,
        meta: BookMeta,
        chapters: List<Chapter>,
        cover: Cover?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val total = chapters.size + 6
        var done = 0
        fun tick() {
            done++
            onProgress(done, total)
        }

        ZipOutputStream(java.io.BufferedOutputStream(out, 256 * 1024)).use { zip ->

            // 1. mimetype 必须第一个出现且不压缩
            writeStored(zip, "mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
            tick()

            // 2. container.xml
            write(zip, "META-INF/container.xml", containerXml())
            write(zip, "META-INF/com.apple.ibooks.display-options.xml", ibooksOptions())
            tick()

            // 3. 样式
            write(zip, "OEBPS/style.css", STYLE_CSS)
            tick()

            // 4. 封面
            val coverHref = if (cover != null) {
                val href = "OEBPS/images/cover.${cover.ext}"
                writeBinary(zip, href, cover.bytes)
                href
            } else null
            val coverRel = coverHref?.removePrefix("OEBPS/")

            // 5. 封面页
            if (coverRel != null) {
                write(zip, "OEBPS/cover.xhtml", coverPage(meta.title, coverRel))
            }
            tick()

            // 6. 章节
            val hrefs = ArrayList<String>(chapters.size)
            for (ch in chapters) {
                val name = String.format(Locale.US, "text/ch%04d.xhtml", ch.id)
                hrefs.add(name)
                write(zip, "OEBPS/$name", chapterPage(ch))
                tick()
            }

            // 7. 导航
            val tree = buildTree(chapters)
            write(zip, "OEBPS/nav.xhtml", navDocument(meta.title, chapters, hrefs, tree))
            write(zip, "OEBPS/toc.ncx", ncxDocument(meta, chapters, hrefs, tree))
            tick()

            // 8. OPF
            write(zip, "OEBPS/content.opf", opfDocument(meta, chapters, hrefs, cover, coverRel))
            tick()
        }
    }

    // ---------------------------------------------------------------- 文件写入

    private fun write(zip: ZipOutputStream, name: String, content: String) =
        writeBinary(zip, name, content.toByteArray(Charsets.UTF_8))

    private fun writeBinary(zip: ZipOutputStream, name: String, data: ByteArray) {
        val e = ZipEntry(name)
        zip.putNextEntry(e)
        zip.write(data)
        zip.closeEntry()
    }

    private fun writeStored(zip: ZipOutputStream, name: String, data: ByteArray) {
        val e = ZipEntry(name)
        e.method = ZipEntry.STORED
        e.size = data.size.toLong()
        val crc = CRC32()
        crc.update(data)
        e.crc = crc.value
        zip.putNextEntry(e)
        zip.write(data)
        zip.closeEntry()
    }

    // ---------------------------------------------------------------- 目录树

    private class Node(val index: Int, val children: MutableList<Node> = mutableListOf())

    private fun buildTree(chapters: List<Chapter>): List<Node> {
        val roots = ArrayList<Node>()
        var lastRoot: Node? = null
        chapters.forEachIndexed { i, ch ->
            if (ch.level >= 2 && lastRoot != null) {
                lastRoot!!.children.add(Node(i))
            } else {
                val n = Node(i)
                roots.add(n)
                lastRoot = n
            }
        }
        return roots
    }

    // ---------------------------------------------------------------- 文档生成

    private fun containerXml(): String = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private fun ibooksOptions(): String = """<?xml version="1.0" encoding="UTF-8"?>
<display_options>
  <platform name="*">
    <option name="specified-fonts">true</option>
  </platform>
</display_options>
"""

    private fun coverPage(title: String, coverRel: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN" lang="zh-CN">
<head>
  <meta charset="utf-8"/>
  <title>${esc(title.ifBlank { "封面" })}</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body class="cover-page">
  <div class="cover"><img src="${esc(coverRel)}" alt="cover"/></div>
</body>
</html>
"""

    private fun chapterPage(ch: Chapter): String {
        val tag = if (ch.level >= 2) "h2" else "h1"
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<!DOCTYPE html>""").append('\n')
        sb.append("""<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN" lang="zh-CN">""").append('\n')
        sb.append("<head>\n")
        sb.append("""  <meta charset="utf-8"/>""").append('\n')
        sb.append("  <title>${esc(ch.title)}</title>\n")
        sb.append("""  <link rel="stylesheet" type="text/css" href="../style.css"/>""").append('\n')
        sb.append("</head>\n<body>\n")
        sb.append("<$tag>${esc(ch.title)}</$tag>\n")
        for (p in ch.paragraphs) {
            val t = esc(p)
            if (t.isBlank()) continue
            sb.append("<p>$t</p>\n")
        }
        sb.append("</body>\n</html>\n")
        return sb.toString()
    }

    private fun navDocument(
        title: String,
        chapters: List<Chapter>,
        hrefs: List<String>,
        tree: List<Node>,
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<!DOCTYPE html>""").append('\n')
        sb.append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN" lang="zh-CN">""").append('\n')
        sb.append("<head>\n  <meta charset=\"utf-8\"/>\n  <title>${esc(title.ifBlank { "目录" })}</title>\n")
        sb.append("""  <link rel="stylesheet" type="text/css" href="style.css"/>""").append('\n')
        sb.append("</head>\n<body>\n")
        sb.append("""<nav epub:type="toc" id="toc" role="doc-toc">""").append('\n')
        sb.append("<h1>目录</h1>\n<ol>\n")
        fun emit(node: Node, depth: Int) {
            val ch = chapters[node.index]
            val href = hrefs[node.index]
            val pad = "  ".repeat(depth + 1)
            sb.append("$pad<li><a href=\"${esc(href)}\">${esc(ch.title)}</a>")
            if (node.children.isNotEmpty()) {
                sb.append("\n$pad  <ol>\n")
                node.children.forEach { emit(it, depth + 2) }
                sb.append("$pad  </ol>\n$pad")
            }
            sb.append("</li>\n")
        }
        tree.forEach { emit(it, 0) }
        sb.append("</ol>\n</nav>\n")
        sb.append("""<nav epub:type="landmarks" hidden="hidden">""").append('\n')
        sb.append("""  <ol><li><a epub:type="bodymatter" href="${esc(hrefs.firstOrNull() ?: "nav.xhtml")}">正文</a></li></ol>""").append('\n')
        sb.append("</nav>\n</body>\n</html>\n")
        return sb.toString()
    }

    private fun ncxDocument(
        meta: BookMeta,
        chapters: List<Chapter>,
        hrefs: List<String>,
        tree: List<Node>,
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1" xml:lang="zh-CN">""").append('\n')
        sb.append("<head>\n")
        sb.append("""  <meta name="dtb:uid" content="${esc(meta.identifier)}"/>""").append('\n')
        sb.append("""  <meta name="dtb:depth" content="${if (tree.any { it.children.isNotEmpty() }) 2 else 1}"/>""").append('\n')
        sb.append("""  <meta name="dtb:totalPageCount" content="0"/>""").append('\n')
        sb.append("""  <meta name="dtb:maxPageNumber" content="0"/>""").append('\n')
        sb.append("</head>\n")
        sb.append("<docTitle><text>${esc(meta.title.ifBlank { "未命名" })}</text></docTitle>\n")
        if (meta.author.isNotBlank()) {
            sb.append("<docAuthor><text>${esc(meta.author)}</text></docAuthor>\n")
        }
        sb.append("<navMap>\n")
        var order = 1
        fun emit(node: Node, depth: Int) {
            val ch = chapters[node.index]
            val id = "np${order}"
            val pad = "  ".repeat(depth + 1)
            sb.append("$pad<navPoint id=\"$id\" playOrder=\"$order\">\n")
            sb.append("$pad  <navLabel><text>${esc(ch.title)}</text></navLabel>\n")
            sb.append("$pad  <content src=\"${esc(hrefs[node.index])}\"/>\n")
            order++
            node.children.forEach { emit(it, depth + 1) }
            sb.append("$pad</navPoint>\n")
        }
        tree.forEach { emit(it, 0) }
        sb.append("</navMap>\n</ncx>\n")
        return sb.toString()
    }

    private fun opfDocument(
        meta: BookMeta,
        chapters: List<Chapter>,
        hrefs: List<String>,
        cover: Cover?,
        coverRel: String?,
    ): String {
        val date = meta.date.ifBlank { nowStamp() }
        val iso = meta.date.ifBlank { nowIso() }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="BookId" xml:lang="zh-CN">""").append('\n')
        sb.append("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""").append('\n')
        sb.append("    <dc:identifier id=\"BookId\">${esc(meta.identifier)}</dc:identifier>\n")
        sb.append("    <dc:title>${esc(meta.title.ifBlank { "未命名" })}</dc:title>\n")
        sb.append("    <dc:creator>${esc(meta.author.ifBlank { "佚名" })}</dc:creator>\n")
        sb.append("    <dc:language>${esc(meta.language.ifBlank { "zh-CN" })}</dc:language>\n")
        if (meta.publisher.isNotBlank()) sb.append("    <dc:publisher>${esc(meta.publisher)}</dc:publisher>\n")
        if (meta.description.isNotBlank()) sb.append("    <dc:description>${esc(meta.description)}</dc:description>\n")
        sb.append("    <dc:date>$date</dc:date>\n")
        sb.append("""    <meta property="dcterms:modified">$iso</meta>""").append('\n')
        if (coverRel != null) sb.append("""    <meta name="cover" content="cover-image"/>""").append('\n')
        sb.append("  </metadata>\n")
        sb.append("  <manifest>\n")
        sb.append("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""").append('\n')
        sb.append("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""").append('\n')
        sb.append("""    <item id="css" href="style.css" media-type="text/css"/>""").append('\n')
        if (coverRel != null && cover != null) {
            sb.append("""    <item id="cover-image" href="${esc(coverRel)}" media-type="${cover.mime}" properties="cover-image"/>""").append('\n')
            sb.append("""    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>""").append('\n')
        }
        chapters.forEachIndexed { i, ch ->
            sb.append("""    <item id="ch${ch.id}" href="${esc(hrefs[i])}" media-type="application/xhtml+xml"/>""").append('\n')
        }
        sb.append("  </manifest>\n")
        sb.append("""  <spine toc="ncx">""").append('\n')
        if (coverRel != null) sb.append("""    <itemref idref="cover" linear="no"/>""").append('\n')
        sb.append("""    <itemref idref="nav" linear="no"/>""").append('\n')
        for (ch in chapters) sb.append("""    <itemref idref="ch${ch.id}"/>""").append('\n')
        sb.append("  </spine>\n")
        if (coverRel != null) {
            sb.append("""  <guide><reference type="cover" title="封面" href="cover.xhtml"/></guide>""").append('\n')
        }
        sb.append("</package>\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- 工具

    fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun nowStamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(java.util.Date())
    }

    private fun nowIso(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    private const val STYLE_CSS = """body {
  margin: 6% 5%;
  line-height: 1.8;
  font-family: "Noto Serif CJK SC", "Source Han Serif SC", serif;
  text-align: justify;
  color: #1a1a1a;
}
h1 {
  font-size: 1.5em;
  font-weight: bold;
  line-height: 1.5;
  text-align: center;
  margin: 1.2em 0 1.4em;
}
h2 {
  font-size: 1.25em;
  font-weight: bold;
  line-height: 1.5;
  text-align: center;
  margin: 1.2em 0 1.2em;
}
p {
  text-indent: 2em;
  margin: 0 0 0.5em;
}
.cover-page, .cover {
  margin: 0;
  padding: 0;
  text-align: center;
}
.cover img {
  max-width: 100%;
  max-height: 100%;
}
"""
}
