@file:OptIn(ExperimentalMaterial3Api::class)

package com.txt2epub.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.txt2epub.app.model.Step
import com.txt2epub.app.ui.screens.ExportScreen
import com.txt2epub.app.ui.screens.MetaScreen
import com.txt2epub.app.ui.screens.PickScreen
import com.txt2epub.app.ui.screens.RulesScreen
import com.txt2epub.app.ui.screens.TocScreen
import com.txt2epub.app.vm.ConvertViewModel

@Composable
fun AppRoot(
    incomingUri: Uri?,
    vm: ConvertViewModel = viewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showRules by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(incomingUri) {
        incomingUri?.let { vm.load(context, it) }
    }

    val pickTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.load(context, it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.applyCustomCover(loadBitmap(context, it)) }
    }
    val saveEpub = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        uri?.let { dest ->
            vm.saveTo(context, dest) { ok, err ->
                vm.notify(if (ok) "已保存到你选择的位置" else (err ?: "保存失败"))
            }
        }
    }

    val hasText = vm.lines.isNotEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (showRules) "目录规则管理" else "Txt 转 Epub",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (showRules) {
                        TextButton(onClick = { showRules = false }) { Text("返回") }
                    }
                },
                actions = {
                    if (!showRules && hasText) {
                        TextButton(onClick = { showRules = true }) { Text("规则") }
                    }
                }
            )
        },
        bottomBar = {
            if (!showRules) {
                Column {
                    StepBar(
                        current = vm.step,
                        enabled = { step ->
                            when (step) {
                                Step.PICK -> true
                                Step.TOC -> hasText
                                Step.META -> hasText
                                Step.EXPORT -> hasText
                            }
                        },
                        onSelect = { vm.goTo(it) }
                    )
                    ActionRow(
                        step = vm.step,
                        hasText = hasText,
                        hasOutput = vm.outputFile != null,
                        exporting = vm.exporting,
                        onPick = { pickTxt.launch(arrayOf("text/plain")) },
                        onPrev = { vm.goTo(Step.values()[vm.step.ordinal - 1]) },
                        onNext = { vm.goTo(Step.values()[vm.step.ordinal + 1]) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                showRules -> RulesScreen(vm)
                vm.step == Step.PICK -> PickScreen(vm) { pickTxt.launch(arrayOf("text/plain")) }
                vm.step == Step.TOC -> TocScreen(vm) { showRules = true }
                vm.step == Step.META -> MetaScreen(vm) { pickImage.launch("image/*") }
                vm.step == Step.EXPORT -> ExportScreen(
                    vm = vm,
                    onConvert = { vm.convert(context) },
                    onSave = { saveEpub.launch("${vm.meta.title.ifBlank { "book" }}.epub") },
                    onOpen = { openEpub(context, vm.outputFile) }
                )
            }
        }
    }
}

@Composable
private fun StepBar(
    current: Step,
    enabled: (Step) -> Boolean,
    onSelect: (Step) -> Unit,
) {
    Surface(tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Step.values().forEachIndexed { i, s ->
                val active = s == current
                val ok = enabled(s)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = ok) { onSelect(s) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 12.sp,
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        s.label,
                        fontSize = 11.sp,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (ok) 1f else 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    step: Step,
    hasText: Boolean,
    hasOutput: Boolean,
    exporting: Boolean,
    onPick: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step == Step.PICK) {
                Spacer(Modifier.width(1.dp))
            } else {
                TextButton(onClick = onPrev) { Text("上一步") }
            }

            when (step) {
                Step.PICK -> Button(onClick = onPick, enabled = true, modifier = Modifier.fillMaxWidth()) {
                    Text(if (hasText) "下一步：识别目录" else "选择 TXT 文件")
                }
                Step.TOC -> Button(onClick = onNext, enabled = hasText) { Text("下一步：书籍信息") }
                Step.META -> Button(onClick = onNext, enabled = hasText) { Text("下一步：转换导出") }
                Step.EXPORT -> Text(
                    if (exporting) "正在生成…" else if (hasOutput) "生成完成" else "点击开始转换",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri) = try {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (_: Throwable) {
    null
}

private fun openEpub(context: Context, file: java.io.File?) {
    if (file == null || !file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/epub+zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开 EPUB"))
    } catch (t: Throwable) {
        android.widget.Toast.makeText(context, "没有可打开 EPUB 的应用", android.widget.Toast.LENGTH_SHORT).show()
    }
}

