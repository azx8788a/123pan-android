/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.azx8788a.pan123.AppGraph
import io.github.azx8788a.pan123.api.*
import io.github.azx8788a.pan123.data.*
import io.github.azx8788a.pan123.service.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CloudPath(val id: Long, val name: String)
data class CloudState(
    val path: List<CloudPath> = listOf(CloudPath(0, "我的云盘")),
    val files: List<CloudFile> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val query: String = "",
    val page: Int = 0,
    val total: Long = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val trash: Boolean = false,
)
data class QrState(val url: String = "", val message: String = "正在获取二维码")
data class TextResult(val title: String, val text: String)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val graph: AppGraph) : ViewModel() {
    val session = graph.auth.session
    val accounts = graph.auth.accounts
    val settings = graph.settings.state
    private val mutableCloud = MutableStateFlow(CloudState())
    val cloud = mutableCloud.asStateFlow()
    val profile = MutableStateFlow<CloudProfile?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val operation = MutableStateFlow("")
    val needsLogin = MutableStateFlow(false)
    val qr = MutableStateFlow<QrState?>(null)
    val textResult = MutableStateFlow<TextResult?>(null)
    val storageGranted = MutableStateFlow(graph.storage.granted())
    val openRequest = MutableStateFlow<String?>(null)
    private var pendingPreview: String? = null
    val shareLinks = MutableStateFlow<List<ShareLink>>(emptyList())
    val shareLoading = MutableStateFlow(false)
    val shareHasMore = MutableStateFlow(false)
    private var shareNext = "0"
    private var loadJob: Job? = null
    private var qrJob: Job? = null
    private var loadGeneration = 0
    val tasks = session.flatMapLatest { account ->
        if (account == null) flowOf(emptyList()) else graph.database.transfers().observe(account.accountKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            session.collectLatest { account ->
                loadJob?.cancel()
                mutableCloud.value = CloudState()
                profile.value = null
                shareLinks.value = emptyList()
                if (account != null) {
                    refresh()
                    try { profile.value = graph.auth.profile() }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { report(error) }
                }
            }
        }
        viewModelScope.launch {
            var initialized = false
            var previous = emptySet<String>()
            tasks.collect { transfers ->
                transfers.firstOrNull { it.id == pendingPreview && it.status == TransferStatus.COMPLETE }?.let {
                    openRequest.value = it.localPath
                    pendingPreview = null
                }
                val completed = transfers.filter { it.kind == "upload" && it.status == TransferStatus.COMPLETE }
                val ids = completed.map { it.id }.toSet()
                if (initialized && completed.any { it.id !in previous && it.parentId == cloud.value.path.last().id }) refresh()
                previous = ids
                initialized = true
            }
        }
    }

    fun updatePermission() { storageGranted.value = graph.storage.granted() }
    fun roots(): List<File> = graph.storage.roots()
    suspend fun localFiles(path: File) = graph.storage.list(path)
    suspend fun folderContents(id: Long): List<CloudFile> = graph.files.allFiles(id).filter { it.isDirectory }

    private fun report(error: Exception) {
        if (error is SessionExpiredException) needsLogin.value = true
        message.value = error.message?.take(300) ?: "操作失败，请重试"
    }

