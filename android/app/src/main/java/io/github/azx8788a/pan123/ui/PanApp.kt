/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanApp(model: MainViewModel, requestFiles: () -> Unit, requestNotifications: () -> Unit, openFile: (String) -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val dark = settings.theme == "dark" || (settings.theme == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFADC2FF)) else
        lightColorScheme(primary = Color(0xFF4665D9), secondary = Color(0xFF4F657C))) {
        val session by model.session.collectAsStateWithLifecycle()
        val needsLogin by model.needsLogin.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val result by model.textResult.collectAsStateWithLifecycle()
        val openRequest by model.openRequest.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(message) {
            message?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null }
        }
        LaunchedEffect(openRequest) { openRequest?.let { openFile(it); model.openRequest.value = null } }
        Surface(Modifier.fillMaxSize()) {
            if (session == null || needsLogin) {
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    LoginScreen(model, Modifier.padding(padding))
                }
            } else {
                val cloud by model.cloud.collectAsStateWithLifecycle()
                BackHandler(tab == 0 && (cloud.path.size > 1 || cloud.trash || cloud.selected.isNotEmpty())) { model.goBack() }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            listOf("文件" to Icons.Default.Cloud, "传输" to Icons.Default.SwapVert,
                                "分享" to Icons.Default.Share, "设置" to Icons.Default.Settings).forEachIndexed { index, (title, icon) ->
                                NavigationBarItem(selected = tab == index, onClick = { tab = index; if (index == 2) model.loadShares() },
                                    icon = { Icon(icon, title) }, label = { Text(title) })
                            }
                        }
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

@Composable fun TextResultDialog(result: TextResult, dismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(result.title) },
        text = { SelectionContainer { Text(result.text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(result.text)) }) { Text("复制") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}

@Composable fun InputDialog(title: String, initial: String = "", multiline: Boolean = false, label: String = "名称",
    confirm: (String) -> Unit, dismiss: () -> Unit) {
    var value by rememberSaveable(title, initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = !multiline,
            minLines = if (multiline) 4 else 1, maxLines = if (multiline) 9 else 1, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(onClick = { confirm(value); dismiss() }, enabled = value.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

fun formatBytes(value: Long): String {
    if (value < 0) return "未知大小"
    var number = value.toDouble()
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var unit = 0
    while (number >= 1024 && unit < units.lastIndex) { number /= 1024; unit++ }
    return if (unit == 0) "$value B" else java.lang.String.format(java.util.Locale.ROOT, "%.1f %s", number, units[unit])
}
