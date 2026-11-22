/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.azx8788a.pan123.api.CloudFile
import io.github.azx8788a.pan123.service.LocalEntry
import java.io.File
import kotlinx.coroutines.CancellationException

@Composable fun NativeFilePicker(model: MainViewModel, directoryOnly: Boolean, initial: File? = null,
    choose: (List<File>) -> Unit, dismiss: () -> Unit) {
    val roots = remember { model.roots() }
    var current by remember { mutableStateOf(initial?.takeIf { it.isDirectory } ?: roots.first()) }
    var entries by remember { mutableStateOf<List<LocalEntry>>(emptyList()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        loading = true; error = null
        try { entries = model.localFiles(current) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { entries = emptyList(); error = e.message }
        finally { loading = false }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(if (directoryOnly) "选择下载目录" else "选择上传文件", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { current.parentFile?.let { current = it } }, enabled = roots.none { it.absolutePath == current.absolutePath }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一级")
                    }
                    Text(current.path, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    roots.forEachIndexed { index, root -> FilterChip(selected = current.path.startsWith(root.path),
                        onClick = { current = root }, label = { Text(if (index == 0) "内部存储" else "存储 ${index + 1}") }) }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                LazyColumn(Modifier.weight(1f)) {
                    items(entries.filter { !directoryOnly || it.directory }, key = { it.file.absolutePath }) { entry ->
                        ListItem(headlineContent = { Text(entry.file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (entry.directory) "文件夹" else formatBytes(entry.size)) },
                            leadingContent = { Icon(if (entry.directory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) },
                            trailingContent = { if (!entry.directory) Checkbox(entry.file.absolutePath in selected, {
                                selected = if (entry.file.absolutePath in selected) selected - entry.file.absolutePath else selected + entry.file.absolutePath
                            }) },
                            modifier = Modifier.clickable {
                                if (entry.directory) current = entry.file else selected = if (entry.file.absolutePath in selected)
                                    selected - entry.file.absolutePath else selected + entry.file.absolutePath
                            })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("取消") }
                    if (!directoryOnly) TextButton(onClick = { choose(listOf(current)); dismiss() }, enabled = error == null) { Text("上传当前文件夹") }
                    Button(onClick = { choose(if (directoryOnly) listOf(current) else selected.map(::File)); dismiss() },
                        enabled = error == null && (directoryOnly || selected.isNotEmpty())) { Text(if (directoryOnly) "使用此目录" else "上传 ${selected.size} 个") }
                }
            }
        }
    }
}

@Composable fun CloudFolderPicker(model: MainViewModel, excluded: Set<Long>, copy: Boolean,
    choose: (Long) -> Unit, dismiss: () -> Unit) {
    var path by remember { mutableStateOf(listOf(CloudPath(0, "我的云盘"))) }
    var entries by remember { mutableStateOf<List<CloudFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        loading = true; error = null
        try { entries = model.folderContents(path.last().id).filter { it.id !in excluded } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { entries = emptyList(); error = e.message }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (copy) "复制到" else "移动到") }, text = {
        Column(Modifier.heightIn(min = 240.dp, max = 430.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { path = path.dropLast(1) }, enabled = path.size > 1) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一级") }
                Text(path.joinToString(" / ") { it.name }, maxLines = 2)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.id }) { file -> ListItem(headlineContent = { Text(file.name) },
                    leadingContent = { Icon(Icons.Default.Folder, null) }, modifier = Modifier.clickable { path = path + CloudPath(file.id, file.name) }) }
            }
        }
    }, confirmButton = { TextButton(onClick = { choose(path.last().id); dismiss() }, enabled = !loading && error == null) { Text("选择此目录") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
