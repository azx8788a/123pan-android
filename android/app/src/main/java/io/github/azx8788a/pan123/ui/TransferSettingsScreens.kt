/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
@Composable
fun TransfersScreen(model: MainViewModel, openFile: (String) -> Unit) {
    val tasks by model.tasks.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(0) }
    val running = tasks.count { it.status == TransferStatus.RUNNING }
    val queued = tasks.count { it.status == TransferStatus.QUEUED }
    val complete = tasks.count { it.status == TransferStatus.COMPLETE }
    val unfinished = tasks.count { it.status != TransferStatus.COMPLETE }
    val visible = remember(tasks, filter) {
        when (filter) {
            1 -> tasks.filter { it.status != TransferStatus.COMPLETE }
            2 -> tasks.filter { it.status == TransferStatus.COMPLETE }
            else -> tasks
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "传输",
            subtitle = if (tasks.isEmpty()) "上传与下载任务会出现在这里" else "$running 个传输中 · $complete 个已完成",
            actions = {
                IconButton(
                    onClick = model::clearTasks,
                    enabled = tasks.any { it.status == TransferStatus.COMPLETE || it.status == TransferStatus.CANCELLED },
                ) {
                    Icon(Icons.Default.CleaningServices, "清理已完成记录")
                }
            },
        )
        if (tasks.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricTile("传输中", running.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricTile("排队中", queued.toString(), WarningAmber, Modifier.weight(1f))
                    MetricTile("已完成", complete.toString(), SuccessGreen, Modifier.weight(1f))
                }
            }
            FilterTabs(
                selected = filter,
                labels = listOf("全部", "未完成", "已完成"),
                counts = listOf(tasks.size, unfinished, complete),
                onSelect = { filter = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tasks.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.SwapHoriz,
                        "暂无传输任务",
                        "在文件页下载文件或上传本地文件，任务进度会显示在这里。",
                    )
                }
            } else if (visible.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.SwapHoriz,
                        if (filter == 1) "没有未完成的任务" else "没有已完成的任务",
                        "切换上方筛选条件查看其他任务。",
                    )
                }
            }
            items(visible, key = { it.id }) { task ->
                TransferCard(task, model, openFile)
            }
        }
    }
}

