/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azx8788a.pan123.api.CloudFile
import io.github.azx8788a.pan123.api.CloudProfile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    model: MainViewModel,
    requestFiles: () -> Unit,
    requestNotifications: () -> Unit,
    showTransfers: () -> Unit,
) {
    val state by model.cloud.collectAsStateWithLifecycle()
    val permission by model.storageGranted.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val operation by model.operation.collectAsStateWithLifecycle()
    val profile by model.profile.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var fileMenu by remember { mutableStateOf<CloudFile?>(null) }
    var input by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<CloudFile?>(null) }
    var delete by remember { mutableStateOf<List<CloudFile>?>(null) }
    var permanentlyDelete by remember { mutableStateOf<CloudFile?>(null) }
    var share by remember { mutableStateOf<List<CloudFile>?>(null) }
    var destination by remember { mutableStateOf<Pair<List<CloudFile>, Boolean>?>(null) }
    var localPicker by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("id") }

    fun download(files: List<CloudFile>) {
        if (!permission) {
            requestFiles()
            return
        }
        requestNotifications()
        model.download(files)
        showTransfers()
    }

    val files = remember(state.files, sort) {
        state.files.sortedWith(
            compareBy<CloudFile> { !it.isDirectory }.thenComparator { a, b ->
                when (sort) {
                    "name" -> a.name.compareTo(b.name, ignoreCase = true)
                    "size" -> b.size.compareTo(a.size)
                    else -> b.id.compareTo(a.id)
                }
            },
        )
    }
    val subtitle = when {
        state.selected.isNotEmpty() -> "轻点文件继续选择"
        state.trash -> "${state.total} 项 · 可恢复或永久删除"
        state.path.size == 1 && profile != null ->
            "已用 ${formatBytes(profile!!.used)} · 总空间 ${formatBytes(profile!!.total)}"
        state.path.size == 1 -> "所有文件都在这里"
        else -> "${state.total} 项内容"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.selected.isNotEmpty()) {
                SelectionHeader(
                    count = state.selected.size,
                    allSelected = state.files.isNotEmpty() && state.selected.size == state.files.size,
                    clear = model::goBack,
                    selectAll = model::selectAll,
                )
            } else {
                PageHeader(
                    title = if (state.trash) "回收站" else state.path.last().name,
                    subtitle = subtitle,
                    onBack = if (state.path.size > 1 || state.trash) model::goBack else null,
                    actions = {
                        IconButton(onClick = model::refresh, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                        if (!state.trash) {
                            IconButton(onClick = model::toggleTrash) { Icon(Icons.Default.Delete, "回收站") }
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.trash) "返回我的云盘" else "打开回收站") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; model.toggleTrash() },
                            )
                            DropdownMenuItem(
                                text = { Text("全选已加载文件") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                                onClick = { menu = false; model.selectAll() },
                            )
                            if (!state.trash) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("离线下载") },
                                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                                    onClick = { menu = false; input = "offline" },
                                )
                                DropdownMenuItem(
                                    text = { Text("导入秒传数据") },
                                    leadingIcon = { Icon(Icons.Default.Bolt, null) },
                                    onClick = { menu = false; input = "rapid" },
                                )
                            }
                        }
                    },
                )
            }
            if (busy) OperationStrip(operation)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = if (state.selected.isNotEmpty()) 112.dp else 22.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (!permission) item { PermissionCard(requestFiles) }
                if (state.trash) item { TrashNotice(state.total) }
                if (!state.trash) {
                    if (state.path.size == 1 && profile != null) item { StorageCard(profile!!) }
                    item {
                        SearchField(
                            value = state.query,
                            change = model::search,
                            submit = model::refresh,
                            clear = { model.search(""); model.refresh() },
                        )
                    }
                    item {
                        QuickActions(
                            onUpload = { if (permission) localPicker = true else requestFiles() },
                            onFolder = { input = "folder" },
                            onOffline = { input = "offline" },
                            onRapid = { input = "rapid" },
                        )
                    }
                    if (state.path.size > 1) {
                        item {
                            Breadcrumbs(
                                items = state.path.map { it.name },
                                current = state.path.lastIndex,
                                go = model::goToPath,
                            )
                        }
                    }
                }
                item {
                    ListToolbar(
                        count = state.total,
                        query = state.query,
                        trash = state.trash,
                        sort = sort,
                        openSort = { sortMenu = true },
                        sortMenu = sortMenu,
                        dismissSort = { sortMenu = false },
                        chooseSort = { sort = it; sortMenu = false },
                    )
                }
                if (state.loading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                        )
                    }
                }
                if (files.isEmpty() && !state.loading) {
                    item {
                        EmptyState(
                            icon = when {
                                state.trash -> Icons.Default.Delete
                                state.query.isNotBlank() -> Icons.Default.Search
                                else -> Icons.Default.FolderOpen
                            },
                            title = when {
                                state.trash -> "回收站是空的"
                                state.query.isNotBlank() -> "没有匹配的文件"
                                else -> "这里还没有文件"
                            },
                            description = when {
                                state.trash -> "删除的文件会在这里保留一段时间"
                                state.query.isNotBlank() -> "换个关键词试试，或清空搜索条件"
                                else -> "点下方的快捷按钮上传文件或新建文件夹"
                            },
                        )
                    }
                }
                items(files, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        selected = file.id in state.selected,
                        selecting = state.selected.isNotEmpty(),
                        trash = state.trash,
                        open = {
                            when {
                                state.selected.isNotEmpty() -> model.select(file)
                                file.isDirectory && !state.trash -> model.openFolder(file)
                                else -> fileMenu = file
                            }
                        },
                        toggle = { model.select(file) },
                        menu = { fileMenu = file },
                    )
                }
                if (state.hasMore) {
                    item {
                        OutlinedButton(
                            onClick = model::loadMore,
                            enabled = !state.loading,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Text("加载更多 · ${state.files.size}/${state.total}")
                        }
                    }
                }
            }
        }

        if (state.selected.isNotEmpty() && !state.trash) {
            SelectionBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                download = { download(model.selectedFiles()) },
                move = { destination = model.selectedFiles() to false },
                copy = { destination = model.selectedFiles() to true },
                share = { share = model.selectedFiles() },
                delete = { delete = model.selectedFiles() },
            )
        }
    }

    fileMenu?.let { file ->
        FileActionSheet(
            file = file,
            trash = state.trash,
            dismiss = { fileMenu = null },
            dismissThen = { action -> fileMenu = null; action() },
            download = { download(listOf(file)) },
            preview = {
                if (!permission) requestFiles() else {
                    requestNotifications()
                    model.preview(file)
                    showTransfers()
                }
            },
            rename = { rename = file },
            move = { destination = listOf(file) to false },
            copy = { destination = listOf(file) to true },
            share = { share = listOf(file) },
            export = { model.exportRapid(listOf(file)) },
            trashAction = { delete = listOf(file) },
            restore = { model.restore(file) },
            permanent = { permanentlyDelete = file },
        )
    }
    input?.let { mode ->
        InputDialog(
            title = when (mode) {
                "folder" -> "新建文件夹"
                "rapid" -> "导入秒传数据"
                else -> "离线下载"
            },
            multiline = mode != "folder",
            label = when (mode) {
                "folder" -> "文件夹名称"
                "rapid" -> "秒传 JSON / 123FastLink"
                else -> "HTTP / Magnet / 迅雷链接"
            },
            confirm = {
                when (mode) {
                    "folder" -> model.createFolder(it)
                    "rapid" -> model.importRapid(it)
                    else -> model.offline(it)
                }
            },
            dismiss = { input = null },
        )
    }
    rename?.let { file ->
        InputDialog("重命名", file.name, confirm = { model.rename(file, it) }, dismiss = { rename = null })
    }
    share?.let { files ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { share = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("创建分享") },
            text = {
                Column {
                    Text(
                        "将分享 ${files.size} 项内容，链接创建后可在“分享”页管理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("提取码（可选）") },
                        placeholder = { Text("留空则无需提取码") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { model.share(files, password); share = null }) { Text("创建分享") }
            },
            dismissButton = { TextButton(onClick = { share = null }) { Text("取消") } },
        )
    }
    delete?.let { files ->
        AlertDialog(
            onDismissRequest = { delete = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("移入回收站？") },
            text = { Text("选中的 ${files.size} 项可以在回收站中恢复。") },
            confirmButton = {
                TextButton(onClick = { model.trash(files); delete = null }) {
                    Text("移入回收站", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("取消") } },
        )
    }
    permanentlyDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { permanentlyDelete = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("永久删除？", color = MaterialTheme.colorScheme.error) },
            text = { Text("${file.name}\n\n删除后将无法从回收站恢复。") },
            confirmButton = {
                TextButton(onClick = { model.permanentDelete(file); permanentlyDelete = null }) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { permanentlyDelete = null }) { Text("取消") } },
        )
    }
    destination?.let { (files, copy) ->
        CloudFolderPicker(
            model,
            files.map { it.id }.toSet(),
            copy,
            choose = { model.moveCopy(files, it, copy) },
            dismiss = { destination = null },
        )
    }
    if (localPicker) {
        NativeFilePicker(
            model,
            false,
            choose = {
                requestNotifications()
                model.upload(it)
                showTransfers()
            },
            dismiss = { localPicker = false },
        )
    }
}

