/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azx8788a.pan123.api.CloudFile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun FilesScreen(model: MainViewModel, requestFiles: () -> Unit, requestNotifications: () -> Unit, showTransfers: () -> Unit) {
    val state by model.cloud.collectAsStateWithLifecycle()
    val permission by model.storageGranted.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val operation by model.operation.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var fileMenu by remember { mutableStateOf<CloudFile?>(null) }
    var input by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<CloudFile?>(null) }
    var delete by remember { mutableStateOf<List<CloudFile>?>(null) }
    var permanentlyDelete by remember { mutableStateOf<CloudFile?>(null) }
    var share by remember { mutableStateOf<List<CloudFile>?>(null) }
    var destination by remember { mutableStateOf<Pair<List<CloudFile>, Boolean>?>(null) }
    var localPicker by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("id") }
    fun download(files: List<CloudFile>) {
        if (!permission) { requestFiles(); return }
        requestNotifications(); model.download(files); showTransfers()
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (state.selected.isNotEmpty()) "已选 ${state.selected.size} 项" else state.path.last().name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { if (state.path.size > 1 || state.trash || state.selected.isNotEmpty()) IconButton(onClick = model::goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            } }, actions = {
                IconButton(onClick = model::refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "刷新") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(if (state.trash) "返回我的云盘" else "回收站") }, onClick = { menu = false; model.toggleTrash() })
                    DropdownMenuItem(text = { Text("全选已加载文件") }, onClick = { menu = false; model.selectAll() })
                    DropdownMenuItem(text = { Text("按名称排序") }, onClick = { menu = false; sort = "name" })
                    DropdownMenuItem(text = { Text("按大小排序") }, onClick = { menu = false; sort = "size" })
                    DropdownMenuItem(text = { Text("最新文件优先") }, onClick = { menu = false; sort = "id" })
                    if (!state.trash) {
                        DropdownMenuItem(text = { Text("离线下载") }, onClick = { menu = false; input = "offline" })
                        DropdownMenuItem(text = { Text("导入秒传数据") }, onClick = { menu = false; input = "rapid" })
                    }
                }
            })
        if (!permission) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("文件访问尚未开启", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = requestFiles) { Text("开启所有文件访问") }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(operation, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (!state.trash) OutlinedTextField(state.query, model::search, label = { Text("搜索云盘文件") }, singleLine = true,
            trailingIcon = { IconButton(onClick = model::refresh) { Icon(Icons.Default.Search, "搜索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.refresh() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            state.path.forEachIndexed { index, path ->
                TextButton(onClick = { model.goToPath(index) }) { Text(path.name, maxLines = 1) }
                if (index != state.path.lastIndex) Text("/", Modifier.align(Alignment.CenterVertically))
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val files = remember(state.files, sort) {
            state.files.sortedWith(compareBy<CloudFile> { !it.isDirectory }.thenComparator { a, b ->
                when (sort) { "name" -> a.name.compareTo(b.name, ignoreCase = true); "size" -> b.size.compareTo(a.size); else -> b.id.compareTo(a.id) }
            })
        }
        LazyColumn(Modifier.weight(1f)) {
            if (files.isEmpty() && !state.loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(if (state.query.isNotBlank()) "没有匹配文件" else "暂无文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            items(files, key = { it.id }) { file ->
                ListItem(headlineContent = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (file.isDirectory) "文件夹" else formatBytes(file.size)) },
                    leadingContent = {
                        if (state.selected.isNotEmpty()) Checkbox(file.id in state.selected, { model.select(file) })
                        else Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null,
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }, trailingContent = { IconButton(onClick = { fileMenu = file }) { Icon(Icons.Default.MoreVert, "文件操作") } },
                    modifier = Modifier.combinedClickable(onClick = {
                        if (state.selected.isNotEmpty()) model.select(file) else if (file.isDirectory && !state.trash) model.openFolder(file) else fileMenu = file
                    }, onLongClick = { model.select(file) }))
            }
            if (state.hasMore) item { TextButton(onClick = model::loadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text("加载更多（${state.files.size}/${state.total}）")
            } }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (state.selected.isNotEmpty() && !state.trash) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { download(model.selectedFiles()) }) { Icon(Icons.Default.Download, "下载") }
            IconButton(onClick = { destination = model.selectedFiles() to false }) { Icon(Icons.Default.DriveFileMove, "移动") }
            IconButton(onClick = { destination = model.selectedFiles() to true }) { Icon(Icons.Default.FileCopy, "复制") }
            IconButton(onClick = { share = model.selectedFiles() }) { Icon(Icons.Default.Share, "分享") }
            IconButton(onClick = { delete = model.selectedFiles() }) { Icon(Icons.Default.Delete, "移入回收站") }
        } else if (!state.trash) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { input = "folder" }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("新建文件夹") }
            Button(onClick = { if (permission) localPicker = true else requestFiles() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Upload, null); Spacer(Modifier.width(8.dp)); Text("上传")
            }
        }
    }
    fileMenu?.let { file ->
        AlertDialog(onDismissRequest = { fileMenu = null }, title = { Text(file.name, maxLines = 3, overflow = TextOverflow.Ellipsis) }, text = {
            Column {
                Text(if (file.isDirectory) "文件夹 · ID ${file.id}" else "${formatBytes(file.size)} · ID ${file.id}", style = MaterialTheme.typography.bodySmall)
                if (state.trash) {
                    TextButton(onClick = { fileMenu = null; model.restore(file) }) { Text("恢复到原位置") }
                    TextButton(onClick = { fileMenu = null; permanentlyDelete = file }) { Text("永久删除") }
                } else {
                    TextButton(onClick = { fileMenu = null; download(listOf(file)) }) { Text(if (file.isDirectory) "打包下载文件夹" else "下载") }
                    if (!file.isDirectory) TextButton(onClick = {
                        fileMenu = null
                        if (!permission) requestFiles() else { requestNotifications(); model.preview(file); showTransfers() }
                    }) { Text("打开 / 预览（先下载）") }
                    TextButton(onClick = { fileMenu = null; rename = file }) { Text("重命名") }
                    Row {
                        TextButton(onClick = { fileMenu = null; destination = listOf(file) to false }) { Text("移动") }
                        TextButton(onClick = { fileMenu = null; destination = listOf(file) to true }) { Text("复制") }
                        TextButton(onClick = { fileMenu = null; share = listOf(file) }) { Text("分享") }
                    }
                    TextButton(onClick = { fileMenu = null; model.exportRapid(listOf(file)) }) { Text("导出秒传 JSON") }
                    TextButton(onClick = { fileMenu = null; delete = listOf(file) }) { Text("移入回收站") }
                }
            }
        }, confirmButton = { TextButton(onClick = { fileMenu = null }) { Text("关闭") } })
    }
    input?.let { mode -> InputDialog(
        title = when (mode) { "folder" -> "新建文件夹"; "rapid" -> "导入秒传数据"; else -> "离线下载" },
        multiline = mode != "folder", label = when (mode) { "folder" -> "文件夹名称"; "rapid" -> "秒传 JSON / 123FastLink"; else -> "HTTP / Magnet / 迅雷链接" },
        confirm = { when (mode) { "folder" -> model.createFolder(it); "rapid" -> model.importRapid(it); else -> model.offline(it) } }, dismiss = { input = null }) }
    rename?.let { file -> InputDialog("重命名", file.name, confirm = { model.rename(file, it) }, dismiss = { rename = null }) }
    share?.let { files ->
        var password by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { share = null }, title = { Text("分享 ${files.size} 项") }, text = {
            OutlinedTextField(password, { password = it }, label = { Text("提取码（可留空）") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { model.share(files, password); share = null }) { Text("创建分享") } },
            dismissButton = { TextButton(onClick = { share = null }) { Text("取消") } })
    }
    delete?.let { files -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("移入回收站？") },
        text = { Text("选中的 ${files.size} 项可以在回收站恢复。") },
        confirmButton = { TextButton(onClick = { model.trash(files); delete = null }) { Text("移入回收站") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("取消") } }) }
    permanentlyDelete?.let { file -> AlertDialog(onDismissRequest = { permanentlyDelete = null }, title = { Text("永久删除？") },
        text = { Text("${file.name}\n删除后无法从回收站恢复。") },
        confirmButton = { TextButton(onClick = { model.permanentDelete(file); permanentlyDelete = null }) { Text("永久删除") } },
        dismissButton = { TextButton(onClick = { permanentlyDelete = null }) { Text("取消") } }) }
    destination?.let { (files, copy) -> CloudFolderPicker(model, files.map { it.id }.toSet(), copy,
        choose = { model.moveCopy(files, it, copy) }, dismiss = { destination = null }) }
    if (localPicker) NativeFilePicker(model, false, choose = { requestNotifications(); model.upload(it); showTransfers() }, dismiss = { localPicker = false })
}
