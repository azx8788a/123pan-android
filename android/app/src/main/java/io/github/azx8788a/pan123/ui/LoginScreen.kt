/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun LoginScreen(model: MainViewModel, modifier: Modifier = Modifier) {
    val session by model.session.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf(session?.username.orEmpty().takeUnless { it.startsWith("uid:") }.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val qr by model.qr.collectAsStateWithLifecycle()
    val accounts by model.accounts.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { model.stopQr() } }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.Cloud, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("123pan Next", style = MaterialTheme.typography.headlineLarge)
        Text("原生安卓客户端 · 基于 123panNextGen", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(username, { username = it }, label = { Text("手机号 / 邮箱") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示密码") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!busy) model.login(username, password) }), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = { model.login(username, password) }, enabled = !busy && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("登录")
        }
        TextButton(onClick = { model.startQr() }, enabled = !busy) { Text("使用 123 云盘 App 扫码登录") }
        if (session != null) TextButton(onClick = { model.needsLogin.value = false }) { Text("返回当前账号") }
        if (accounts.isNotEmpty()) {
            Text("已保存的账号", style = MaterialTheme.typography.labelLarge)
            accounts.forEach { key -> TextButton(onClick = { model.switchAccount(key) }, enabled = !busy) { Text(key) } }
        }
        Spacer(Modifier.height(24.dp))
        Text("第三方开源客户端，与 123 云盘官方无关。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    qr?.let { state ->
        var bitmap by remember(state.url) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(state.url) {
            if (state.url.isNotBlank()) bitmap = withContext(Dispatchers.Default) {
                val matrix = MultiFormatWriter().encode(state.url, BarcodeFormat.QR_CODE, 512, 512)
                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                    val pixels = IntArray(512 * 512) { index -> if (matrix[index % 512, index / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                    setPixels(pixels, 0, 512, 0, 0, 512, 512)
                }
            }
        }
        AlertDialog(onDismissRequest = model::stopQr, title = { Text("扫码登录") }, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                bitmap?.let { Image(it.asImageBitmap(), "登录二维码", Modifier.size(240.dp)) }
                    ?: CircularProgressIndicator(Modifier.padding(40.dp))
                Text(state.message, Modifier.padding(top = 12.dp))
            }
        }, confirmButton = { TextButton(onClick = model::startQr) { Text("刷新") } },
            dismissButton = { TextButton(onClick = model::stopQr) { Text("关闭") } })
    }
}
