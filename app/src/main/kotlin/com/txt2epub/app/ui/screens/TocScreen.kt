@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.txt2epub.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txt2epub.app.core.TocDetector
import com.txt2epub.app.model.TocEntry
import com.txt2epub.app.ui.components.ChipText
import com.txt2epub.app.ui.components.Hint
import com.txt2epub.app.ui.components.SectionCard
import com.txt2epub.app.ui.components.StatGrid
import com.txt2epub.app.vm.ConvertViewModel

@Composable
fun TocScreen(vm: ConvertViewModel, onManageRules: () -> Unit) {
    var showOptions by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        SectionCard(
            title = "目录识别",
            trailing = { TextButton(onClick = onManageRules) { Text("规则管理") } }
        ) {
            StatGrid(
                listOf(
                    "命中目录" to "${vm.toc.size}",
                    "章节数" to "${vm.chapters.size}",
                    "启用规则" to "${vm.rules.count { it.enabled }}/${vm.rules.size}",
                    "正文字数" to "${vm.totalWords}",
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.rescanAsync() }) { Text("重新识别") }
                TextButton(onClick = { showOptions = !showOptions }) {
                    Text(if (showOptions) "收起参数" else "识别参数")
                }
            }
            if (showOptions) {
                Spacer(Modifier.height(6.dp))
                OptionsPanel(vm)
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(
            modifier = Modifier.weight(1f, fill = false),
            title = "目录 (${vm.toc.size})",
            trailing = {
                Row {
                    TextButton(onClick = { vm.setAllIncluded(true) }) { Text("全选", fontSize = 12.sp) }
                    TextButton(onClick = { vm.setAllIncluded(false) }) { Text("全不选", fontSize = 12.sp) }
                }
            }
        ) {
            if (vm.toc.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Hint("没有识别到目录。可以调整规则或参数，也可以在规则管理里新增正则。")
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(vm.toc.size) { i ->
                        TocRow(
                            entry = vm.toc[i],
                            onToggle = { vm.updateEntry(i, included = it) },
                            onEdit = { editing = i }
                        )
                        if (i < vm.toc.size - 1) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { idx ->
        val e = vm.toc.getOrNull(idx) ?: return@let
        EntryEditor(
            entry = e,
            onDismiss = { editing = null },
            onSave = { title, level ->
                vm.updateEntry(idx, title = title, level = level)
                editing = null
            }
        )
    }
}

@Composable
private fun TocRow(entry: TocEntry, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(
            checked = entry.included,
            onCheckedChange = onToggle
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = if (entry.included) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "第 ${entry.lineIndex + 1} 行",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ChipText(
            text = if (entry.effectiveLevel() >= 2) "二级" else "一级",
            selected = true,
            onClick = onEdit
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "✎",
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun OptionsPanel(vm: ConvertViewModel) {
    val o = vm.options
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SliderRow(
            label = "标题最长字符",
            value = o.maxTitleLength.toFloat(),
            range = 20f..120f,
            display = "${o.maxTitleLength} 字",
            onChange = { vm.updateOptions(o.copy(maxTitleLength = it.toInt())) }
        )
        SliderRow(
            label = "跳过开头行数",
            value = o.skipFirstLines.toFloat(),
            range = 0f..1000f,
            display = "${o.skipFirstLines} 行",
            onChange = { vm.updateOptions(o.copy(skipFirstLines = it.toInt())) }
        )
        SliderRow(
            label = "最小章节字数",
            value = o.minChapterChars.toFloat(),
            range = 0f..1000f,
            display = "${o.minChapterChars} 字",
            onChange = { vm.updateOptions(o.copy(minChapterChars = it.toInt())) }
        )
        SliderRow(
            label = "无目录时按字数分章",
            value = o.fallbackChunkChars.toFloat(),
            range = 2000f..20000f,
            display = "${o.fallbackChunkChars} 字",
            onChange = { vm.updateOptions(o.copy(fallbackChunkChars = it.toInt())) }
        )
        SwitchRow("过滤广告与分隔线", o.cleanNoise) {
            vm.updateOptions(o.copy(cleanNoise = it))
        }
        SwitchRow("开头内容作为「简介」章节", o.includePreamble) {
            vm.updateOptions(o.copy(includePreamble = it))
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(display, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = ((range.endInclusive - range.start) / 20f).toInt().coerceAtLeast(1)
        )
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EntryEditor(
    entry: TocEntry,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var text by remember { mutableStateOf(entry.title) }
    var level by remember { mutableStateOf(entry.effectiveLevel()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑目录项", fontSize = 17.sp) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("标题") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipText("一级标题", level == 1) { level = 1 }
                    ChipText("二级标题", level == 2) { level = 2 }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim(), level) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PlaceholderBox(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