@Composable
private fun SelectionHeader(count: Int, allSelected: Boolean, clear: () -> Unit, selectAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = clear) { Icon(Icons.Default.Close, "取消选择") }
        Column(Modifier.weight(1f)) {
            Text("已选择 $count 项", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(
                "轻点文件继续选择",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = selectAll) { Text(if (allSelected) "取消全选" else "全选") }
    }
}

@Composable
private fun OperationStrip(operation: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(operation.ifBlank { "正在处理" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionCard(request: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Default.Security, WarningAmber, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("开启文件访问", style = MaterialTheme.typography.titleSmall)
                Text(
                    "上传、下载与选择目录需要共享存储权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = request, shape = RoundedCornerShape(14.dp)) { Text("去开启") }
        }
    }
}

@Composable
private fun TrashNotice(total: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Delete, MaterialTheme.colorScheme.onSurfaceVariant, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("回收站", style = MaterialTheme.typography.titleSmall)
                Text(
                    "共 $total 项。长按不会选中，点开文件可恢复或永久删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StorageCard(profile: CloudProfile) {
    val fraction = if (profile.total > 0) (profile.used.toDouble() / profile.total).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = (profile.total - profile.used).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3155E8), Color(0xFF0E8F8A))))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("云盘空间", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatBytes(profile.used)} / ${formatBytes(profile.total)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Cloud, null, tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.22f),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "已使用 ${formatPercent(profile.used, profile.total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "剩余 ${formatBytes(remaining)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, change: (String) -> Unit, submit: () -> Unit, clear: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索云盘文件") },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = clear) { Icon(Icons.Default.Close, "清空搜索") }
                }
                IconButton(onClick = submit) { Icon(Icons.Default.Search, "搜索") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
    )
}

