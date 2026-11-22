/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(model: MainViewModel, modifier: Modifier = Modifier) {
    val session by model.session.collectAsStateWithLifecycle()
    var username by rememberSaveable {
        mutableStateOf(session?.username.orEmpty().takeUnless { it.startsWith("uid:") }.orEmpty())
    }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val qr by model.qr.collectAsStateWithLifecycle()
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )
    DisposableEffect(Unit) { onDispose { model.stopQr() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            BrandMark(82.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text("123pan Next", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "你的云盘，轻装上阵",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("登录账号", style = MaterialTheme.typography.titleLarge)
                Text(
                    "使用 123 云盘手机号或邮箱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    username,
                    { username = it },
                    label = { Text("手机号 / 邮箱") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    colors = fieldColors,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示密码")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy) model.login(username, password) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { model.login(username, password) },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("登录", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(19.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        "或",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = { model.startQr() },
                    enabled = !busy,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("使用 123 App 扫码登录")
                }
            }
        }

        if (session != null) {
            TextButton(onClick = { model.needsLogin.value = false }, modifier = Modifier.padding(top = 6.dp)) {
                Text("返回当前账号")
            }
        }
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "已保存账号",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(accounts, key = { it }) { account ->
                    AssistChip(
                        onClick = { if (!busy) model.switchAccount(account) },
                        label = { Text(account, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(17.dp)) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
        Spacer(Modifier.height(34.dp))
        Text(
            "第三方开源客户端 · 与 123 云盘官方无关",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
    }

    qr?.let { state ->
        var bitmap by remember(state.url) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(state.url) {
            if (state.url.isNotBlank()) {
                bitmap = withContext(Dispatchers.Default) {
                    val matrix = MultiFormatWriter().encode(state.url, BarcodeFormat.QR_CODE, 512, 512)
                    Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                        val pixels = IntArray(512 * 512) { index ->
                            if (matrix[index % 512, index / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                        }
                        setPixels(pixels, 0, 512, 0, 0, 512, 512)
                    }
                }
            }
        }
        Dialog(
            onDismissRequest = model::stopQr,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("扫码登录", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "使用 123 云盘 App 扫描二维码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        bitmap?.let {
                            Image(it.asImageBitmap(), "登录二维码", Modifier.fillMaxSize())
                        } ?: CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = model::stopQr,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("关闭") }
                        Button(
                            onClick = model::startQr,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("刷新二维码") }
                    }
                }
            }
        }
    }
}