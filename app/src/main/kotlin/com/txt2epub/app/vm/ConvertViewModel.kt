package com.txt2epub.app.vm

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.txt2epub.app.core.BuiltInRules
import com.txt2epub.app.core.CoverGenerator
import com.txt2epub.app.core.EncodingDetector
import com.txt2epub.app.core.EpubBuilder
import com.txt2epub.app.core.TocDetector
import com.txt2epub.app.data.RuleStore
import com.txt2epub.app.model.BookMeta
import com.txt2epub.app.model.Chapter
import com.txt2epub.app.model.ChapterRule
import com.txt2epub.app.model.Step
import com.txt2epub.app.model.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class CoverMode { AUTO, IMAGE, NONE }

class ConvertViewModel(app: Application) : AndroidViewModel(app) {

    private val ruleStore = RuleStore(app)

    // ---------------- 流程 ----------------
    var step by mutableStateOf(Step.PICK)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // ---------------- 源文件 ----------------
    var sourceUri by mutableStateOf<Uri?>(null)
        private set
    var fileName by mutableStateOf("")
        private set
    var fileSize by mutableStateOf(0L)
        private set
    var detectedCharset by mutableStateOf("")
        private set
    var selectedCharset by mutableStateOf("")
        private set
    var lines by mutableStateOf<List<String>>(emptyList())
        private set
    var charCount by mutableStateOf(0)
        private set
    private var rawBytes: ByteArray? = null

    // ---------------- 规则与目录 ----------------
    var rules by mutableStateOf<List<ChapterRule>>(BuiltInRules.defaults())
        private set
    var options by mutableStateOf(TocDetector.Options())
        private set
    var toc by mutableStateOf<List<TocEntry>>(emptyList())
        private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set

    // ---------------- 元数据与封面 ----------------
    var meta by mutableStateOf(BookMeta())
        private set
    var coverMode by mutableStateOf(CoverMode.AUTO)
    var themeIndex by mutableStateOf(0)
    var customCover by mutableStateOf<Bitmap?>(null)
    var coverBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // ---------------- 导出 ----------------
    var exporting by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var outputFile by mutableStateOf<File?>(null)
        private set
    var outputSize by mutableStateOf(0L)
        private set

