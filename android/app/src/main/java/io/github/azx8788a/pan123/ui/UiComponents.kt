/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.azx8788a.pan123.api.CloudFile

data class FileLook(val icon: ImageVector, val color: Color, val label: String)

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "avif")
private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "m4v", "3gp")
private val audioExtensions = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "ape")
private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "iso")
private val codeExtensions = setOf(
    "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "scss", "json", "xml",
    "yml", "yaml", "sh", "c", "h", "cpp", "hpp", "go", "rs", "rb", "php", "sql", "toml",
)
private val documentExtensions = setOf("txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "epub")

fun fileLook(file: CloudFile): FileLook {
    if (file.isDirectory) return FileLook(Icons.Default.Folder, Color(0xFFE39A2D), "文件夹")
    val extension = file.name.substringAfterLast('.', "").lowercase()
    val mime = file.contentType.lowercase()
    return when {
        extension == "pdf" || mime == "application/pdf" ->
            FileLook(Icons.Default.PictureAsPdf, Color(0xFFE15443), "PDF 文档")
        extension in imageExtensions || mime.startsWith("image/") ->
            FileLook(Icons.Default.Image, Color(0xFFE45E91), "图片")
        extension in videoExtensions || mime.startsWith("video/") ->
            FileLook(Icons.Default.Movie, Color(0xFF8465E8), "视频")
        extension in audioExtensions || mime.startsWith("audio/") ->
            FileLook(Icons.Default.MusicNote, Color(0xFF10A37C), "音频")
        extension in archiveExtensions ->
            FileLook(Icons.Default.Archive, Color(0xFFDD8A22), "压缩包")
        extension in codeExtensions ->
            FileLook(Icons.Default.Code, Color(0xFF2E8BDF), "代码")
        extension in documentExtensions ->
            FileLook(Icons.Default.Description, Color(0xFF3B63F3), "文档")
        else -> FileLook(Icons.Default.InsertDriveFile, Color(0xFF718096), "文件")
    }
}

@Composable
fun FileIcon(file: CloudFile, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    val look = fileLook(file)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(look.color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(look.icon, null, tint = look.color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    color: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = color,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}