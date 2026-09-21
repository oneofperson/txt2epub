package com.txt2epub.app

import com.txt2epub.app.core.BuiltInRules
import com.txt2epub.app.core.EncodingDetector
import com.txt2epub.app.core.EpubBuilder
import com.txt2epub.app.core.TocDetector
import com.txt2epub.app.model.BookMeta
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 本地单元测试：用样例小说跑通「编码识别 → 目录识别 → 章节切分 → EPUB 打包」全链路，
 * 产出的 epub 落在 build/test-output/ 下，可直接用阅读器或解压校验。
 */
class CorePipelineTest {

    /** 从 src/test/resources 读样例，避免在测试里写死本机绝对路径。 */
    private fun readSample(name: String): ByteArray {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "缺少测试资源 $name"
        }
        return stream.use { it.readBytes() }
    }

    @Test
    fun pipelineProducesValidEpub() {
        val bytes = readSample("sample_utf8.txt")
        val charset = EncodingDetector.detect(bytes)
        println("[test] charset=$charset")
        assertTrue(charset.isNotBlank())

        val text = EncodingDetector.decode(bytes, charset)
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        println("[test] lines=${lines.size}")

        val options = TocDetector.Options()
        val toc = TocDetector.detect(lines, BuiltInRules.defaults(), options)
        println("[test] toc=${toc.size}")
        toc.forEach { println("   L${it.level} ${it.title} @${it.lineIndex}") }

        val chapters = TocDetector.split(lines, toc, options)
        println("[test] chapters=${chapters.size}")
        chapters.forEach { println("   #${it.id} L${it.level} ${it.title} (${it.charCount}字)") }

        assertTrue("应识别到目录", toc.size >= 10)
        assertTrue("应切分出章节", chapters.size >= 8)
        assertTrue("章节数不应被过度合并", chapters.size >= toc.size - 3)
        assertTrue("首个标题前的内容应作为「简介」章放在最前", chapters.first().title == "简介")

        val out = File("build/test-output/sample.epub")
        out.parentFile?.mkdirs()
        out.outputStream().use { fos ->
            EpubBuilder.build(
                fos,
                BookMeta(title = "山中有虎", author = "林下行"),
                chapters,
                null
            ) { _, _ -> }
        }
        println("[test] epub=${out.absolutePath} size=${out.length()}")
        assertTrue(out.length() > 500)

        // 校验 zip 结构：mimetype 必须存在且未压缩
        java.util.zip.ZipFile(out).use { zip ->
            val mimeEntry = zip.getEntry("mimetype")
            assertTrue("缺少 mimetype", mimeEntry != null)
            if (mimeEntry != null) {
                val mimeText = zip.getInputStream(mimeEntry).bufferedReader().readText()
                println("[test] mimetype=$mimeText, method=${mimeEntry.method}")
                assertTrue(mimeText == "application/epub+zip")
                assertTrue("mimetype 必须是 STORED(0)", mimeEntry.method == 0)
            }
            val names = zip.entries().toList().map { it.name }
            println("[test] entries=${names.size}")
            assertTrue(names.contains("META-INF/container.xml"))
            assertTrue(names.contains("OEBPS/content.opf"))
            assertTrue(names.contains("OEBPS/toc.ncx"))
            assertTrue(names.contains("OEBPS/nav.xhtml"))
            assertTrue(names.any { it.startsWith("OEBPS/text/ch") })
        }
    }

    @Test
    fun detectsGbkEncodedFile() {
        val bytes = readSample("sample_gbk.txt")
        val charset = EncodingDetector.detect(bytes)
        println("[test] gbk charset=$charset")
        assertTrue("应识别为中文编码，实际是 $charset", charset == "GB18030" || charset == "GBK")

        val text = EncodingDetector.decode(bytes, charset)
        assertTrue(text.contains("第一章"))
        assertFalse("解码后不应有乱码替换符", text.contains('\uFFFD'))
    }

    @Test
    fun rulesMatchExpectedTitles() {
        val text = """
            楔子
            这是一段引子。
            第一卷 初入太行
            第一章 少年下山
            正文内容第一行。
            正文内容第二行。
            第二章 遇见
            又一段正文。
            尾声
            结束。
        """.trimIndent()
        val lines = text.split('\n')
        val toc = TocDetector.detect(lines, BuiltInRules.defaults(), TocDetector.Options())
        val titles = toc.map { it.title }
        println("[test] matched=$titles")
        assertTrue(titles.any { it.contains("楔子") })
        assertTrue(titles.any { it.contains("第一卷") })
        assertTrue(titles.any { it.contains("第一章") })
        assertTrue(titles.any { it.contains("第二章") })
        assertTrue(titles.any { it.contains("尾声") })
    }
}