@Composable
private fun TransferCard(task: TransferEntity, model: MainViewModel, openFile: (String) -> Unit) {
    var previousBytes by remember(task.id) { mutableLongStateOf(task.done) }
    var previousTime by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var previousPhase by remember(task.id) { mutableStateOf(task.message) }
    var speed by remember(task.id) { mutableLongStateOf(0) }
    LaunchedEffect(task.done, task.message) {
        val now = System.currentTimeMillis()
        speed = if (previousPhase == task.message && now > previousTime) {
            maxOf(0, (task.done - previousBytes) * 1000 / (now - previousTime))
        } else {
            0
        }
        previousBytes = task.done
        previousTime = now
        previousPhase = task.message
    }
    var details by remember { mutableStateOf(false) }
    val active = task.status in listOf(TransferStatus.RUNNING, TransferStatus.QUEUED)
    val statusColor = when (task.status) {
        TransferStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TransferStatus.QUEUED -> WarningAmber
        TransferStatus.COMPLETE -> SuccessGreen
        TransferStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (task.status) {
        TransferStatus.RUNNING -> "传输中"
        TransferStatus.QUEUED -> "排队中"
        TransferStatus.PAUSED -> "已暂停"
        TransferStatus.FAILED -> "传输失败"
        TransferStatus.CANCELLED -> "已取消"
        TransferStatus.COMPLETE -> "已完成"
        else -> task.status
    }
    val kindColor = if (task.kind == "upload") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val progress = if (task.total > 0) (task.done.toDouble() / task.total).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (task.kind == "upload") Icons.Default.Upload else Icons.Default.Download,
                    kindColor,
                    size = 46.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(statusText, statusColor)
                        if (task.total > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                formatPercent(task.done, task.total),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = { details = true }) { Icon(Icons.Default.MoreVert, "任务详情") }
            }
            Spacer(Modifier.height(12.dp))
            if (task.total > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            } else if (active) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatBytes(task.done)} / ${formatBytes(task.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (speed > 0 && task.status == TransferStatus.RUNNING && task.message in listOf("下载中", "上传中")) {
                    StatusPill("${formatBytes(speed)}/s", kindColor)
                }
            }
            if (task.message.isNotBlank() && task.status != TransferStatus.COMPLETE) {
                Text(
                    task.message,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == TransferStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.status == TransferStatus.COMPLETE && task.kind == "download") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { openFile(task.localPath) }) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("打开")
                    }
                }
            } else if (active) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (task.status == TransferStatus.RUNNING) {
                        TextButton(onClick = { model.pause(task.id) }) { Text("暂停") }
                    }
                    TextButton(onClick = { model.cancel(task.id) }) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (task.status in listOf(TransferStatus.PAUSED, TransferStatus.FAILED, TransferStatus.CANCELLED)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (task.message.contains("登录已过期")) {
                        TextButton(onClick = model::addAccount) { Text("重新登录") }
                    }
                    TextButton(onClick = { model.resume(task.id) }) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (task.status == TransferStatus.FAILED) "重试" else "继续")
                    }
                }
            }
        }
    }

    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("任务详情") },
            text = {
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            if (task.kind == "upload") Icons.Default.Upload else Icons.Default.Download,
                            kindColor,
                            size = 44.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(task.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    DetailLine("状态", statusText)
                    DetailLine("进度", "${formatBytes(task.done)} / ${formatBytes(task.total)} · ${formatPercent(task.done, task.total)}")
                    DetailLine("本地路径", task.localPath)
                    DetailLine("状态信息", task.message)
                }
            },
            confirmButton = { TextButton(onClick = { details = false }) { Text("关闭") } },
            dismissButton = {
                if (!active) {
                    TextButton(onClick = { model.removeTask(task.id); details = false }) {
                        Text("移除记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        label,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FilterTabs(
    selected: Int,
    labels: List<String>,
    counts: List<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "filterContainer",
            )
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = container,
                shadowElevation = if (isSelected) 2.dp else 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (counts[index] > 0) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            counts[index].toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharesScreen(model: MainViewModel) {
    val links by model.shareLinks.collectAsStateWithLifecycle()
    val loading by model.shareLoading.collectAsStateWithLifecycle()
    val hasMore by model.shareHasMore.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var delete by remember { mutableStateOf<Long?>(null) }

    fun copy(link: io.github.azx8788a.pan123.api.ShareLink) {
        clipboard.setText(
            AnnotatedString(link.url + if (link.password.isBlank()) "" else "\n提取码：${link.password}"),
        )
        model.message.value = "分享链接已复制"
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "分享",
            subtitle = if (links.isEmpty()) "集中管理你创建的分享链接" else "已加载 ${links.size} 个分享链接",
            actions = {
                IconButton(onClick = { model.loadShares() }, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新") }
            },
        )
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 16.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.Link, MaterialTheme.colorScheme.secondary, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "取消分享只会让链接失效，云端文件不会被删除。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (links.isEmpty() && !loading) {
                item {
                    EmptyState(
                        Icons.Default.Link,
                        "还没有分享链接",
                        "在文件页选择文件或文件夹，点击“分享”即可创建。",
                    )
                }
            }
            items(links, key = { it.id }) { link ->
                ShareCard(
                    link = link,
                    copy = { copy(link) },
                    open = { uriHandler.openUri(link.url) },
                    details = {
                        model.textResult.value = TextResult(
                            link.name,
                            link.url + if (link.password.isBlank()) "" else "\n提取码：${link.password}",
                        )
                    },
                    delete = { delete = link.id },
                )
            }
            if (hasMore) {
                item {
                    OutlinedButton(
                        onClick = { model.loadShares(false) },
                        enabled = !loading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("加载更多") }
                }
            }
        }
    }
    delete?.let { id ->
        AlertDialog(
            onDismissRequest = { delete = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("取消分享？") },
            text = { Text("此分享链接将立即失效，云端文件会完整保留。") },
            confirmButton = {
                TextButton(onClick = { model.deleteShare(id); delete = null }) {
                    Text("取消分享", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("返回") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareCard(
    link: io.github.azx8788a.pan123.api.ShareLink,
    copy: () -> Unit,
    open: () -> Unit,
    details: () -> Unit,
    delete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Link, MaterialTheme.colorScheme.tertiary, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        link.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "分享操作") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("查看详情") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menu = false; details() },
                        )
                        DropdownMenuItem(
                            text = { Text("在浏览器中打开") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                            onClick = { menu = false; open() },
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("取消分享", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; delete() },
                        )
                    }
                }
            }
            if (link.password.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                StatusPill("提取码 ${link.password}", WarningAmber)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = copy, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制链接")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = open) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("打开")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: MainViewModel, requestFiles: () -> Unit) {
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
    var accountMenu by remember { mutableStateOf(false) }
    LaunchedEffect(license) {
        if (license) {
            licenseText = withContext(Dispatchers.IO) {
                context.assets.open("LICENSE").bufferedReader().use { it.readText() }
            }
        }
    }
    val displayName = profile?.nickname?.takeIf { it.isNotBlank() }
        ?: session?.username?.takeIf { it.isNotBlank() }
        ?: "未登录"

    Column(Modifier.fillMaxSize()) {
        PageHeader(title = "设置", subtitle = "账号、存储与传输偏好")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountAvatar(displayName, 56.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    session?.accountKey.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            StatusPill("已登录", SuccessGreen)
                        }
                        if (profile != null) {
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("云盘空间", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${formatBytes(profile!!.used)} / ${formatBytes(profile!!.total)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (profile!!.total > 0) {
                                        (profile!!.used.toDouble() / profile!!.total).toFloat().coerceIn(0f, 1f)
                                    } else 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "已使用 ${formatPercent(profile!!.used, profile!!.total)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (accounts.size > 1) {
                                Box {
                                    OutlinedButton(onClick = { accountMenu = true }, shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("切换账号")
                                    }
                                    DropdownMenu(accountMenu, { accountMenu = false }) {
                                        accounts.forEach { account ->
                                            DropdownMenuItem(
                                                text = { Text(account, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                trailingIcon = {
                                                    if (account == session?.accountKey) {
                                                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                },
                                                onClick = {
                                                    accountMenu = false
                                                    if (account != session?.accountKey) model.switchAccount(account)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            FilledTonalButton(onClick = model::addAccount, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("添加账号")
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { logout = true }) {
                                Icon(Icons.Default.Logout, "退出登录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { SectionLabel("存储与权限") }
            item {
                SettingsGroup {
                    SettingRow(
                        icon = Icons.Default.Security,
                        tint = if (permission) SuccessGreen else WarningAmber,
                        title = "所有文件访问",
                        subtitle = if (permission) "已开启，可直接读写共享存储" else "未开启，上传和下载将无法使用",
                        trailing = {
                            if (permission) {
                                TextButton(onClick = requestFiles) { Text("管理") }
                            } else {
                                Button(onClick = requestFiles, shape = RoundedCornerShape(13.dp)) { Text("开启") }
                            }
                        },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Default.Folder,
                        tint = MaterialTheme.colorScheme.primary,
                        title = "下载目录",
                        subtitle = settings.downloadDirectory,
                        trailing = {
                            TextButton(onClick = { if (permission) directory = true else requestFiles() }) { Text("更改") }
                        },
                    )
                }
            }

            item { SectionLabel("传输") }
            item {
                SettingsGroup {
                    NumberSettingRow(
                        icon = Icons.Default.Speed,
                        tint = MaterialTheme.colorScheme.primary,
                        title = "并行传输任务",
                        subtitle = "同时进行的上传与下载数量",
                        value = settings.concurrentTasks,
                        range = 1..8,
                        change = { model.updateSettings(settings.copy(concurrentTasks = it)) },
                    )
                    SettingDivider()
                    NumberSettingRow(
                        icon = Icons.Default.SwapHoriz,
                        tint = MaterialTheme.colorScheme.secondary,
                        title = "上传分片并发",
                        subtitle = "每个文件同时上传的分片数",
                        value = settings.uploadThreads,
                        range = 1..4,
                        change = { model.updateSettings(settings.copy(uploadThreads = it)) },
                    )
                    Text(
                        "下载支持断点续传，上传采用 5 MiB 分片。修改并发设置后，新启动的任务生效。",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionLabel("外观") }
            item {
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeOption("跟随系统", Icons.Default.AutoMode, settings.theme == "system", Modifier.weight(1f)) {
                            model.updateSettings(settings.copy(theme = "system"))
                        }
                        ThemeOption("浅色", Icons.Default.LightMode, settings.theme == "light", Modifier.weight(1f)) {
                            model.updateSettings(settings.copy(theme = "light"))
                        }
                        ThemeOption("深色", Icons.Default.DarkMode, settings.theme == "dark", Modifier.weight(1f)) {
                            model.updateSettings(settings.copy(theme = "dark"))
                        }
                    }
                }
            }

            item { SectionLabel("关于") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(50.dp)
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("123pan Next", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${BuildConfig.VERSION_NAME} · 原生 Kotlin / Compose",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill("GPL-3.0", MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(16.dp))
                        AboutLink("查看源码与构建", Icons.Default.OpenInNew) {
                            uriHandler.openUri("https://github.com/azx8788a/123pan-android/tree/android")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        AboutLink("GPL 许可证", Icons.Default.Description) { license = true }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "基于 123panNextGen/123pan 改写。软件按“现状”提供，不提供任何担保，与 123 云盘官方无关。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (directory) {
        NativeFilePicker(
            model,
            true,
            File(settings.downloadDirectory),
            choose = { model.updateSettings(settings.copy(downloadDirectory = it.single().absolutePath)) },
            dismiss = { directory = false },
        )
    }
    if (logout) {
        AlertDialog(
            onDismissRequest = { logout = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("退出当前账号？") },
            text = { Text("该账号未完成的传输任务将暂停，本地文件不会删除。") },
            confirmButton = {
                TextButton(onClick = { logout = false; model.logout() }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } },
        )
    }
    if (license) TextResultDialog(TextResult("GNU GPL v3", licenseText)) { license = false }
}

@Composable
private fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun NumberSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    change: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { change(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, "减少")
        }
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                value.toString(),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        IconButton(onClick = { change(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, "增加")
        }
    }
}

@Composable
private fun ThemeOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "themeContainer",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AboutLink(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
    }
}