@Composable
private fun QuickActions(onUpload: () -> Unit, onFolder: () -> Unit, onOffline: () -> Unit, onRapid: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction(
            Icons.Default.Upload, "上传", MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer, onUpload, Modifier.weight(1f),
        )
        QuickAction(
            Icons.Default.CreateNewFolder, "文件夹", MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer, onFolder, Modifier.weight(1f),
        )
        QuickAction(
            Icons.Default.Cloud, "离线", MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer, onOffline, Modifier.weight(1f),
        )
        QuickAction(
            Icons.Default.Bolt, "秒传", MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceContainerHigh, onRapid, Modifier.weight(1f),
        )
    }
}

@Composable
private fun Breadcrumbs(items: List<String>, current: Int, go: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, name ->
            if (index > 0) {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
            Surface(
                onClick = { if (index != current) go(index) },
                shape = RoundedCornerShape(50),
                color = if (index == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    name,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ListToolbar(
    count: Long,
    query: String,
    trash: Boolean,
    sort: String,
    openSort: () -> Unit,
    sortMenu: Boolean,
    dismissSort: () -> Unit,
    chooseSort: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                query.isNotBlank() -> "搜索结果"
                trash -> "回收站文件"
                else -> "全部文件"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.width(7.dp))
        Text("$count 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = openSort, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Icon(Icons.Default.SwapVert, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    when (sort) {
                        "name" -> "名称"
                        "size" -> "大小"
                        else -> "最新"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(sortMenu, dismissSort) {
                listOf("id" to "最新文件优先", "name" to "按名称排序", "size" to "按大小排序").forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (sort == key) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { chooseSort(key) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: CloudFile,
    selected: Boolean,
    selecting: Boolean,
    trash: Boolean,
    open: () -> Unit,
    toggle: () -> Unit,
    menu: () -> Unit,
) {
    val look = fileLook(file)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = open, onLongClick = if (trash) open else toggle),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(file)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(look.label)
                        if (!file.isDirectory) append(" · ${formatBytes(file.size)}")
                        if (trash) append(" · 已删除")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { toggle() })
            } else {
                IconButton(onClick = menu) { Icon(Icons.Default.MoreVert, "文件操作") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionSheet(
    file: CloudFile,
    trash: Boolean,
    dismiss: () -> Unit,
    dismissThen: (() -> Unit) -> Unit,
    download: () -> Unit,
    preview: () -> Unit,
    rename: () -> Unit,
    move: () -> Unit,
    copy: () -> Unit,
    share: () -> Unit,
    export: () -> Unit,
    trashAction: () -> Unit,
    restore: () -> Unit,
    permanent: () -> Unit,
) {
    val look = fileLook(file)
    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null,
    ) {
        Column(Modifier.padding(bottom = 26.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileIcon(file, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(look.label)
                            if (!file.isDirectory) append(" · ${formatBytes(file.size)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(6.dp))
            if (trash) {
                SheetAction(
                    Icons.Default.Restore, "恢复文件",
                    "回到删除前的位置",
                ) { dismissThen(restore) }
                SheetAction(
                    Icons.Default.DeleteForever, "永久删除",
                    "删除后无法恢复",
                    tint = MaterialTheme.colorScheme.error,
                ) { dismissThen(permanent) }
            } else {
                SheetAction(
                    Icons.Default.Download,
                    if (file.isDirectory) "下载文件夹" else "下载",
                    if (file.isDirectory) "打包为压缩包后下载" else "保存到本地下载目录",
                ) { dismissThen(download) }
                if (!file.isDirectory) {
                    SheetAction(
                        Icons.Default.OpenInNew, "打开 / 预览",
                        "先下载到本地，再用其他应用打开",
                    ) { dismissThen(preview) }
                }
                SheetAction(Icons.Default.Edit, "重命名", "修改文件或文件夹名称") { dismissThen(rename) }
                SheetAction(Icons.Default.DriveFileMove, "移动到…", "选择新的云端目录") { dismissThen(move) }
                SheetAction(Icons.Default.FileCopy, "复制到…", "保留原文件并复制一份") { dismissThen(copy) }
                SheetAction(Icons.Default.Share, "创建分享", "生成可复制的分享链接") { dismissThen(share) }
                SheetAction(Icons.Default.Bolt, "导出秒传 JSON", "用于在其他客户端快速转存") { dismissThen(export) }
                HorizontalDivider(Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
                SheetAction(
                    Icons.Default.Delete, "移入回收站",
                    "之后可以在回收站中恢复",
                    tint = MaterialTheme.colorScheme.error,
                ) { dismissThen(trashAction) }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    modifier: Modifier = Modifier,
    download: () -> Unit,
    move: () -> Unit,
    copy: () -> Unit,
    share: () -> Unit,
    delete: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SelectionAction(Icons.Default.Download, "下载", MaterialTheme.colorScheme.primary, download, Modifier.weight(1f))
            SelectionAction(Icons.Default.DriveFileMove, "移动", MaterialTheme.colorScheme.onSurface, move, Modifier.weight(1f))
            SelectionAction(Icons.Default.FileCopy, "复制", MaterialTheme.colorScheme.onSurface, copy, Modifier.weight(1f))
            SelectionAction(Icons.Default.Share, "分享", MaterialTheme.colorScheme.onSurface, share, Modifier.weight(1f))
            SelectionAction(Icons.Default.Delete, "删除", MaterialTheme.colorScheme.error, delete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    action: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = action)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}