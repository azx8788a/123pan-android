/* Port of upstream offline_service.py. SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.service

import io.github.azx8788a.pan123.api.*
import java.math.BigInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class RapidFile(val path: String, val etag: String, val size: Long)

object RapidData {
    private const val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private fun etag(value: String, base62: Boolean): String {
        if (!base62) return value.lowercase()
        var number = BigInteger.ZERO
        value.forEach { char ->
            val index = alphabet.indexOf(char)
            require(index >= 0) { "秒传数据包含无效 Base62 字符" }
            number = number * BigInteger.valueOf(62) + BigInteger.valueOf(index.toLong())
        }
        return number.toString(16).padStart(32, '0')
    }

    fun parse(text: String): List<RapidFile> {
        val input = text.trim()
        require(input.isNotBlank()) { "请输入秒传数据" }
        val decoded = if (input.startsWith('{')) {
            val root = Json.parseToJsonElement(input).jsonObject
            val common = root.text("commonPath")
            root.array("files").mapNotNull { it as? JsonObject }.map {
                RapidFile(common + it.text("path"), etag(it.text("etag"), root.flag("usesBase62EtagsInExport")), it.number("size"))
            }
        } else {
            var common = ""
            val body = if (input.startsWith("123FLCPV2$")) {
                input.removePrefix("123FLCPV2$").let { rest ->
                    require('%' in rest) { "秒传链接缺少路径分隔符" }
                    common = rest.substringBefore('%')
                    rest.substringAfter('%')
                }
            } else input
            body.replace("\r", "").split('$', '\n').mapNotNull { line ->
                val parts = line.split('#', limit = 3)
                if (parts.size != 3) null else parts[1].toLongOrNull()?.let {
                    RapidFile(common + parts[2], etag(parts[0], parts[0].length == 22), it)
                }
            }
        }
        return decoded.filter { it.path.isNotBlank() && validMd5(it.etag) && it.size >= 0 }.also {
            require(it.isNotEmpty()) { "未解析到有效的秒传文件" }
        }
    }

    fun export(files: List<RapidFile>): String = Json { prettyPrint = true }.encodeToString(buildJsonObject {
        put("exportVersion", "1.0"); put("usesBase62EtagsInExport", false); put("commonPath", "")
        put("files", Json.encodeToJsonElement(files)); put("totalFilesCount", files.size)
        put("totalSize", files.sumOf { it.size })
    })
}

class RapidTransferService(private val api: PanApi, private val files: FileRepository) {
    suspend fun importData(text: String, destination: Long, progress: (String) -> Unit): String {
        val items = RapidData.parse(text)
        val directories = mutableMapOf<Pair<Long, String>, Long>()
        val failures = mutableListOf<String>()
        var completed = 0
        items.forEachIndexed { index, item ->
            try {
                val parts = item.path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }
                var parent = destination
                parts.dropLast(1).forEach { name ->
                    val key = parent to name
                    parent = directories[key] ?: (files.allFiles(parent).firstOrNull { it.isDirectory && it.name == name }?.id
                        ?: files.createFolder(name, parent)).also { directories[key] = it }
                }
                val result = api.requestUpload(parts.last(), item.size, item.etag, parent)
                if (!result.flag("Reuse", "reuse")) throw ApiException(-1, "云端不存在相同内容")
                completed++
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { failures.add("${item.path}：${error.message}") }
            progress("秒传 ${index + 1}/${items.size}")
        }
        return "秒传成功 $completed/${items.size}" + if (failures.isEmpty()) "" else "\n" + failures.joinToString("\n")
    }

    suspend fun export(selected: List<CloudFile>): String {
        val items = mutableListOf<RapidFile>()
        suspend fun walk(file: CloudFile, path: String) {
            if (file.isDirectory) files.allFiles(file.id).forEach { walk(it, "$path/${it.name}") }
            else if (validMd5(file.etag)) items.add(RapidFile(path, file.etag, file.size))
        }
        selected.forEach { walk(it, it.name) }
        require(items.isNotEmpty()) { "所选文件没有可导出的 MD5 数据" }
        return RapidData.export(items)
    }
}