    private fun action(label: String, success: String? = null, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            operation.value = label
            try { block(); if (success != null) message.value = success }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { report(error) }
            finally { busy.value = false; operation.value = "" }
        }
    }

    fun login(username: String, password: String) = action("正在登录") {
        stopQr()
        val previousAccount = session.value?.accountKey
        graph.auth.login(username, password)
        if (previousAccount != null && previousAccount != session.value?.accountKey) graph.transfers.pauseAccount(previousAccount)
        needsLogin.value = false
    }
    fun addAccount() { needsLogin.value = true }

    fun switchAccount(key: String) = action("切换账号") {
        session.value?.accountKey?.let { graph.transfers.pauseAccount(it) }
        graph.auth.select(key)
        needsLogin.value = false
    }

    fun logout() = action("退出登录") {
        session.value?.accountKey?.let { graph.transfers.pauseAccount(it) }
        graph.auth.logout()
        needsLogin.value = false
    }

    fun startQr() {
        stopQr()
        qr.value = QrState()
        qrJob = viewModelScope.launch {
            try {
                val data = graph.auth.qrGenerate()
                val id = data.text("uniID", "uniId")
                val url = data.text("url")
                require(id.isNotBlank() && url.isNotBlank()) { "服务端未返回二维码信息" }
                qr.value = QrState(url, "请使用 123 云盘 App 扫码确认")
                while (isActive) {
                    delay(2000)
                    val result = graph.auth.qrPoll(id)
                    val token = result.text("token")
                    if (token.isNotBlank()) {
                        graph.auth.acceptQrToken(token)
                        needsLogin.value = false
                        qr.value = null
                        return@launch
                    }
                    when (result.number("loginStatus").toInt()) {
                        1 -> qr.value = QrState(url, "已扫码，请在 123 云盘 App 确认")
                        2 -> { qr.value = QrState(url, "已拒绝，请刷新二维码"); break }
                        3 -> { qr.value = QrState(url, "请使用 123 云盘 App 扫码，暂不支持微信确认"); break }
                        4 -> { qr.value = QrState(url, "二维码已过期，请刷新"); break }
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { qr.value = QrState(message = error.message ?: "二维码获取失败") }
        }
    }
    fun stopQr() { qrJob?.cancel(); qrJob = null; qr.value = null }

    fun search(value: String) { mutableCloud.update { it.copy(query = value) } }
    fun refresh() = load(reset = true)
    fun loadMore() { if (!cloud.value.loading && cloud.value.hasMore) load(false) }

    private fun load(reset: Boolean) {
        if (session.value == null) return
        loadJob?.cancel()
        val generation = ++loadGeneration
        val before = cloud.value
        val page = if (reset) 1 else before.page + 1
        mutableCloud.update { it.copy(loading = true, selected = if (reset) emptySet() else it.selected) }
        loadJob = viewModelScope.launch {
            try {
                val result = graph.files.list(before.path.last().id, page, before.query, before.trash)
                if (generation != loadGeneration) return@launch
                val loaded = if (reset) result.files else (before.files + result.files).distinctBy { it.id }
                mutableCloud.update { it.copy(files = loaded, page = page, total = result.total,
                    hasMore = result.hasMore && (reset || loaded.size > before.files.size), loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { report(error) }
            finally { if (generation == loadGeneration) mutableCloud.update { it.copy(loading = false) } }
        }
    }

    fun openFolder(file: CloudFile) {
        if (!file.isDirectory || cloud.value.trash) return
        mutableCloud.update { it.copy(path = it.path + CloudPath(file.id, file.name), files = emptyList(), query = "", selected = emptySet()) }
        refresh()
    }
    fun goBack() {
        if (cloud.value.selected.isNotEmpty()) { mutableCloud.update { it.copy(selected = emptySet()) }; return }
        if (cloud.value.trash) { toggleTrash(); return }
        if (cloud.value.path.size > 1) {
            mutableCloud.update { it.copy(path = it.path.dropLast(1), files = emptyList(), query = "", selected = emptySet()) }
            refresh()
        }
    }
    fun goToPath(index: Int) {
        if (index !in cloud.value.path.indices || index == cloud.value.path.lastIndex) return
        mutableCloud.update { it.copy(path = it.path.take(index + 1), files = emptyList(), selected = emptySet(), query = "") }
        refresh()
    }
    fun toggleTrash() {
        mutableCloud.value = CloudState(path = listOf(CloudPath(0, if (cloud.value.trash) "我的云盘" else "回收站")), trash = !cloud.value.trash)
        refresh()
    }
    fun select(file: CloudFile) { mutableCloud.update { it.copy(selected = if (file.id in it.selected) it.selected - file.id else it.selected + file.id) } }
    fun selectAll() { mutableCloud.update { it.copy(selected = if (it.selected.size == it.files.size) emptySet() else it.files.map { file -> file.id }.toSet()) } }
    fun selectedFiles(): List<CloudFile> = cloud.value.files.filter { it.id in cloud.value.selected }

    fun createFolder(name: String) = action("创建文件夹", "文件夹已创建") {
        graph.files.createFolder(name, cloud.value.path.last().id); refresh()
    }
    fun rename(file: CloudFile, name: String) = action("重命名", "已重命名") { graph.files.rename(file, name); refresh() }
    fun trash(files: List<CloudFile>) = action("移入回收站", "已移入回收站") { files.forEach { graph.files.trash(it) }; refresh() }
    fun restore(file: CloudFile) = action("恢复文件", "已恢复") { graph.files.restore(file); refresh() }
    fun permanentDelete(file: CloudFile) = action("永久删除", "已永久删除") { graph.files.permanentDelete(file); refresh() }
    fun moveCopy(files: List<CloudFile>, target: Long, copy: Boolean) = action(if (copy) "复制文件" else "移动文件", "操作已完成") {
        if (copy) graph.files.copy(files, target) else graph.files.move(files, target)
        refresh()
    }
    fun share(files: List<CloudFile>, password: String) = action("创建分享") {
        val url = graph.files.share(files, password)
        textResult.value = TextResult("分享链接", url + if (password.isBlank()) "" else "\n提取码：$password")
    }
    fun exportRapid(files: List<CloudFile>) = action("导出秒传数据") {
        textResult.value = TextResult("秒传 JSON", RapidTransferService(graph.api, graph.files).export(files))
    }
    fun importRapid(text: String) = action("导入秒传数据") {
        val result = RapidTransferService(graph.api, graph.files).importData(text, cloud.value.path.last().id) { operation.value = it }
        textResult.value = TextResult("秒传结果", result); refresh()
    }
    fun offline(text: String) = action("提交离线下载") { textResult.value = TextResult("离线任务", graph.files.offline(text)); refresh() }

    fun upload(files: List<File>) = action("添加上传任务") {
        val destination = cloud.value.path.last().id
        val originalAccount = session.value?.accountKey
        var count = 0
        val seen = mutableSetOf<String>()
        suspend fun add(file: File, parent: Long) {
            currentCoroutineContext().ensureActive()
            if (session.value?.accountKey != originalAccount) throw IOException("账号已切换，请重新选择上传文件")
            if (!seen.add(file.canonicalPath)) return
            if (file.isDirectory) {
                val children = file.listFiles() ?: throw IOException("无法读取目录：${file.path}")
                val id = graph.files.createFolder(file.name, parent)
                children.forEach { add(it, id) }
            } else if (file.isFile) {
                graph.transfers.upload(file, parent); count++
                operation.value = "已添加 $count 个上传任务"
            }
        }
        withContext(Dispatchers.IO) { files.forEach { add(it, destination) } }
        message.value = "已添加 $count 个文件上传任务"
        refresh()
    }
    fun download(files: List<CloudFile>) = action("添加下载任务") {
        files.forEach { graph.transfers.download(it) }
        message.value = "已添加 ${files.size} 个下载任务"
    }
    fun preview(file: CloudFile) = action("准备打开文件") {
        pendingPreview = graph.transfers.download(file)
        message.value = "下载完成后将打开文件"
    }
    private fun taskAction(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { report(e) }
    } }
    fun pause(id: String) = taskAction { graph.transfers.pause(id) }
    fun resume(id: String) = taskAction { graph.transfers.resume(id) }
    fun cancel(id: String) = taskAction { graph.transfers.cancel(id) }
    fun removeTask(id: String) = taskAction { graph.transfers.remove(id) }
    fun clearTasks() = taskAction { graph.transfers.clearFinished() }
    fun updateSettings(value: SettingsState) { graph.settings.update(value) }

    fun loadShares(reset: Boolean = true) {
        if (shareLoading.value) return
        viewModelScope.launch {
            shareLoading.value = true
            try {
                val (links, next) = graph.files.shares(if (reset) "0" else shareNext)
                shareLinks.value = if (reset) links else (shareLinks.value + links).distinctBy { it.id }
                shareHasMore.value = next != "-1" && next != shareNext && links.isNotEmpty()
                shareNext = next
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { report(error) }
            finally { shareLoading.value = false }
        }
    }
    fun deleteShare(id: Long) = action("取消分享", "已取消分享") { graph.files.deleteShare(id); loadShares() }
}
