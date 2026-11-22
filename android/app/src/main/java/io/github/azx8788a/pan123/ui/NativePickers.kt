/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.azx8788a.pan123.api.CloudFile
import io.github.azx8788a.pan123.service.LocalEntry
import java.io.File
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeFilePicker(
    model: MainViewModel,
    directoryOnly: Boolean,
    initial: File? = null,
    choose: (List<File>) -> Unit,
    dismiss: () -> Unit,
) {
    val roots = remember { model.roots() }
    var current by remember { mutableStateOf(initial?.takeIf { it.isDirectory } ?: roots.firstOrNull() ?: File("/")) }
    var entries by remember { mutableStateOf<List<LocalEntry>>(emptyList()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        loading = true
        error = null
        try {
            entries = model.localFiles(current)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            entries = emptyList()
            error = exception.message
        } finally {
            loading = false
        }
    }
    val visible = entries.filter { !directoryOnly || it.directory }
    val atRoot = roots.any { it.absolutePath == current.absolutePath }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (directoryOnly) "选择下载目录" else "选择上传文件",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (directoryOnly) "下载内容将保存到所选文件夹" else "支持多选文件，也可以上传整个文件夹",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    roots.forEachIndexed { index, root ->
                        FilterChip(
                            selected = current.absolutePath.startsWith(root.absolutePath),
                            onClick = { current = root },
                            label = { Text(if (index == 0) "内部存储" else "存储 ${index + 1}") },
                            leadingIcon = { Icon(Icons.Default.Storage, null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { current.parentFile?.let { current = it } }, enabled = !atRoot) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一级")
                        }
                        Icon(
                            Icons.Default.FolderOpen,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            current.path,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (visible.isEmpty() && !loading) {
                        item {
                            EmptyState(
                                Icons.Default.FolderOpen,
                                if (directoryOnly) "没有子文件夹" else "此目录为空",
                                "可以返回上一级目录继续浏览。",
                            )
                        }
                    }
                    items(visible, key = { it.file.absolutePath }) { entry ->
                        LocalFileRow(
                            entry = entry,
                            selected = entry.file.absolutePath in selected,
                            toggle = {
                                selected = if (entry.file.absolutePath in selected) {
                                    selected - entry.file.absolutePath
                                } else {
                                    selected + entry.file.absolutePath
                                }
                            },
                            open = { if (entry.directory) current = entry.file },
                        )
                    }
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    if (!directoryOnly) {
                        Text(
                            if (selected.isEmpty()) "尚未选择文件" else "已选择 ${selected.size} 个文件",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = dismiss) { Text("取消") }
                        Spacer(Modifier.weight(1f))
                        if (!directoryOnly) {
                            TextButton(
                                onClick = { choose(listOf(current)); dismiss() },
                                enabled = error == null,
                            ) { Text("上传当前文件夹") }
                        }
                        Button(
                            onClick = {
                                choose(if (directoryOnly) listOf(current) else selected.map(::File))
                                dismiss()
                            },
                            enabled = error == null && (directoryOnly || selected.isNotEmpty()),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text(if (directoryOnly) "使用此目录" else "上传 ${selected.size} 个")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalFileRow(entry: LocalEntry, selected: Boolean, toggle: () -> Unit, open: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (entry.directory) open() else toggle() },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                if (entry.directory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                if (entry.directory) ColorFolder else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (entry.directory) "文件夹" else formatBytes(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.directory) {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
            } else {
                Checkbox(checked = selected, onCheckedChange = { toggle() })
            }
        }
    }
}

private val ColorFolder = androidx.compose.ui.graphics.Color(0xFFE39A2D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudFolderPicker(
    model: MainViewModel,
    excluded: Set<Long>,
    copy: Boolean,
    choose: (Long) -> Unit,
    dismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(listOf(CloudPath(0, "我的云盘"))) }
    var entries by remember { mutableStateOf<List<CloudFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        loading = true
        error = null
        try {
            entries = model.folderContents(path.last().id).filter { it.id !in excluded }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            entries = emptyList()
            error = exception.message
        } finally {
            loading = false
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(min = 420.dp, max = 620.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (copy) "复制到…" else "移动到…", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "选择一个云端文件夹作为目标位置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { path = path.dropLast(1) }, enabled = path.size > 1) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一级")
                        }
                        Icon(
                            Icons.Default.FolderOpen,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            path.joinToString(" / ") { it.name },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                error?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (entries.isEmpty() && !loading && error == null) {
                        item {
                            EmptyState(
                                Icons.Default.Folder,
                                "没有子文件夹",
                                "可以直接选择当前目录。",
                            )
                        }
                    }
                    items(entries, key = { it.id }) { folder ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { path = path + CloudPath(folder.id, folder.name) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(Icons.Default.Folder, ColorFolder, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    folder.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = dismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { path = path.dropLast(1) }, enabled = path.size > 1, shape = RoundedCornerShape(15.dp)) {
                        Text("上一级")
                    }
                    Button(
                        onClick = { choose(path.last().id); dismiss() },
                        enabled = !loading && error == null,
                        shape = RoundedCornerShape(15.dp),
                    ) { Text("选择此目录") }
                }
            }
        }
    }
}