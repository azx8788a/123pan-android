/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azx8788a.pan123.BuildConfig
import io.github.azx8788a.pan123.data.TransferEntity
import io.github.azx8788a.pan123.data.TransferStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TransfersScreen(model: MainViewModel, openFile: (String) -> Unit) {
    val tasks by model.tasks.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("传输任务") }, actions = { IconButton(onClick = model::clearTasks) { Icon(Icons.Default.CleaningServices, "清理已完成记录") } })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tasks.isEmpty()) item { Text("暂无传输任务", Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(tasks, key = { it.id }) { task -> TransferCard(task, model, openFile) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable private fun TransferCard(task: TransferEntity, model: MainViewModel, openFile: (String) -> Unit) {
    var previousBytes by remember(task.id) { mutableLongStateOf(task.done) }
    var previousTime by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var previousPhase by remember(task.id) { mutableStateOf(task.message) }
    var speed by remember(task.id) { mutableLongStateOf(0) }
    LaunchedEffect(task.done, task.message) {
        val now = System.currentTimeMillis()
        speed = if (previousPhase == task.message && now > previousTime) maxOf(0, (task.done - previousBytes) * 1000 / (now - previousTime)) else 0
        previousBytes = task.done; previousTime = now; previousPhase = task.message
    }
    var details by remember { mutableStateOf(false) }
    val active = task.status in listOf(TransferStatus.RUNNING, TransferStatus.QUEUED)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (task.kind == "upload") Icons.Default.Upload else Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(task.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { details = true }) { Icon(Icons.Default.MoreVert, "任务详情") }
            }
            Spacer(Modifier.height(8.dp))
            if (task.total > 0) LinearProgressIndicator(progress = { (task.done.toDouble() / task.total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(task.message, style = MaterialTheme.typography.bodySmall, color = if (task.status == TransferStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatBytes(task.done)} / ${formatBytes(task.total)}" + if (speed > 0 && task.message in listOf("下载中", "上传中")) " · ${formatBytes(speed)}/s" else "",
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (task.status == TransferStatus.COMPLETE && task.kind == "download") TextButton(onClick = { openFile(task.localPath) }) { Text("打开") }
                if (active) {
                    TextButton(onClick = { model.pause(task.id) }) { Text("暂停") }
                    TextButton(onClick = { model.cancel(task.id) }) { Text("取消") }
                }
                if (task.status in listOf(TransferStatus.PAUSED, TransferStatus.FAILED, TransferStatus.CANCELLED)) {
                    if (task.message.contains("登录已过期")) TextButton(onClick = model::addAccount) { Text("重新登录") }
                    TextButton(onClick = { model.resume(task.id) }) { Text(if (task.status == TransferStatus.FAILED) "重试" else "继续") }
                }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("任务详情") }, text = {
        Text("${task.name}\n\n${task.localPath}\n\n${task.message}")
    }, confirmButton = { TextButton(onClick = { details = false }) { Text("关闭") } }, dismissButton = {
        if (!active) TextButton(onClick = { model.removeTask(task.id); details = false }) { Text("移除记录") }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SharesScreen(model: MainViewModel) {
    val links by model.shareLinks.collectAsStateWithLifecycle()
    val loading by model.shareLoading.collectAsStateWithLifecycle()
    val hasMore by model.shareHasMore.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var delete by remember { mutableStateOf<Long?>(null) }
    Column {
        TopAppBar(title = { Text("我的分享") }, actions = { IconButton(onClick = { model.loadShares() }) { Icon(Icons.Default.Refresh, "刷新") } })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            if (links.isEmpty() && !loading) item { Text("暂无可显示的分享", Modifier.padding(32.dp)) }
            items(links, key = { it.id }) { link -> ListItem(headlineContent = { Text(link.name, maxLines = 2) },
                supportingContent = { Text(link.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Default.Link, null) }, trailingContent = {
                    Row {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(link.url + if (link.password.isBlank()) "" else "\n提取码：${link.password}")) }) { Icon(Icons.Default.ContentCopy, "复制链接") }
                        IconButton(onClick = { delete = link.id }) { Icon(Icons.Default.Delete, "取消分享") }
                    }
                }, modifier = Modifier.clickable { model.textResult.value = TextResult(link.name, link.url + if (link.password.isBlank()) "" else "\n提取码：${link.password}") }) }
            if (hasMore) item { TextButton(onClick = { model.loadShares(false) }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("加载更多") } }
        }
    }
    delete?.let { id -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("取消分享？") }, text = { Text("此分享链接将失效，云端文件保留。") },
        confirmButton = { TextButton(onClick = { model.deleteShare(id); delete = null }) { Text("取消分享") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("返回") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(model: MainViewModel, requestFiles: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val permission by model.storageGranted.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val profile by model.profile.collectAsStateWithLifecycle()
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var directory by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf("") }
    LaunchedEffect(license) { if (license) licenseText = withContext(Dispatchers.IO) { context.assets.open("LICENSE").bufferedReader().use { it.readText() } } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(profile?.nickname?.takeIf { it.isNotBlank() } ?: session?.username.orEmpty(), style = MaterialTheme.typography.titleLarge)
                profile?.let { Text("已用 ${formatBytes(it.used)} / ${formatBytes(it.total)}", Modifier.padding(top = 8.dp)) }
                if (accounts.size > 1) accounts.forEach { account -> TextButton(onClick = { model.switchAccount(account) }, enabled = account != session?.accountKey) { Text("切换到 $account") } }
                TextButton(onClick = model::addAccount) { Text("添加其他账号") }
                TextButton(onClick = { logout = true }) { Text("退出当前账号") }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("文件访问与下载目录", style = MaterialTheme.typography.titleMedium)
                ListItem(headlineContent = { Text("所有文件访问") }, supportingContent = { Text(if (permission) "已开启 · 直接读写共享存储" else "未开启") },
                    trailingContent = { TextButton(onClick = requestFiles) { Text(if (permission) "管理" else "开启") } })
                Text(settings.downloadDirectory, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { if (permission) directory = true else requestFiles() }) { Text("更改下载目录") }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("传输", style = MaterialTheme.typography.titleMedium)
                NumberSetting("并行传输任务", settings.concurrentTasks, 1..8) { model.updateSettings(settings.copy(concurrentTasks = it)) }
                NumberSetting("每个文件上传分片并发", settings.uploadThreads, 1..4) { model.updateSettings(settings.copy(uploadThreads = it)) }
                Text("下载支持断点续传；上传采用 5 MiB 分片。修改并发设置后，新启动的任务生效。", style = MaterialTheme.typography.bodySmall)
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("外观", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, title) ->
                        FilterChip(selected = settings.theme == key, onClick = { model.updateSettings(settings.copy(theme = key)) }, label = { Text(title) })
                    }
                }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("关于 123pan Next", style = MaterialTheme.typography.titleMedium)
                Text("${BuildConfig.VERSION_NAME} · 原生 Kotlin / Compose", style = MaterialTheme.typography.bodySmall)
                Text("基于 123panNextGen/123pan，GPL-3.0-or-later 开源。软件不提供任何担保，与 123 云盘官方无关。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uriHandler.openUri("https://github.com/azx8788a/123pan-android/tree/android") }) { Text("查看源码与构建") }
                TextButton(onClick = { license = true }) { Text("查看 GPL 许可证") }
            } } }
        }
    }
    if (directory) NativeFilePicker(model, true, File(settings.downloadDirectory), choose = {
        model.updateSettings(settings.copy(downloadDirectory = it.single().absolutePath))
    }, dismiss = { directory = false })
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出当前账号？") },
        text = { Text("该账号的未完成传输将暂停。") }, confirmButton = { TextButton(onClick = { logout = false; model.logout() }) { Text("退出") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
    if (license) TextResultDialog(TextResult("GNU GPL v3", licenseText)) { license = false }
}

@Composable private fun NumberSetting(title: String, value: Int, range: IntRange, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { change(value - 1) }, enabled = value > range.first) { Icon(Icons.Default.Remove, "减少") }
        Text(value.toString())
        IconButton(onClick = { change(value + 1) }, enabled = value < range.last) { Icon(Icons.Default.Add, "增加") }
    }
}
