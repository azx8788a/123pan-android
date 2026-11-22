/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.azx8788a.pan123.*
import io.github.azx8788a.pan123.api.*
import io.github.azx8788a.pan123.data.*
import io.github.azx8788a.pan123.service.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TransferWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val graph = (context.applicationContext as PanApplication).graph
    private val dao = graph.database.transfers()
    private val taskId get() = inputData.getString("task_id").orEmpty()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val task = dao.get(taskId)
        return foreground(task?.name ?: "文件传输", TransferProgress("准备传输", 0, -1))
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        graph.taskLocks.getOrPut(taskId) { Mutex() }.withLock {
            val task = dao.get(taskId) ?: return@withLock Result.failure()
            if (task.workId != id.toString() || task.status !in listOf(TransferStatus.QUEUED, TransferStatus.RUNNING)) {
                return@withLock Result.success()
            }
            val session = graph.auth.session.value
            if (session == null || session.accountKey != task.accountKey) {
                dao.workerStatus(taskId, id.toString(), TransferStatus.PAUSED, "请先登录该任务所属账号")
                return@withLock Result.failure()
            }
            try {
                graph.storage.requireAccess()
                setForeground(getForegroundInfo())
                graph.gate.withPermit {
                    currentCoroutineContext().ensureActive()
                    if (dao.workerStatus(taskId, id.toString(), TransferStatus.RUNNING, "准备传输") == 0) {
                        return@withPermit Result.success()
                    }
                    // Bind this worker to its original account even if the UI switches accounts.
                    val api = PanApi({ session }, { session.loginUuid })
                    val progress = AtomicReference(TransferProgress("准备传输", task.done, task.total))
                    coroutineScope {
                        val reporter = launch {
                            while (isActive) {
                                val snapshot = progress.get()
                                dao.progress(taskId, id.toString(), snapshot.done, snapshot.total, snapshot.phase)
                                setForeground(foreground(task.name, snapshot))
                                delay(1000)
                            }
                        }
                        try {
                            val length = if (task.kind == "download") {
                                DownloadEngine(api, graph.transferHttp).run(json.decodeFromString<CloudFile>(task.cloudJson),
                                    File(task.localPath), TransferManager.staging(task)) { progress.set(it) }
                            } else {
                                val saved = task.uploadJson.takeIf { it.isNotBlank() }?.let { json.decodeFromString<UploadSession>(it) }
                                UploadEngine(api, graph.transferHttp).run(File(task.localPath), task.parentId, saved,
                                    graph.settings.state.value.uploadThreads,
                                    saveState = { dao.uploadState(taskId, id.toString(), json.encodeToString(it)) },
                                    onProgress = { progress.set(it) })
                                File(task.localPath).length()
                            }
                            dao.progress(taskId, id.toString(), length, length, "已完成")
                            dao.workerStatus(taskId, id.toString(), TransferStatus.COMPLETE, "已完成")
                            Result.success()
                        } finally {
                            reporter.cancelAndJoin()
                        }
                    }
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    dao.workerStatus(taskId, id.toString(), TransferStatus.QUEUED, "任务被中断，等待继续")
                }
                throw error
            } catch (error: Exception) {
                val retry = when (error) {
                    is FileNotFoundException -> false
                    is TransferHttpException -> error.retryable || error.status == 403
                    is IOException -> graph.storage.granted()
                    is ApiException -> error.code == 429 || error.code >= 500
                    else -> false
                } && runAttemptCount < 4
                val text = when (error) {
                    is SessionExpiredException -> "登录已过期，请重新登录后继续"
                    is ApiException -> error.message ?: "接口请求失败"
                    else -> error.message?.take(160) ?: "传输失败"
                }
                dao.workerStatus(taskId, id.toString(), if (retry) TransferStatus.QUEUED else TransferStatus.FAILED,
                    if (retry) "$text；将自动重试" else text)
                if (retry) Result.retry() else Result.failure()
            }
        }
    }

    private fun foreground(name: String, progress: TransferProgress): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("transfers", "文件传输", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getBroadcast(applicationContext, taskId.hashCode(),
            Intent(applicationContext, TransferActionReceiver::class.java).putExtra("task_id", taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val percent = if (progress.total > 0) (progress.done.toDouble() / progress.total * 100).toInt().coerceIn(0, 100) else 0
        val notification = NotificationCompat.Builder(applicationContext, "transfers")
            .setSmallIcon(R.drawable.ic_transfer).setContentTitle(name).setContentText(progress.phase)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent, progress.total <= 0).setGroup("file_transfers")
            .addAction(R.drawable.ic_transfer, "暂停", pause).build()
        return ForegroundInfo(taskId.hashCode() and Int.MAX_VALUE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
