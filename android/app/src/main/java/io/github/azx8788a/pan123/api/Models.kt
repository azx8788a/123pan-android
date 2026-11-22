/*
 * Derived from 123panNextGen/123pan (C) 2026 123panNextGen.
 * Android adaptation (C) 2026 123pan Android contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.azx8788a.pan123.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class AccountSession(
    val username: String,
    val token: String,
    val loginUuid: String,
    val osVersion: String = "13",
    val deviceType: String = "2211133C",
) {
    val accountKey: String get() = username.trim().lowercase(java.util.Locale.ROOT)
    override fun toString(): String = "AccountSession(credentials=REDACTED)"
}

@Serializable
data class CloudFile(
    val id: Long,
    val name: String,
    val type: Int,
    val size: Long,
    val etag: String = "",
    val s3KeyFlag: String = "",
    val parentId: Long = 0,
    val contentType: String = "application/octet-stream",
) {
    val isDirectory: Boolean get() = type == 1

    companion object {
        fun fromJson(json: JsonObject) = CloudFile(
            id = json.number("FileId", "fileId"),
            name = json.text("FileName", "fileName"),
            type = json.number("Type", "type").toInt(),
            size = json.number("Size", "size"),
            etag = json.text("Etag", "etag"),
            s3KeyFlag = json.text("S3KeyFlag", "s3keyFlag", "s3KeyFlag"),
            parentId = json.number("ParentFileId", "parentFileId"),
            contentType = json.text("ContentType", "contentType").ifBlank { "application/octet-stream" },
        )
    }
}

data class FilePage(val files: List<CloudFile>, val total: Long, val page: Int) {
    val hasMore: Boolean get() = files.isNotEmpty() && page.toLong() * 100 < total
}

data class CloudProfile(val uid: Long, val nickname: String, val used: Long, val total: Long) {
    companion object {
        fun fromJson(json: JsonObject) = CloudProfile(
            uid = json.number("UID", "uid"),
            nickname = json.text("Nickname", "nickname"),
            used = json.number("SpaceUsed", "spaceUsed"),
            total = json.number("SpacePermanent", "spacePermanent") + json.number("SpaceTemp", "spaceTemp"),
        )
    }
}

@Serializable
data class UploadSession(
    val bucket: String,
    val key: String,
    val uploadId: String,
    val storageNode: String,
    val fileId: Long,
    val size: Long,
    val modifiedAt: Long,
    val md5: String,
    val phase: String = "parts",
) {
    override fun toString(): String = "UploadSession(metadata=REDACTED)"
}

data class DownloadLink(val url: String, val direct: Boolean)

data class ShareLink(val id: Long, val name: String, val url: String, val password: String) {
    companion object {
        fun fromJson(json: JsonObject) = ShareLink(
            id = json.number("ShareId", "shareId"),
            name = json.text("ShareName", "shareName"),
            url = json.text("ShareUrl", "shareUrl").ifBlank { "https://www.123pan.cn/s/${json.text("ShareKey", "shareKey")}" },
            password = json.text("SharePwd", "sharePwd"),
        )
    }
}

open class ApiException(val code: Int, message: String) : Exception(message)
class SessionExpiredException : ApiException(2, "登录已过期，请重新登录")

fun JsonObject.text(vararg keys: String): String = keys.firstNotNullOfOrNull {
    (get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}.orEmpty()

fun JsonObject.number(vararg keys: String): Long = keys.firstNotNullOfOrNull {
    (get(it) as? JsonPrimitive)?.longOrNull
} ?: 0

fun JsonObject.flag(vararg keys: String): Boolean = keys.firstNotNullOfOrNull {
    (get(it) as? JsonPrimitive)?.booleanOrNull
} ?: false

fun JsonObject.obj(vararg keys: String): JsonObject = keys.firstNotNullOfOrNull {
    get(it) as? JsonObject
} ?: JsonObject(emptyMap())

fun JsonObject.array(vararg keys: String): JsonArray = keys.firstNotNullOfOrNull {
    get(it) as? JsonArray
} ?: JsonArray(emptyList())
