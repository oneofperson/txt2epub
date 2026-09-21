@file:OptIn(ExperimentalMaterial3Api::class)

package com.txt2epub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txt2epub.app.ui.components.Hint
import com.txt2epub.app.ui.components.SectionCard
import com.txt2epub.app.ui.components.StatGrid
import com.txt2epub.app.vm.ConvertViewModel

@Composable
fun ExportScreen(
    vm: ConvertViewModel,
    onConvert: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard(title = "转换") {
            StatGrid(
                listOf(
                    "书名" to vm.meta.title.ifBlank { "未命名" },
                    "作者" to vm.meta.author.ifBlank { "佚名" },
                    "章节" to "${vm.chapters.size} 章",
                    "字数" to "${vm.totalWords} 字",
                    "封面" to when (vm.coverMode) {
                        com.txt2epub.app.vm.CoverMode.AUTO -> "自动生成"
                        com.txt2epub.app.vm.CoverMode.IMAGE -> "自定义图片"
                        com.txt2epub.app.vm.CoverMode.NONE -> "无"
                    },
                    "大小" to formatSize(vm.outputSize),
                )
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConvert,
                enabled = !vm.exporting && vm.chapters.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.exporting) "转换中…" else "开始转换")
            }
            if (vm.exporting) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            }
            if (vm.chapters.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Hint("还没有章节内容，请回到上一步检查目录识别结果。")
            }
        }

        vm.outputFile?.let { file ->
            SectionCard(title = "已生成") {
                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Hint("${formatSize(vm.outputSize)} · 位于应用私有目录，建议保存到手机存储")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存到…") }
                Row {
                    TextButton(onClick = onOpen) { Text("用其他应用打开") }
                    TextButton(onClick = onConvert) { Text("重新转换") }
                }
            }
        }
    }
}
