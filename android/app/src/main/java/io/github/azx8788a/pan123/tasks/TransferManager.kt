/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.tasks

import android.content.Context
import androidx.work.*
import io.github.azx8788a.pan123.AppGraph
import io.github.azx8788a.pan123.api.CloudFile
import io.github.azx8788a.pan123.data.TransferEntity
import io.github.azx8788a.pan123.data.TransferStatus
import io.github.azx8788a.pan123.service.reserveOutput
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TransferManager(context: Context, private val graph: AppGraph) {
    private val workManager = WorkManager.getInstance(context)
    private val dao = graph.database.transfers()

    private fun request(taskId: String, accountKey: String) = OneTimeWorkRequestBuilder<TransferWorker>()
        .setInputData(workDataOf("task_id" to taskId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .addTag("account:$accountKey")
        .build()

    suspend fun upload(file: File, parentId: Long) = withContext(Dispatchers.IO) {
        graph.storage.requireAccess()
        if (!file.isFile || !file.canRead()) throw IOException("文件无法读取：${file.path}")
        val key = graph.auth.session.value?.accountKey ?: throw IOException("请先登录")
        val id = UUID.randomUUID().toString()
        val work = request(id, key)
        dao.insert(TransferEntity(id, key, "upload", file.name, file.absolutePath,
            parentId = parentId, total = file.length(), workId = work.id.toString()))
        workManager.enqueueUniqueWork("transfer:$id", ExistingWorkPolicy.KEEP, work)
    }

    suspend fun download(file: CloudFile, directory: File = File(graph.settings.state.value.downloadDirectory)) =
        withContext(Dispatchers.IO) {
            graph.storage.requireAccess()
            val key = graph.auth.session.value?.accountKey ?: throw IOException("请先登录")
            val id = UUID.randomUUID().toString()
            val work = request(id, key)
            val destination = reserveOutput(directory, if (file.isDirectory) "${file.name}.zip" else file.name)
            dao.insert(TransferEntity(id, key, "download", destination.name, destination.absolutePath,
                cloudJson = Json.encodeToString(file), parentId = file.parentId,
                total = if (file.isDirectory) -1 else file.size, workId = work.id.toString()))
            workManager.enqueueUniqueWork("transfer:$id", ExistingWorkPolicy.KEEP, work)
            id
        }

    suspend fun pause(id: String) {
        if (dao.control(id, TransferStatus.PAUSED, "已暂停") > 0) workManager.cancelUniqueWork("transfer:$id")
    }

    suspend fun resume(id: String) {
        val task = dao.get(id) ?: return
        if (task.accountKey != graph.auth.session.value?.accountKey) throw IOException("请先切换到该任务所属账号")
        val work = request(id, task.accountKey)
        if (dao.reschedule(id, work.id.toString()) > 0) {
            workManager.enqueueUniqueWork("transfer:$id", ExistingWorkPolicy.REPLACE, work)
        }
    }

    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        if (dao.control(id, TransferStatus.CANCELLED, "已取消") == 0) return@withContext
        workManager.cancelUniqueWork("transfer:$id")
        graph.taskLocks.getOrPut(id) { Mutex() }.withLock {
            val task = dao.get(id) ?: return@withLock
            if (task.kind == "download") {
                staging(task).delete()
                val destination = File(task.localPath)
                if (destination.isFile && destination.length() == 0L) destination.delete()
            }
        }
    }

    suspend fun pauseAccount(accountKey: String) {
        dao.pauseAccount(accountKey)
        workManager.cancelAllWorkByTag("account:$accountKey")
    }

    suspend fun remove(id: String) {
        val task = dao.get(id) ?: return
        if (task.status != TransferStatus.COMPLETE && task.status != TransferStatus.CANCELLED) cancel(id)
        dao.delete(id)
    }

    suspend fun clearFinished() {
        graph.auth.session.value?.accountKey?.let { dao.clearFinished(it) }
    }

    companion object {
        fun staging(task: TransferEntity): File = File(File(task.localPath).parentFile, ".123pan-${task.id}.part")
    }
}
