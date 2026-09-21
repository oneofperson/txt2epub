@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.txt2epub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txt2epub.app.core.TocDetector
import com.txt2epub.app.model.ChapterRule
import com.txt2epub.app.ui.components.ChipText
import com.txt2epub.app.ui.components.Hint
import com.txt2epub.app.ui.components.SectionCard
import com.txt2epub.app.vm.ConvertViewModel

@Composable
fun RulesScreen(vm: ConvertViewModel) {
    var editing by remember { mutableStateOf<ChapterRule?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionCard(
            modifier = Modifier.weight(1f, fill = false),
            title = "目录规则（${vm.rules.size}）"
        ) {
            Hint("按序号从上到下匹配，命中即停止。关闭不需要的规则可以减少误判。")
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(vm.rules.size) { i ->
                    RuleRow(
                        rule = vm.rules[i],
                        onToggle = { vm.toggleRule(vm.rules[i].id, it) },
                        onEdit = { editing = vm.rules[i] },
                        onDelete = { if (!vm.rules[i].builtIn) vm.deleteRule(vm.rules[i].id) }
                    )
                    if (i < vm.rules.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { editing = ChapterRule(name = "", pattern = "", level = 1, builtIn = false, order = 999) },
                modifier = Modifier.weight(1f)
            ) { Text("新增规则") }
            TextButton(onClick = { vm.resetRules() }) { Text("恢复默认") }
        }
        Spacer(Modifier.height(8.dp))
    }

    editing?.let { rule ->
        RuleEditor(
            rule = rule,
            lines = vm.lines,
            onDismiss = { editing = null },
            onSave = { saved ->
                if (rule.builtIn) vm.updateRule(saved)
                else if (vm.rules.any { it.id == saved.id }) vm.updateRule(saved)
                else vm.addRule(saved.name, saved.pattern, saved.level)
                editing = null
            }
        )
    }
}

@Composable
private fun RuleRow(
    rule: ChapterRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.name.ifBlank { "未命名规则" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (rule.level >= 2) "二级" else "一级",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (rule.builtIn) {
                    Spacer(Modifier.width(6.dp))
                    Text("内置", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                rule.pattern,
                fontSize = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onEdit) { Text("编辑", fontSize = 12.sp) }
        if (!rule.builtIn) {
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RuleEditor(
    rule: ChapterRule,
    lines: List<String>,
    onDismiss: () -> Unit,
    onSave: (ChapterRule) -> Unit,
) {
    var name by remember { mutableStateOf(rule.name) }
    var pattern by remember { mutableStateOf(rule.pattern) }
    var level by remember { mutableStateOf(rule.level) }
    var hits by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    var valid by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.name.isBlank()) "新增规则" else "编辑规则", fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        valid = runCatching { Regex(it) }.isSuccess
                    },
                    label = { Text("正则表达式") },
                    maxLines = 3,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!valid) {
                    Hint("正则表达式语法有误", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipText("一级标题", level == 1) { level = 1 }
                    ChipText("二级标题", level == 2) { level = 2 }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { hits = TocDetector.testPattern(lines, pattern, 20) },
                    enabled = valid && pattern.isNotBlank()
                ) { Text("在当前文件中试一试") }

                hits?.let { list ->
                    Spacer(Modifier.height(6.dp))
                    if (list.isEmpty()) {
                        Hint("没有命中任何行")
                    } else {
                        Text(
                            "命中 ${list.size} 处：",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            list.take(8).joinToString("\n") { it.second },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 8
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && pattern.isNotBlank()) {
                        onSave(rule.copy(name = name, pattern = pattern, level = level))
                    }
                },
                enabled = valid && pattern.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
