@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.txt2epub.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txt2epub.app.core.CoverGenerator
import com.txt2epub.app.ui.components.ChipText
import com.txt2epub.app.ui.components.Hint
import com.txt2epub.app.ui.components.SectionCard
import com.txt2epub.app.vm.ConvertViewModel
import com.txt2epub.app.vm.CoverMode

@Composable
fun MetaScreen(
    vm: ConvertViewModel,
    onPickImage: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard(title = "书籍信息") {
            Field("书名", vm.meta.title) { vm.updateMeta { title = it } }
            Field("作者", vm.meta.author) { vm.updateMeta { author = it } }
            Field("语言", vm.meta.language) { vm.updateMeta { language = it } }
            Field("出版方 / 来源", vm.meta.publisher) { vm.updateMeta { publisher = it } }
            Field("简介", vm.meta.description, singleLine = false) { vm.updateMeta { description = it } }
        }

        SectionCard(title = "封面") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipText("自动生成", vm.coverMode == CoverMode.AUTO) { vm.changeCoverMode(CoverMode.AUTO) }
                ChipText("选择图片", vm.coverMode == CoverMode.IMAGE) {
                    vm.changeCoverMode(CoverMode.IMAGE)
                    onPickImage()
                }
                ChipText("无封面", vm.coverMode == CoverMode.NONE) { vm.changeCoverMode(CoverMode.NONE) }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val bmp = vm.coverBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "封面预览",
                        modifier = Modifier
                            .width(135.dp)
                            .height(180.dp)
                    )
                } else {
                    Box(
                        Modifier
                            .width(135.dp)
                            .height(180.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Hint("无封面") }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (vm.coverMode) {
                CoverMode.AUTO -> {
                    Hint("配色主题")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CoverGenerator.themes.forEachIndexed { i, theme ->
                            val selected = i == vm.themeIndex
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(
                                                androidx.compose.ui.graphics.Color(theme.from),
                                                androidx.compose.ui.graphics.Color(theme.to)
                                            )
                                        )
                                    )
                                    .then(
                                        if (selected) Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { vm.setTheme(i) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.refreshCover() }) { Text("重新生成封面") }
                }

                CoverMode.IMAGE -> {
                    Button(onClick = onPickImage) { Text("从相册选择图片") }
                    Spacer(Modifier.height(6.dp))
                    Hint("图片会按 3:4 居中裁剪并缩放到 600×800。")
                }

                CoverMode.NONE -> Hint("不生成封面，部分阅读器会显示默认图标。")
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun MetaSummary(vm: ConvertViewModel) {
    Text(vm.meta.title.ifBlank { "未命名" }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Text(
        "${vm.meta.author.ifBlank { "佚名" }} · ${vm.chapters.size} 章 · ${vm.totalWords} 字",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
