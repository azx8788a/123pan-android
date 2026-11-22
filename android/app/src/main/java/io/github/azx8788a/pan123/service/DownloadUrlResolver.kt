/*
 * Port of upstream api/download_url.py, Copyright (C) 2026 123panNextGen.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.azx8788a.pan123.service

import java.net.URI
import java.util.Base64
import io.github.azx8788a.pan123.api.DownloadLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import io.github.azx8788a.pan123.api.withResponse

object DownloadUrls {
    private fun query(raw: String?): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
        raw.orEmpty().split('&').filter { '=' in it }.forEach {
            val (key, value) = it.split('=', limit = 2)
            put(key, value)
        }
    }

    private fun noAutoRedirect(url: String): String {
        val parsed = URI(url)
        val args = query(parsed.rawQuery).apply { put("auto_redirect", "0") }
        return "${parsed.scheme}://${parsed.rawAuthority}${parsed.rawPath.orEmpty()}?" +
            args.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    fun decode(value: String): String {
        val normalized = java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        return try { String(Base64.getUrlDecoder().decode(normalized), Charsets.UTF_8) }
        catch (_: IllegalArgumentException) {
            try { String(Base64.getDecoder().decode(normalized), Charsets.UTF_8) }
            catch (_: IllegalArgumentException) { "" }
        }
    }

    fun rewrite(url: String): String {
        val parsed = URI(url)
        if (parsed.host.orEmpty().startsWith("web-pro")) {
            val args = query(parsed.rawQuery)
            val inner = decode(args["params"].orEmpty())
            if (inner.isBlank()) return url
            args["params"] = Base64.getUrlEncoder().encodeToString(noAutoRedirect(inner).toByteArray())
            return "${parsed.scheme}://${parsed.rawAuthority}${parsed.rawPath.orEmpty()}?" +
                args.entries.joinToString("&") { "${it.key}=${it.value}" }
        }
        val encoded = Base64.getUrlEncoder().encodeToString(noAutoRedirect(url).toByteArray())
        return "https://web-pro2.123952.com/download-v2/?params=$encoded&is_s3=0"
    }

    fun decodeParams(url: String): String = decode(query(URI(url).rawQuery)["params"].orEmpty())
}

class DownloadUrlResolver(private val transfer: TransferHttp) {
    suspend fun resolve(link: DownloadLink): String {
        if (link.direct) return link.url
        val rewritten = DownloadUrls.rewrite(link.url)
        val request = Request.Builder().url(rewritten).header("Accept-Encoding", "identity").build()
        val resolved = transfer.client.withResponse(request) { response ->
            response.header("Location")?.takeIf { response.code in 300..399 }?.let {
                request.url.resolve(it)?.toString()
            } ?: Regex("href=[\"'](https?://[^\"']+)[\"']")
                .find(response.peekBody(16 * 1024).string())?.groupValues?.get(1)?.replace("&amp;", "&")
        }
        if (!resolved.isNullOrBlank() && resolved.toHttpUrlOrNull() != null) return resolved
        return DownloadUrls.decodeParams(rewritten).takeIf { it.toHttpUrlOrNull() != null } ?: rewritten
    }
}
