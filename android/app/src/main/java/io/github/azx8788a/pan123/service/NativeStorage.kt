/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalEntry(val file: File, val directory: Boolean, val size: Long)

class NativeStorage(private val context: Context) {
    fun granted(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun requireAccess() { if (!granted()) throw IOException("请先在设置中开启文件访问权限") }

    fun roots(): List<File> = (listOf(Environment.getExternalStorageDirectory()) +
        context.getExternalFilesDirs(null).mapNotNull { path ->
            path?.parentFile?.parentFile?.parentFile?.parentFile
        }).distinctBy { it.absolutePath }

    suspend fun list(directory: File): List<LocalEntry> = withContext(Dispatchers.IO) {
        requireAccess()
        val children = directory.listFiles() ?: throw IOException("无法读取该目录：${directory.path}")
        children.map { LocalEntry(it, it.isDirectory, if (it.isFile) it.length() else 0) }
            .sortedWith(compareBy<LocalEntry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.file.name })
    }
}
