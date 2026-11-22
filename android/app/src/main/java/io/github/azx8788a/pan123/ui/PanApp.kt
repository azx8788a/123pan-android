/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azx8788a.pan123.data.TransferStatus
import kotlin.math.roundToInt

private data class PanTab(val label: String, val icon: ImageVector)

private val panTabs = listOf(
    PanTab("文件", Icons.Default.Folder),
    PanTab("传输", Icons.Default.SwapVert),
    PanTab("分享", Icons.Default.Link),
    PanTab("设置", Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanApp(model: MainViewModel, requestFiles: () -> Unit, requestNotifications: () -> Unit, openFile: (String) -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val dark = settings.theme == "dark" || (settings.theme == "system" && isSystemInDarkTheme())
    PanTheme(darkTheme = dark) {
        val session by model.session.collectAsStateWithLifecycle()
        val needsLogin by model.needsLogin.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val result by model.textResult.collectAsStateWithLifecycle()
        val openRequest by model.openRequest.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableStateOf(0) }
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                if (model.message.value == it) model.message.value = null
            }
        }
        LaunchedEffect(openRequest) {
            openRequest?.let {
                openFile(it)
                model.openRequest.value = null
            }
        }
        PanBackground {
            if (session == null || needsLogin) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { PanSnackbarHost(snackbar) },
                ) { padding ->
                    LoginScreen(model, Modifier.padding(padding))
                }
            } else {
                val cloud by model.cloud.collectAsStateWithLifecycle()
                val tasks by model.tasks.collectAsStateWithLifecycle()
                val activeTransfers = tasks.count {
                    it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED
                }
                BackHandler(tab == 0 && (cloud.path.size > 1 || cloud.trash || cloud.selected.isNotEmpty())) { model.goBack() }
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { PanSnackbarHost(snackbar) },
                    bottomBar = {
                        PanBottomBar(
                            selected = tab,
                            activeTransfers = activeTransfers,
                            onSelect = {
                                tab = it
                                if (it == 2) model.loadShares()
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (tab) {
                            0 -> FilesScreen(model, requestFiles, requestNotifications) { tab = 1 }
                            1 -> TransfersScreen(model, openFile)
                            2 -> SharesScreen(model)
                            3 -> SettingsScreen(model, requestFiles)
                        }
                    }
                }
            }
            result?.let { TextResultDialog(it) { model.textResult.value = null } }
        }
    }
}

@Composable
private fun PanSnackbarHost(host: SnackbarHostState) {
    SnackbarHost(hostState = host, modifier = Modifier.padding(16.dp)) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

@Composable
private fun PanBottomBar(selected: Int, activeTransfers: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                panTabs.forEachIndexed { index, item ->
                    val isSelected = selected == index
                    val container by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "tabContainer",
                    )
                    val content by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabContent",
                    )
                    Surface(
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = container,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BadgedBox(
                                badge = {
                                    if (index == 1 && activeTransfers > 0) {
                                        Badge(containerColor = DangerRed, contentColor = Color.White) {
                                            Text(activeTransfers.coerceAtMost(99).toString())
                                        }
                                    }
                                },
                            ) {
                                Icon(item.icon, item.label, tint = content, modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = content,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextResultDialog(result: TextResult, dismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(result.text) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(result.title) },
        text = {
            SelectionContainer {
                Text(
                    result.text,
                    modifier = Modifier.height(420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(result.text))
                copied = true
            }) { Text(if (copied) "已复制" else "复制") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } },
    )
}

@Composable
fun InputDialog(
    title: String,
    initial: String = "",
    multiline: Boolean = false,
    label: String = "名称",
    confirm: (String) -> Unit,
    dismiss: () -> Unit,
) {
    var value by rememberSaveable(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = dismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                { value = it },
                label = { Text(label) },
                singleLine = !multiline,
                minLines = if (multiline) 4 else 1,
                maxLines = if (multiline) 9 else 1,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm(value); dismiss() }, enabled = value.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}

fun formatBytes(value: Long): String {
    if (value < 0) return "未知大小"
    var number = value.toDouble()
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var unit = 0
    while (number >= 1024 && unit < units.lastIndex) {
        number /= 1024
        unit++
    }
    return if (unit == 0) "$value B" else java.lang.String.format(java.util.Locale.ROOT, "%.1f %s", number, units[unit])
}

fun formatPercent(part: Long, total: Long): String {
    if (total <= 0) return "0%"
    return "${((part.toDouble() / total) * 100).coerceIn(0.0, 100.0).roundToInt()}%"
}