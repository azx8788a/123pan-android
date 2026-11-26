/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.service

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import io.github.azx8788a.pan123.api.obj
import io.github.azx8788a.pan123.api.text
import io.github.azx8788a.pan123.api.withResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class TransferProgress(val phase: String, val done: Long, val total: Long)
typealias ProgressCallback = (TransferProgress) -> Unit

class TransferHttpException(val status: Int) : IOException("传输请求失败（HTTP $status）") {
    val retryable: Boolean get() = status == 408 || status == 429 || status >= 500
}

class DownloadProtocolException(message: String) : IOException(message)

private val redirectCodes = setOf(301, 302, 303, 307, 308)
private const val maxRedirectBodyBytes = 16_384L

/** Only inspect small JSON control responses, leaving attachments and file data untouched. */
private fun Response.jsonDownloadRedirect(expectedSize: Long): String? {
    val body = body ?: return null
    val subtype = body.contentType()?.subtype ?: return null
    val length = body.contentLength()
    if (code != 200 || (subtype != "json" && !subtype.endsWith("+json")) || length > maxRedirectBodyBytes ||
        (expectedSize >= 0 && length == expectedSize) ||
        header("Content-Disposition").orEmpty().substringBefore(';').trim().equals("attachment", ignoreCase = true)) {
        return null
    }
    val bytes = peekBody(maxRedirectBodyBytes + 1).bytes()
    if (bytes.size > maxRedirectBodyBytes || bytes.size.toLong() == expectedSize) return null
    val root = try {
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return null
    val redirect = root.obj("data", "Data").text("RedirectUrl", "redirect_url", "redirectUrl")
    if (redirect.isBlank()) return null
    val status = ((root["code"] ?: root["Code"]) as? JsonPrimitive)?.intOrNull ?: return null
    if (status != 0) throw DownloadProtocolException("下载跳转接口返回错误（code $status）")
    return redirect
}

private fun HttpUrl.hasUserInfo() = username.isNotEmpty() || password.isNotEmpty()

/** CDN/S3 requests deliberately use their own client; API headers are not attached. */
class TransferHttp(val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .followRedirects(false)
    .retryOnConnectionFailure(false)
    .build()) {

    suspend fun <T> download(url: String, offset: Long, expectedSize: Long, block: suspend (Response) -> T): T {
        // Request the provider-issued URL without changing its signed query or sending it to a proxy.
        var current = url.toHttpUrlOrNull()?.takeUnless { it.hasUserInfo() }
            ?: throw DownloadProtocolException("下载链接格式无效")
        repeat(10) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(current).header("Accept-Encoding", "identity")
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            var redirected = false
            var value: T? = null
            client.withResponse(request) { response ->
                val target = if (response.code in redirectCodes) {
                    response.header("Location")?.takeIf { it.isNotBlank() }
                        ?: throw DownloadProtocolException("下载重定向缺少有效地址")
                } else {
                    response.jsonDownloadRedirect(expectedSize)
                }
                if (target != null) {
                    val next = request.url.resolve(target)?.takeUnless { it.hasUserInfo() }
                        ?: throw DownloadProtocolException("下载重定向地址无效")
                    if (request.url.isHttps && !next.isHttps) throw DownloadProtocolException("已拒绝不安全的下载跳转")
                    current = next
                    redirected = true
                } else {
                    value = block(response)
                }
            }
            if (!redirected) {
                @Suppress("UNCHECKED_CAST")
                return value as T
            }
        }
        throw DownloadProtocolException("下载重定向次数过多")
    }
}

suspend fun fileMd5(file: File, onProgress: ProgressCallback = {}): String {
    val digest = MessageDigest.getInstance("MD5")
    var count = 0L
    val size = file.length()
    file.inputStream().use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            count += read
            onProgress(TransferProgress("计算 MD5", count, size))
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun validMd5(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{32}"))

fun safeFileName(name: String): String = name.replace(Regex("[\\\\/\\u0000]"), "_")
    .trim().takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "未命名文件"

/** Atomically reserve a new output name, keeping simultaneous same-name tasks separate. */
fun reserveOutput(directory: File, name: String): File {
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建下载目录：${directory.path}")
    val clean = safeFileName(name)
    val dot = clean.lastIndexOf('.').takeIf { it > 0 } ?: clean.length
    val stem = clean.substring(0, dot)
    val suffix = clean.substring(dot)
    var index = 0
    while (true) {
        val file = File(directory, if (index == 0) clean else "$stem ($index)$suffix")
        if (file.createNewFile()) return file
        index++
    }
}
