/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.service

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import io.github.azx8788a.pan123.api.CloudFile
import io.github.azx8788a.pan123.api.PanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DownloadEngine(private val api: PanApi, private val transfer: TransferHttp) {
    suspend fun run(
        cloudFile: CloudFile,
        destination: File,
        staging: File,
        onProgress: ProgressCallback,
    ): Long = withContext(Dispatchers.IO) {
        val expectedSize = if (cloudFile.isDirectory) -1L else cloudFile.size
        // Reconcile an interrupted worker that moved the file before persisting completion.
        if (!staging.exists() && destination.isFile && destination.length() > 0) {
            if (expectedSize >= 0 && destination.length() != expectedSize) throw IOException("目标文件长度与云端文件不符")
            if (validMd5(cloudFile.etag) && !fileMd5(destination).equals(cloudFile.etag, true)) {
                throw IOException("目标文件已被修改，请重新添加任务")
            }
            return@withContext destination.length()
        }
        if (!staging.exists()) staging.createNewFile()
        if (expectedSize >= 0 && staging.length() > expectedSize) RandomAccessFile(staging, "rw").use { it.setLength(0) }
        if (expectedSize < 0 || staging.length() != expectedSize) {
            onProgress(TransferProgress("获取下载链接", staging.length(), expectedSize))
            downloadTo(api.downloadLink(cloudFile), staging, expectedSize, onProgress)
        }
        if (expectedSize >= 0 && staging.length() != expectedSize) throw IOException("下载长度不完整，可继续重试")
        if (!cloudFile.isDirectory && validMd5(cloudFile.etag)) {
            val actual = fileMd5(staging) { onProgress(it.copy(phase = "校验下载文件")) }
            if (!actual.equals(cloudFile.etag, ignoreCase = true)) {
                RandomAccessFile(staging, "rw").use { it.setLength(0) }
                throw IOException("文件 MD5 不匹配，重试将重新下载")
            }
        }
        currentCoroutineContext().ensureActive()
        val length = staging.length()
        onProgress(TransferProgress("保存文件", length, length))
        if (destination.exists() && destination.length() != 0L) throw IOException("目标文件已被修改，请重新创建下载任务")
        Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        length
    }

    /** Exposed independently so range/resume behavior can be tested without a real account. */
    suspend fun downloadTo(url: String, staging: File, expectedSize: Long, onProgress: ProgressCallback = {}) =
        withContext(Dispatchers.IO) {
            var complete = false
            var restarted = false
            while (!complete) {
                currentCoroutineContext().ensureActive()
                val offset = staging.length()
                transfer.download(url, offset, expectedSize) { response ->
                    if (response.code == 416) {
                        val remoteSize = response.header("Content-Range")?.substringAfter("*/")?.toLongOrNull()
                        if (remoteSize == offset && (expectedSize < 0 || expectedSize == offset)) {
                            complete = true
                            return@download
                        }
                        if (restarted) throw TransferHttpException(416)
                        RandomAccessFile(staging, "rw").use { it.setLength(0) }
                        restarted = true
                        return@download
                    }
                    if (!response.isSuccessful) throw TransferHttpException(response.code)
                    val body = response.body ?: throw IOException("下载响应为空")
                    val range = if (response.code == 206) parseContentRange(response.header("Content-Range")) else null
                    if (response.code == 206 && (range == null || range.start != offset)) {
                        throw IOException("服务器返回了错误的续传偏移")
                    }
                    if (range != null && expectedSize >= 0 && range.total != expectedSize) {
                        throw IOException("云端文件大小已改变，请刷新文件列表后重新下载")
                    }
                    var written = if (range != null) offset else 0L
                    val total = range?.total ?: if (expectedSize >= 0) expectedSize else body.contentLength()
                    RandomAccessFile(staging, "rw").use { output ->
                        if (range == null) output.setLength(0) // Server ignored Range: restart, never append duplicates.
                        output.seek(written)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (total >= 0 && written + read > total) throw IOException("下载响应长度超出文件大小")
                                output.write(buffer, 0, read)
                                written += read
                                onProgress(TransferProgress("下载中", written, total))
                            }
                        }
                        output.fd.sync()
                    }
                    if (range != null && written != range.end + 1) throw IOException("分段下载不完整，可继续重试")
                    if (range == null && total >= 0 && written != total) throw IOException("下载被中断，可继续重试")
                    complete = total < 0 || written == total
                }
            }
        }

    data class ContentRange(val start: Long, val end: Long, val total: Long)

    companion object {
        fun parseContentRange(value: String?): ContentRange? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: return null
            val (start, end, total) = match.destructured
            val range = ContentRange(start.toLongOrNull() ?: return null, end.toLongOrNull() ?: return null,
                total.toLongOrNull() ?: return null)
            return range.takeIf { it.start <= it.end && it.end < it.total }
        }
    }
}
