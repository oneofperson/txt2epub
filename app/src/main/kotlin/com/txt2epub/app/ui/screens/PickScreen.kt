@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.txt2epub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txt2epub.app.ui.components.ChipText
import com.txt2epub.app.ui.components.Hint
import com.txt2epub.app.ui.components.SectionCard
import com.txt2epub.app.ui.components.StatGrid
import com.txt2epub.app.vm.ConvertViewModel

private val COMMON_CHARSETS = listOf("UTF-8", "GB18030", "GBK", "Big5", "UTF-16LE", "UTF-16BE")

@Composable
fun PickScreen(vm: ConvertViewModel, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedCard(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", fontSize = 40.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "选择 TXT 文件",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Hint("支持 UTF-8 / GBK / GB18030 / Big5 / UTF-16 自动识别")
                }
            }
        }

        if (vm.fileName.isNotBlank()) {
            SectionCard(title = "文件信息") {
                StatGrid(
                    listOf(
                        "文件名" to vm.fileName,
                        "大小" to formatSize(vm.fileSize),
                        "自动识别" to vm.detectedCharset,
                        "当前编码" to vm.selectedCharset,
                        "行数" to "${vm.lines.size}",
                        "字符数" to "${vm.charCount}",
                    )
                )
            }

            SectionCard(title = "编码（乱码时手动切换）") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COMMON_CHARSETS.forEach { cs ->
                        ChipText(cs, cs == vm.selectedCharset) { vm.setCharset(cs) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Hint("中文网络小说多为 GBK / GB18030，若正文出现方块或问号，请切换到对应编码后重新识别。")
            }
        } else {
            SectionCard(title = "使用说明") {
                Hint(
                    "1. 选择要转换的 TXT 文件\n" +
                        "2. 自动识别章节目录，可在「规则管理」里增删正则\n" +
                        "3. 修改书名、作者等信息，选择或生成封面\n" +
                        "4. 一键转换并导出 EPUB"
                )
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "-"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.2f MB", bytes / 1024.0 / 1024.0)
}
