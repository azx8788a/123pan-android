/*
 * Kotlin port of upstream api/session.py, session_file.py and service/upload_service.py.
 * Copyright (C) 2026 123panNextGen; Android adaptation 2026.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.azx8788a.pan123.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PanApi(
    private val sessionProvider: () -> AccountSession?,
    private val loginUuid: () -> String,
    private val baseUrl: HttpUrl = "https://www.123pan.cn/".toHttpUrl(),
    private val fallbackUrl: HttpUrl? = "https://api.123278.com/".toHttpUrl(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build(),
) {
    @Volatile private var useFallback = false
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(username: String, password: String): String {
        val result = request("POST", "/b/api/user/sign_in", buildJsonObject {
            put("type", 1)
            put("passport", username)
            put("password", password)
        }, authenticated = false, successCodes = setOf(0, 200))
        return result.text("token").ifBlank { throw ApiException(-1, "登录响应缺少令牌") }
    }

    suspend fun profile(): CloudProfile = CloudProfile.fromJson(request("GET", "/b/api/user/info"))

    suspend fun qrGenerate(): JsonObject = request("GET", "/api/user/qr-code/generate",
        authenticated = false, targetBase = "https://login.123pan.com/".toHttpUrl())

    suspend fun qrPoll(id: String): JsonObject = request("GET", "/api/user/qr-code/result",
        query = mapOf("uniID" to id), authenticated = false, successCodes = setOf(0, 200),
        targetBase = "https://login.123pan.com/".toHttpUrl())

    suspend fun listFiles(parentId: Long, page: Int = 1, search: String = "", trashed: Boolean = false): FilePage {
        require(page >= 1)
        val data = request("GET", "/api/file/list/new", query = mapOf(
            "driveId" to "0", "limit" to "100", "next" to "0",
            "orderBy" to "file_id", "orderDirection" to "desc",
            "parentFileId" to parentId.toString(), "trashed" to trashed.toString(),
            "SearchData" to search, "Page" to page.toString(), "OnlyLookAbnormalFile" to "0",
        ))
        val files = data.array("InfoList", "infoList").mapNotNull { it as? JsonObject }.map(CloudFile::fromJson)
        return FilePage(files, data.number("Total", "total"), page)
    }

    suspend fun createFolder(name: String, parentId: Long): Long {
        val data = request("POST", "/a/api/file/upload_request", buildJsonObject {
            put("driveId", 0); put("etag", ""); put("fileName", name)
            put("parentFileId", parentId); put("size", 0); put("type", 1)
            put("duplicate", 1); put("NotReuse", true)
            put("event", "newCreateFolder"); put("operateType", 1)
        })
        return data.obj("Info", "info").number("FileId", "fileId").takeIf { it > 0 }
            ?: data.number("FileId", "fileId").takeIf { it > 0 }
            ?: throw ApiException(-1, "服务器未返回新文件夹的 ID")
    }

    suspend fun rename(fileId: Long, newName: String) {
        request("POST", "/a/api/file/rename", buildJsonObject {
            put("driveId", 0); put("fileId", fileId); put("fileName", newName)
        })
    }

    suspend fun trash(fileId: Long, operation: Boolean = true) {
        request("POST", "/a/api/file/trash", buildJsonObject {
            put("driveId", 0)
            put("fileTrashInfoList", buildJsonArray { add(buildJsonObject { put("FileId", fileId) }) })
            put("operation", operation)
        })
    }

    suspend fun permanentDelete(fileId: Long) {
        request("POST", "/b/api/file/delete", buildJsonObject {
            put("fileIdList", buildJsonArray { add(buildJsonObject { put("fileId", fileId) }) })
            put("event", "recycleDelete"); put("operatePlace", 1)
        })
    }

    suspend fun move(fileIds: List<Long>, destination: Long) {
        request("POST", "/b/api/file/mod_pid", buildJsonObject {
            put("fileIdList", buildJsonArray { fileIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("parentFileId", destination)
        })
    }

    suspend fun copy(files: List<CloudFile>, destination: Long): String {
        val data = request("POST", "/b/api/restful/goapi/v1/file/copy/async", buildJsonObject {
            put("fileList", buildJsonArray {
                files.forEach { file -> add(buildJsonObject {
                    put("FileId", file.id); put("FileName", file.name); put("Type", file.type)
                    put("Size", file.size); put("Etag", file.etag); put("S3KeyFlag", file.s3KeyFlag)
                    put("ParentFileId", file.parentId); put("DriveId", 0)
                }) }
            })
            put("targetFileId", destination)
        })
        return data.text("taskId", "taskID").ifBlank { throw ApiException(-1, "服务器未返回复制任务 ID") }
    }

    suspend fun copyStatus(taskId: String): JsonObject = request("GET", "/b/api/restful/goapi/v1/file/copy/task",
        query = mapOf("taskId" to taskId))

    suspend fun share(fileIds: List<Long>, name: String, password: String = ""): String {
        val data = request("POST", "/b/api/share/create", buildJsonObject {
            put("driveId", 0); put("expiration", "2099-12-12T08:00:00+08:00")
            put("fileIdList", fileIds.joinToString(",")); put("shareName", name); put("sharePwd", password)
            put("event", "shareCreate"); put("fileNum", fileIds.size); put("renameVisible", false)
            put("shareModality", 4); put("operatePlace", 2); put("trafficLimitSwitch", 1)
            put("trafficLimit", 0); put("trafficSwitch", 1); put("fillPwdSwitch", 0)
        }, targetBase = fallbackUrl)
        return "https://www.123pan.cn/s/" + data.text("ShareKey", "shareKey").ifBlank {
            throw ApiException(-1, "服务器未返回分享链接")
        }
    }

    suspend fun shares(next: String = "0"): JsonObject = request("GET", "/b/api/share/list", query = mapOf(
        "driveId" to "0", "limit" to "100", "next" to next, "orderBy" to "fileId",
        "orderDirection" to "desc", "SearchData" to "", "event" to "shareListFile", "operateType" to "1",
    ), targetBase = fallbackUrl)

    suspend fun deleteShare(id: Long) {
        request("POST", "/b/api/share/delete", buildJsonObject {
            put("driveId", 0); put("shareInfoList", buildJsonArray { add(buildJsonObject { put("shareId", id) }) })
            put("isPayShare", 0); put("event", "shareCancel"); put("operatePlace", 2)
        }, targetBase = fallbackUrl)
    }

    suspend fun offlineResolve(urls: String): List<JsonObject> =
        request("POST", "/b/api/v2/offline_download/task/resolve", buildJsonObject { put("urls", urls) },
            targetBase = fallbackUrl).array("list").mapNotNull { it as? JsonObject }

    suspend fun offlineSubmit(resources: List<JsonObject>): List<JsonObject> =
        request("POST", "/b/api/v2/offline_download/task/submit", buildJsonObject {
            put("resource_list", buildJsonArray { resources.forEach { add(it) } })
        }, targetBase = fallbackUrl).array("task_list").mapNotNull { it as? JsonObject }

    suspend fun downloadLink(file: CloudFile): DownloadLink {
        val body = if (file.isDirectory) buildJsonObject {
            put("fileIdList", buildJsonArray { add(buildJsonObject { put("fileId", file.id) }) })
        } else buildJsonObject {
            put("driveId", 0); put("etag", file.etag); put("fileId", file.id)
            put("s3keyFlag", file.s3KeyFlag); put("type", file.type)
            put("fileName", file.name); put("size", file.size)
        }
        val data = request("POST", if (file.isDirectory) "/a/api/file/batch_download_info" else "/a/api/file/download_info",
            body, successCodes = setOf(0, 5113, 5114))
        val direct = data.text("RedirectUrl", "redirect_url", "redirectUrl")
        if (direct.isNotBlank()) return DownloadLink(direct, true)
        val url = data.text("DownloadUrl", "downloadUrl")
        if (url.isBlank()) throw ApiException(-1, "服务端没有返回下载链接")
        return DownloadLink(url, false)
    }

    /** duplicate=1 preserves both files; never silently overwrites an existing cloud file. */
    suspend fun requestUpload(name: String, size: Long, md5: String, parentId: Long): JsonObject =
        request("POST", "/b/api/file/upload_request", buildJsonObject {
            put("driveId", 0); put("etag", md5); put("fileName", name)
            put("parentFileId", parentId); put("size", size); put("type", 0); put("duplicate", 1)
        })

    suspend fun uploadedParts(session: UploadSession): Set<Int> {
        val data = request("POST", "/b/api/file/s3_list_upload_parts", multipartBody(session))
        return data.array("Parts", "parts").mapNotNull { (it as? JsonObject)?.number("PartNumber", "partNumber")?.toInt() }
            .filter { it > 0 }.toSet()
    }

    suspend fun partUrl(session: UploadSession, number: Int): String {
        val data = request("POST", "/b/api/file/s3_repare_upload_parts_batch", buildJsonObject {
            put("bucket", session.bucket); put("key", session.key); put("uploadId", session.uploadId)
            put("StorageNode", session.storageNode)
            put("partNumberStart", number); put("partNumberEnd", number + 1)
        })
        return data.obj("presignedUrls").text(number.toString()).ifBlank {
            throw ApiException(-1, "服务端未返回第 $number 个分片的上传链接")
        }
    }

    suspend fun mergeUpload(session: UploadSession) {
        request("POST", "/b/api/file/s3_complete_multipart_upload", multipartBody(session))
    }

    suspend fun finalizeUpload(session: UploadSession): JsonObject =
        request("POST", "/b/api/file/upload_complete/v2", buildJsonObject {
            put("StorageNode", session.storageNode); put("bucket", session.bucket)
            put("fileId", session.fileId); put("fileSize", session.size)
            put("isMultipart", true); put("key", session.key); put("uploadId", session.uploadId)
        })

    private fun multipartBody(session: UploadSession) = buildJsonObject {
        put("bucket", session.bucket); put("key", session.key)
        put("uploadId", session.uploadId); put("storageNode", session.storageNode)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        query: Map<String, String> = emptyMap(),
        authenticated: Boolean = true,
        successCodes: Set<Int> = setOf(0),
        targetBase: HttpUrl? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        if (authenticated && session == null) throw SessionExpiredException()

        suspend fun perform(base: HttpUrl): JsonObject {
            val url = base.newBuilder().encodedPath(path).apply {
                query.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            val builder = Request.Builder().url(url)
                .header("platform", "android").header("devicename", "Xiaomi")
                .header("app-version", "61").header("x-app-version", "2.4.0")
                .header("user-agent", "123pan/v2.4.0(${session?.osVersion ?: "13"};Xiaomi)")
                .header("osversion", session?.osVersion ?: "13")
                .header("devicetype", session?.deviceType ?: "2211133C")
                .header("loginuuid", session?.loginUuid ?: loginUuid())
                .header("Accept", "application/json")
            if (authenticated && session != null) builder.header("Authorization", "Bearer ${session.token}")
            if (path.startsWith("/api/user/qr-code/")) builder.header("platform", "web").header("app-version", "3")
            builder.method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
            return http.withResponse(builder.build()) { response ->
                if (response.code == 401) throw SessionExpiredException()
                if (!response.isSuccessful) throw ApiException(response.code, "接口请求失败（HTTP ${response.code}）")
                val payload = response.body?.string() ?: throw ApiException(-1, "服务器返回空响应")
                val result = try { json.parseToJsonElement(payload).jsonObject } catch (_: Exception) {
                    throw ApiException(-1, "服务器未返回有效 JSON")
                }
                if ("code" !in result && "Code" !in result) throw ApiException(-1, "服务器响应缺少状态码")
                val code = result.number("code", "Code").toInt()
                val message = result.text("message", "Message", "msg")
                if (code == 2 || message.contains("session_expired", ignoreCase = true)) throw SessionExpiredException()
                if (code !in successCodes) throw ApiException(code, message.take(160).ifBlank { "请求失败（$code）" })
                result.obj("data", "Data")
            }
        }

        try {
            perform(targetBase ?: if (useFallback && fallbackUrl != null) fallbackUrl else baseUrl)
        } catch (error: IOException) {
            // Never automatically replay a state-changing POST after an ambiguous network failure.
            if (method != "GET" || targetBase != null || useFallback || fallbackUrl == null) throw error
            val result = perform(fallbackUrl)
            useFallback = true
            result
        }
    }
}