    val totalWords: Int get() = chapters.sumOf { it.charCount }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = ruleStore.rules.first()
            withContext(Dispatchers.Main) { rules = saved }
        }
    }

    fun goTo(target: Step) {
        step = target
    }

    fun clearMessage() { message = null }

    fun notify(text: String) { message = text }

    // ------------------------------------------------------------------ 载入

    fun load(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val cr = context.contentResolver
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取该文件")
                if (bytes.isEmpty()) throw IllegalStateException("文件是空的")

                try {
                    cr.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) { }

                rawBytes = bytes
                sourceUri = uri
                fileSize = bytes.size.toLong()
                fileName = queryName(cr, uri) ?: "book.txt"
                detectedCharset = EncodingDetector.detect(bytes)
                selectedCharset = detectedCharset

                applyCharset(selectedCharset)

                val base = fileName.substringBeforeLast('.').ifBlank { "未命名" }
                meta = BookMeta(title = base)
                rescan()
                refreshCover()
                withContext(Dispatchers.Main) { step = Step.TOC }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { message = t.message ?: "读取失败" }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    fun setCharset(cs: String) {
        if (rawBytes == null) return
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                selectedCharset = cs
                applyCharset(cs)
                rescan()
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    private fun applyCharset(cs: String) {
        val bytes = rawBytes ?: return
        val text = EncodingDetector.decode(bytes, cs)
        charCount = text.length
        lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
    }

    private fun queryName(cr: ContentResolver, uri: Uri): String? = try {
        cr.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }
    } catch (_: Throwable) {
        null
    }

    // ------------------------------------------------------------------ 规则

    fun updateOptions(newOptions: TocDetector.Options) {
        options = newOptions
        rescanAsync()
    }

    fun toggleRule(id: String, enabled: Boolean) {
        rules = rules.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persistRules()
        rescanAsync()
    }

    fun addRule(name: String, pattern: String, level: Int) {
        val order = (rules.maxOfOrNull { it.order } ?: 100) + 10
        rules = rules + ChapterRule(
            name = name.ifBlank { "自定义规则" },
            pattern = pattern,
            level = level,
            enabled = true,
            builtIn = false,
            order = order
        )
        persistRules()
        rescanAsync()
    }

    fun updateRule(rule: ChapterRule) {
        rules = rules.map { if (it.id == rule.id) rule else it }
        persistRules()
        rescanAsync()
    }

    fun deleteRule(id: String) {
        rules = rules.filter { it.id != id }
        persistRules()
        rescanAsync()
    }

    fun resetRules() {
        rules = BuiltInRules.defaults()
        persistRules()
        rescanAsync()
    }

    private fun persistRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try { ruleStore.save(rules) } catch (_: Throwable) { }
        }
    }

    // ------------------------------------------------------------------ 目录

    fun rescanAsync() {
        viewModelScope.launch(Dispatchers.IO) { rescan() }
    }

    private suspend fun rescan() {
        val detected = TocDetector.detect(lines, rules, options)
        val built = TocDetector.split(lines, detected, options)
        withContext(Dispatchers.Main) {
            toc = detected
            chapters = built
        }
    }

    fun updateEntry(index: Int, title: String? = null, level: Int? = null, included: Boolean? = null) {
        if (index !in toc.indices) return
        val e = toc[index]
        if (title != null) e.title = title
        if (level != null) e.manualLevel = level
        if (included != null) e.included = included
        rescanAsync()
    }

    fun setAllIncluded(included: Boolean) {
        toc.forEach { it.included = included }
        rescanAsync()
    }

    // ------------------------------------------------------------------ 元数据 / 封面

    fun updateMeta(block: BookMeta.() -> Unit) {
        val m = meta.copy()
        m.block()
        meta = m
        if (coverMode == CoverMode.AUTO) refreshCover()
    }

    fun changeCoverMode(mode: CoverMode) {
        coverMode = mode
        refreshCover()
    }

    fun setTheme(index: Int) {
        themeIndex = index
        if (coverMode == CoverMode.AUTO) refreshCover()
    }

    fun applyCustomCover(bitmap: Bitmap?) {
        customCover = bitmap
        if (bitmap != null) {
            coverMode = CoverMode.IMAGE
            coverBitmap = CoverGenerator.fitImage(bitmap)
        }
    }

    fun refreshCover() {
        coverBitmap = when (coverMode) {
            CoverMode.AUTO -> CoverGenerator.generate(meta.title, meta.author, themeIndex)
            CoverMode.IMAGE -> customCover?.let { CoverGenerator.fitImage(it) }
            CoverMode.NONE -> null
        }
    }

    // ------------------------------------------------------------------ 导出

    fun convert(context: Context, onDone: (File) -> Unit = {}) {
        if (chapters.isEmpty()) {
            message = "没有可导出的章节"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            exporting = true
            progress = 0f
            outputFile = null
            try {
                refreshCover()
                val bmp = coverBitmap
                val cover = bmp?.let { EpubBuilder.Cover(CoverGenerator.toJpeg(it), "image/jpeg") }

                val dir = File(context.filesDir, "epub").apply { mkdirs() }
                val file = File(dir, "${safeFileName(meta.title)}.epub")
                FileOutputStream(file).use { fos ->
                    EpubBuilder.build(fos, meta, chapters, cover) { done, total ->
                        progress = done.toFloat() / total.toFloat().coerceAtLeast(1f)
                    }
                }
                outputFile = file
                outputSize = file.length()
                withContext(Dispatchers.Main) {
                    step = Step.EXPORT
                    onDone(file)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { message = t.message ?: "转换失败" }
            } finally {
                withContext(Dispatchers.Main) { exporting = false }
            }
        }
    }

    /** 把已生成的 epub 写到用户选择的位置（SAF）。 */
    fun saveTo(context: Context, dest: Uri, onResult: (Boolean, String?) -> Unit) {
        val file = outputFile
        if (file == null) {
            onResult(false, "还没有生成 EPUB")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(dest)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("无法写入目标位置")
                withContext(Dispatchers.Main) { onResult(true, null) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onResult(false, t.message) }
            }
        }
    }

    companion object {
        fun safeFileName(name: String): String {
            val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\\n\r\t]"), "_").trim()
            val base = if (cleaned.length > 60) cleaned.substring(0, 60) else cleaned
            return base.ifBlank { "book" } + "_" +
                java.text.SimpleDateFormat("MMddHHmm", Locale.US).format(java.util.Date())
        }
    }
}
