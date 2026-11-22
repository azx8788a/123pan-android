/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.service

import io.github.azx8788a.pan123.api.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

class FileRepository(private val api: PanApi) {
    suspend fun list(parentId: Long, page: Int = 1, search: String = "", trash: Boolean = false) =
        api.listFiles(parentId, page, search, trash)

    suspend fun allFiles(parentId: Long): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        val seen = mutableSetOf<Long>()
        var page = 1
        do {
            val result = list(parentId, page++)
            val oldSize = files.size
            files.addAll(result.files.filter { seen.add(it.id) })
            if (files.size == oldSize || !result.hasMore) break
            delay(100)
        } while (true)
        return files
    }

    suspend fun createFolder(name: String, parentId: Long): Long {
        require(name.isNotBlank() && '/' !in name && '\\' !in name) { "请输入有效文件夹名" }
        return api.createFolder(name.trim(), parentId)
    }

    suspend fun rename(file: CloudFile, name: String) {
        require(name.isNotBlank() && '/' !in name && '\\' !in name) { "请输入有效文件名" }
        api.rename(file.id, name.trim())
    }
    suspend fun trash(file: CloudFile) = api.trash(file.id)
    suspend fun restore(file: CloudFile) = api.trash(file.id, false)
    suspend fun permanentDelete(file: CloudFile) = api.permanentDelete(file.id)
    suspend fun move(files: List<CloudFile>, destination: Long) = api.move(files.map { it.id }, destination)
    suspend fun copy(files: List<CloudFile>, destination: Long) {
        val id = api.copy(files, destination)
        repeat(60) {
            val status = api.copyStatus(id)
            when (status.number("status").toInt()) {
                2 -> return
                3 -> throw ApiException(-1, status.text("failMsg").ifBlank { "云端复制失败" })
            }
            delay(1000)
        }
        throw ApiException(-1, "复制仍在处理中，请稍后刷新目标目录")
    }
    suspend fun share(files: List<CloudFile>, password: String) = api.share(files.map { it.id }, files.first().name, password)
    suspend fun shares(next: String = "0"): Pair<List<ShareLink>, String> {
        val result = api.shares(next)
        return result.array("InfoList", "infoList").mapNotNull { it as? JsonObject }.map(ShareLink::fromJson) to
            result.text("Next", "next").ifBlank { "-1" }
    }
    suspend fun deleteShare(id: Long) = api.deleteShare(id)

    suspend fun offline(urls: String): String {
        val resolved = api.offlineResolve(urls)
        val valid = resolved.filter { it.number("result") == 0L }
        if (valid.isEmpty()) throw ApiException(-1, resolved.firstOrNull()?.text("err_msg").orEmpty().ifBlank { "没有可提交的离线资源" })
        val resources = valid.map { item -> buildJsonObject {
            put("resource_id", item.number("id"))
            put("select_file_id", buildJsonArray {
                item.array("files").forEach { file -> (file as? JsonObject)?.let { add(JsonPrimitive(it.number("id", "file_id"))) } }
            })
        } }
        val tasks = api.offlineSubmit(resources)
        val failures = tasks.filter { it.number("result") != 0L }
        if (failures.isNotEmpty()) throw ApiException(-1, failures.first().text("err_msg").ifBlank { "部分离线任务提交失败" })
        return "已提交 ${tasks.size} 个离线任务，完成后请刷新云端根目录"
    }
}
