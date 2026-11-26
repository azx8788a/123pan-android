/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.tasks

import io.github.azx8788a.pan123.api.ApiException
import io.github.azx8788a.pan123.api.SessionExpiredException
import java.net.UnknownHostException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object TransferMessages {
    const val MAX_AUTO_RETRIES = 4
    private val retryPrefix = Regex("""^等待重试（第 (\d+)/$MAX_AUTO_RETRIES 次）；""")
    private val urls = Regex("""https?://[^\s"'<>；，（）]+""", RegexOption.IGNORE_CASE)
    private val quotedDnsHost = Regex("""Unable to resolve host ["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val hostName = Regex("""[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?""")

    fun errorText(error: Throwable): String {
        val dns = generateSequence(error) { it.cause }.take(16).filterIsInstance<UnknownHostException>().firstOrNull()
        val text = when {
            dns != null -> {
                val host = dnsHost(dns.message.orEmpty())
                "域名解析失败${host?.let { "（$it）" }.orEmpty()}，请检查网络或 DNS 设置"
            }
            error is SessionExpiredException -> "登录已过期，请重新登录后继续"
            error is ApiException -> error.message ?: "接口请求失败"
            else -> error.message ?: "传输失败"
        }
        return displayMessage(text).take(160)
    }

    fun retryMessage(text: String, runAttemptCount: Int): String {
        val attempt = (runAttemptCount + 1).coerceIn(1, MAX_AUTO_RETRIES)
        return "等待重试（第 $attempt/$MAX_AUTO_RETRIES 次）；$text"
    }

    fun queuedStatus(message: String): String {
        // Only inspect the worker's stable prefix, with a fallback for older persisted messages.
        return retryPrefix.find(message)?.groupValues?.get(1)?.let { "等待重试 $it/$MAX_AUTO_RETRIES" }
            ?: if (message.endsWith("；将自动重试")) "等待重试" else "排队中"
    }

    fun displayMessage(message: String): String = urls.replace(message) {
        it.value.toHttpUrlOrNull()?.host ?: "[链接已隐藏]"
    }

    private fun dnsHost(message: String): String? {
        val candidate = quotedDnsHost.find(message)?.groupValues?.get(1) ?: message
        val url = urls.find(candidate)?.value
        if (url != null) return url.toHttpUrlOrNull()?.host
        val host = candidate.substringBefore(':').substringBefore('/').substringBefore('?').substringBefore('#').trim()
        return host.takeIf { hostName.matches(it) }?.let { "https://$it".toHttpUrlOrNull()?.host }
    }
}
