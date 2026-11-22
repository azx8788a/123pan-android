/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.data

import android.content.Context
import android.os.Environment
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(val downloadDirectory: String, val concurrentTasks: Int, val uploadThreads: Int, val theme: String)

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SettingsState(
        preferences.getString("download_directory", null)
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "123panNext").path,
        preferences.getInt("concurrent_tasks", 2).coerceIn(1, 8),
        preferences.getInt("upload_threads", 2).coerceIn(1, 4),
        preferences.getString("theme", "system") ?: "system",
    ))
    val state = mutable.asStateFlow()

    fun update(value: SettingsState) {
        mutable.value = value
        preferences.edit().putString("download_directory", value.downloadDirectory)
            .putInt("concurrent_tasks", value.concurrentTasks).putInt("upload_threads", value.uploadThreads)
            .putString("theme", value.theme).apply()
    }
}
