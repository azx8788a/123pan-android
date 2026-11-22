/*
 * Port of upstream service/upload_service.py (C) 2026 123panNextGen.
 * Uses bounded streaming bodies rather than buffering complete parts in memory.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.azx8788a.pan123.service

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import io.github.azx8788a.pan123.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

class UploadEngine(private val api: PanApi, private val transfer: TransferHttp) {
    companion object { const val PART_SIZE = 5L * 1024 * 1024 }

    suspend fun run(
        file: File,
        parentId: Long,
        resume: UploadSession?,
        threads: Int = 2,
        saveState: suspend (UploadSession) -> Unit,
        onProgress: ProgressCallback,
    ): Long = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.canRead()) throw IOException("本地文件不存在或无法读取：${file.name}")
        val size = file.length()
        val modified = file.lastModified()
        if (resume != null && (resume.size != size || resume.modifiedAt != modified)) {
            throw IOException("本地文件已改变，请重新添加上传任务")
        }
        var session = resume
        if (session == null) {
            val md5 = fileMd5(file, onProgress)
            if (file.length() != size || file.lastModified() != modified) throw IOException("计算 MD5 时本地文件发生改变")
            onProgress(TransferProgress("创建上传会话", 0, size))
            val data = api.requestUpload(file.name, size, md5, parentId)
            if (data.flag("Reuse", "reuse")) {
                return@withContext data.number("FileId", "fileId").takeIf { it > 0 }
                    ?: data.obj("Info", "info").number("FileId", "fileId").takeIf { it > 0 }
                    ?: throw ApiException(-1, "秒传响应缺少云端文件记录")
            }
            session = UploadSession(data.text("Bucket", "bucket"), data.text("Key", "key"),
                data.text("UploadId", "uploadId"), data.text("StorageNode", "storageNode"),
                data.number("FileId", "fileId"), size, modified, md5)
            if (session.bucket.isBlank() || session.key.isBlank() || session.uploadId.isBlank() || session.fileId <= 0) {
                throw ApiException(-1, "上传响应缺少分片会话信息")
            }
            saveState(session)
        }
        val activeSession = requireNotNull(session)
        if (activeSession.phase == "done") return@withContext activeSession.fileId
        if (activeSession.phase == "parts") {
            val count = maxOf(1, ((size + PART_SIZE - 1) / PART_SIZE).toInt())
            val uploaded = api.uploadedParts(activeSession).filter { it in 1..count }.toSet()
            val pending = (1..count).filter { it !in uploaded }
            val completed = AtomicLong(uploaded.sumOf { partLength(size, it) })
            val inFlight = ConcurrentHashMap<Int, Long>()
            val next = AtomicInteger(0)
            val progressLock = Any()
            fun report() = onProgress(TransferProgress("上传中", completed.get() + inFlight.values.sum(), size))
            report()
            coroutineScope {
                repeat(minOf(threads.coerceIn(1, 4), pending.size)) {
                    launch {
                        while (true) {
                            val index = next.getAndIncrement()
                            if (index >= pending.size) break
                            currentCoroutineContext().ensureActive()
                            val number = pending[index]
                            val length = partLength(size, number)
                            val job = currentCoroutineContext().job
                            val body = FilePartBody(file, (number - 1) * PART_SIZE, length,
                                checkActive = { if (!job.isActive) throw IOException("传输已停止") },
                                onBytes = { synchronized(progressLock) { inFlight[number] = it; report() } })
                            val request = Request.Builder().url(api.partUrl(activeSession, number)).put(body).build()
                            transfer.client.withResponse(request) { response ->
                                if (!response.isSuccessful) throw TransferHttpException(response.code)
                            }
                            synchronized(progressLock) {
                                inFlight.remove(number)
                                completed.addAndGet(length)
                                report()
                            }
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (file.length() != size || file.lastModified() != modified) throw IOException("上传过程中本地文件发生改变")
            onProgress(TransferProgress("确认分片", size, size))
            val confirmed = api.uploadedParts(activeSession)
            if ((1..count).any { it !in confirmed }) throw IOException("云端分片不完整，请重试续传")
            api.mergeUpload(activeSession)
            session = activeSession.copy(phase = "finalize")
            saveState(session)
        }
        val finalSession = requireNotNull(session)
        onProgress(TransferProgress("确认云端文件", size, size))
        if (size > 64L * 1024 * 1024) delay(3000)
        val result = api.finalizeUpload(finalSession)
        val info = result.obj("file_info", "fileInfo", "info", "Info")
        val id = info.number("fileId", "FileId").takeIf { it > 0 }
        if (id != null) {
            saveState(finalSession.copy(phase = "done", fileId = id))
            return@withContext id
        }
        val expectedId = result.number("fileId", "FileId").takeIf { it > 0 } ?: finalSession.fileId
        repeat(3) {
            val listing = api.listFiles(parentId)
            if (listing.files.any { it.id == expectedId && it.size == size }) {
                saveState(finalSession.copy(phase = "done", fileId = expectedId))
                return@withContext expectedId
            }
            delay(1500)
        }
        throw IOException("分片已提交，但云端文件记录尚未确认；请刷新文件列表")
    }

    private fun partLength(size: Long, number: Int): Long = minOf(PART_SIZE, maxOf(0, size - (number - 1) * PART_SIZE))
}

/** Each part reopens/seeks its own file; memory use is independent of the part size. */
class FilePartBody(
    private val file: File,
    private val offset: Long,
    private val length: Long,
    private val checkActive: () -> Unit = {},
    private val onBytes: (Long) -> Unit = {},
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (sent < length) {
                checkActive()
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), length - sent).toInt())
                if (read < 0) throw IOException("上传文件读取不完整")
                sink.write(buffer, 0, read)
                sent += read
                onBytes(sent)
            }
        }
    }
}
