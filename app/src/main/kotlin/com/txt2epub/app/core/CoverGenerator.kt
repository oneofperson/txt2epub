package com.txt2epub.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * 封面处理：自动生成（渐变 + 排版）与已有图片裁剪。
 */
object CoverGenerator {

    data class Theme(val name: String, val from: Int, val to: Int, val ink: Int, val accent: Int)

    val themes: List<Theme> = listOf(
        Theme("靛蓝", 0xFF4F46E5.toInt(), 0xFF7C3AED.toInt(), 0xFFFFFFFF.toInt(), 0xFFC7D2FE.toInt()),
        Theme("墨黑", 0xFF374151.toInt(), 0xFF111827.toInt(), 0xFFF9FAFB.toInt(), 0xFF9CA3AF.toInt()),
        Theme("朱砂", 0xFFDC2626.toInt(), 0xFF7F1D1D.toInt(), 0xFFFFF7ED.toInt(), 0xFFFECACA.toInt()),
        Theme("青竹", 0xFF0F766E.toInt(), 0xFF064E3B.toInt(), 0xFFECFDF5.toInt(), 0xFF99F6E4.toInt()),
        Theme("秋橘", 0xFFEA580C.toInt(), 0xFFB45309.toInt(), 0xFFFFFBEB.toInt(), 0xFFFED7AA.toInt()),
        Theme("藏蓝", 0xFF1E3A8A.toInt(), 0xFF172554.toInt(), 0xFFEFF6FF.toInt(), 0xFFBFDBFE.toInt()),
        Theme("藕荷", 0xFFBE185D.toInt(), 0xFF831843.toInt(), 0xFFFDF2F8.toInt(), 0xFFFBCFE8.toInt()),
        Theme("沙金", 0xFFB45309.toInt(), 0xFF78350F.toInt(), 0xFFFFFBEB.toInt(), 0xFFFDE68A.toInt()),
    )

    fun generate(
        title: String,
        author: String,
        themeIndex: Int,
        width: Int = 600,
        height: Int = 800,
    ): Bitmap {
        val theme = themes[themeIndex.coerceIn(themes.indices)]
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()

        // 渐变背景
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w, h, theme.from, theme.to, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, bg)

        // 装饰光斑
        val deco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 26
        }
        canvas.drawCircle(w * 0.88f, h * 0.13f, w * 0.45f, deco)
        canvas.drawCircle(w * 0.08f, h * 0.9f, w * 0.34f, deco)

        // 内描边
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            alpha = 110
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(30f, 30f, w - 30f, h - 30f, frame)

        // 顶部装饰细线
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            alpha = 200
            strokeWidth = 4f
        }
        canvas.drawLine(w * 0.36f, h * 0.145f, w * 0.64f, h * 0.145f, line)

        // 书名：自动缩放字号
        val titleText = fitText(title.ifBlank { "未命名" }, 60)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.ink
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val maxW = (w - 140f).toInt()
        val maxH = (h * 0.46f).toInt()
        val layout = fitLayout(titleText, tp, maxW, maxH)
        canvas.save()
        val titleTop = (h * 0.5f - layout.height / 2f).coerceAtLeast(h * 0.18f)
        canvas.translate((w - maxW) / 2f, titleTop)
        layout.draw(canvas)
        canvas.restore()

        // 作者
        val authorText = fitText(author.ifBlank { "佚名" }, 24)
        val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.ink
            alpha = 215
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val line2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            alpha = 170
            strokeWidth = 2f
        }
        val baseY = h - 108f
        canvas.drawLine(w * 0.42f, baseY - 46f, w * 0.58f, baseY - 46f, line2)
        canvas.drawText(authorText, w / 2f, baseY, ap)

        return bmp
    }

    private fun fitLayout(text: String, paint: TextPaint, maxW: Int, maxH: Int): StaticLayout {
        var size = 80f
        while (size > 20f) {
            paint.textSize = size
            val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            if (l.height <= maxH) return l
            size -= 4f
        }
        paint.textSize = 20f
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    private fun fitText(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max) + "…"

    fun toJpeg(bmp: Bitmap, quality: Int = 92): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    fun toPng(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    /** 把用户选的图按 3:4 居中裁剪并缩放为标准封面尺寸。 */
    fun fitImage(src: Bitmap, width: Int = 600, height: Int = 800): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val scale = max(width.toFloat() / src.width, height.toFloat() / src.height)
        val sw = width / scale
        val sh = height / scale
        val sx = ((src.width - sw) / 2f).coerceAtLeast(0f)
        val sy = ((src.height - sh) / 2f).coerceAtLeast(0f)
        val srcRect = Rect(
            sx.toInt(),
            sy.toInt(),
            (sx + sw).toInt().coerceAtMost(src.width),
            (sy + sh).toInt().coerceAtMost(src.height)
        )
        val dstRect = Rect(0, 0, width, height)
        canvas.drawBitmap(src, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
