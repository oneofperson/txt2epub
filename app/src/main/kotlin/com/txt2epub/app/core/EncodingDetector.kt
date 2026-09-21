package com.txt2epub.app.core

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/**
 * TXT 编码自动识别。
 * 中文网络小说常见 GBK / GB18030，也有 UTF-8 与 UTF-16，这里做 BOM 判定 + 严格解码验证 + 替换符统计。
 */
object EncodingDetector {

    val CANDIDATES = listOf(
        "UTF-8", "GB18030", "GBK", "Big5", "UTF-16LE", "UTF-16BE", "ISO-8859-1", "windows-1252"
    )

    private const val PROBE = 256 * 1024

    fun detect(bytes: ByteArray): String {
        // 1. BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return "UTF-8"
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return "UTF-16LE"
            if (b0 == 0xFE && b1 == 0xFF) return "UTF-16BE"
        }
        if (bytes.isEmpty()) return "UTF-8"

        val probe = if (bytes.size > PROBE) bytes.copyOf(PROBE) else bytes

        // 2. UTF-16 启发式：大量 NUL 字节
        val evenNul = countNul(probe, 1)
        val oddNul = countNul(probe, 0)
        if ((evenNul + oddNul) > probe.size / 4 && kotlin.math.max(evenNul, oddNul) > probe.size / 6) {
            return if (evenNul >= oddNul) "UTF-16LE" else "UTF-16BE"
        }

        // 3. UTF-8 严格解码
        if (strictDecode(probe, "UTF-8") != null) return "UTF-8"

        // 4. 其他编码按替换符数量打分
        var best = "GB18030"
        var bestScore = Int.MAX_VALUE
        for (c in listOf("GB18030", "Big5", "windows-1252")) {
            val score = replacementCount(probe, c)
            if (score < bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    /** 按指定编码解码，自动去掉 BOM。 */
    fun decode(bytes: ByteArray, charset: String): String {
        val cs = try {
            Charset.forName(charset)
        } catch (_: Throwable) {
            Charsets.UTF_8
        }
        val start = bomLength(bytes)
        val body = if (start > 0) bytes.copyOfRange(start, bytes.size) else bytes
        val decoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(ByteBuffer.wrap(body)).toString()
    }

    private fun bomLength(bytes: ByteArray): Int {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return 3
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return 2
        }
        return 0
    }

    private fun strictDecode(bytes: ByteArray, charset: String): CharBuffer? = try {
        val cs = Charset.forName(charset)
        val dec = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        dec.decode(ByteBuffer.wrap(bytes))
    } catch (_: Throwable) {
        null
    }

    private fun replacementCount(bytes: ByteArray, charset: String): Int {
        val cs = try {
            Charset.forName(charset)
        } catch (_: Throwable) {
            return Int.MAX_VALUE
        }
        val dec = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return try {
            val cb = dec.decode(ByteBuffer.wrap(bytes))
            var n = 0
            for (i in 0 until cb.length) if (cb[i] == '\uFFFD') n++
            n
        } catch (_: Throwable) {
            Int.MAX_VALUE
        }
    }

    private fun countNul(bytes: ByteArray, startOffset: Int): Int {
        var n = 0
        var i = startOffset
        while (i < bytes.size) {
            if (bytes[i] == 0.toByte()) n++
            i += 2
        }
        return n
    }
}
