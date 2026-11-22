/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.azx8788a.pan123.ui.MainViewModel
import io.github.azx8788a.pan123.ui.PanApp
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (::model.isInitialized) model.updatePermission()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as PanApplication).graph
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(graph) as T
        })[MainViewModel::class.java]
        setContent { PanApp(model, ::requestFileAccess, ::requestNotifications, ::openFile) }
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.updatePermission()
    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else storagePermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openFile(path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "打开文件"))
        } catch (_: Exception) { model.message.value = "没有可打开该文件的应用，或文件已被移动" }
    }
}
