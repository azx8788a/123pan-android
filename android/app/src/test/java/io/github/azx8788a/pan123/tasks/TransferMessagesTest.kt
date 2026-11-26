package io.github.azx8788a.pan123.tasks

import io.github.azx8788a.pan123.api.ApiException
import io.github.azx8788a.pan123.api.SessionExpiredException
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class TransferMessagesTest {
    @Test fun showsUpcomingRetryNumberWithoutChangingTheFourRetryLimit() {
        assertEquals(4, TransferMessages.MAX_AUTO_RETRIES)
        for (runAttemptCount in 0 until TransferMessages.MAX_AUTO_RETRIES) {
            val label = "等待重试 ${runAttemptCount + 1}/4"
            val message = TransferMessages.retryMessage("网络暂时不可用", runAttemptCount)
            assertEquals("等待重试（第 ${runAttemptCount + 1}/4 次）；网络暂时不可用", message)
            assertEquals(label, TransferMessages.queuedStatus(message))
        }
    }

    @Test fun distinguishesOrdinaryQueuedTasksAndLegacyRetryMessages() {
        assertEquals("排队中", TransferMessages.queuedStatus("等待网络或传输队列"))
        assertEquals("排队中", TransferMessages.queuedStatus("任务被中断，等待继续"))
        assertEquals("排队中", TransferMessages.queuedStatus("提示中提到了将自动重试，但尚未安排"))
        assertEquals("等待重试", TransferMessages.queuedStatus("旧版网络错误；将自动重试"))
    }

    @Test fun translatesAndroidDnsFailureAndKeepsOnlyTheHost() {
        val error = UnknownHostException("Unable to resolve host \"cdn.example\": No address associated with hostname")
        assertEquals("域名解析失败（cdn.example），请检查网络或 DNS 设置", TransferMessages.errorText(error))
    }

    @Test fun translatesWrappedDnsFailureWithoutExposingTheOuterSignedUrl() {
        val error = IOException("Failed to download https://other.example/file?signature=private-value",
            UnknownHostException("cdn.example: Temporary failure in name resolution"))
        assertEquals("域名解析失败（cdn.example），请检查网络或 DNS 设置", TransferMessages.errorText(error))
    }

    @Test fun stripsPathCredentialsAndSignatureFromDnsFailure() {
        val error = UnknownHostException("Unable to resolve host \"https://user:password@cdn.example/private/file?signature=private-value\": failed")
        assertEquals("域名解析失败（cdn.example），请检查网络或 DNS 设置", TransferMessages.errorText(error))
    }

    @Test fun usesSafeChineseFallbackWhenDnsHostIsUnavailable() {
        assertEquals("域名解析失败，请检查网络或 DNS 设置", TransferMessages.errorText(UnknownHostException()))
        assertEquals("域名解析失败，请检查网络或 DNS 设置",
            TransferMessages.errorText(UnknownHostException("No address associated with hostname")))
    }

    @Test fun sanitizesUrlsBeforeTruncatingErrorText() {
        val error = IOException("请求失败 https://user:password@cdn.example/file?signature=${"x".repeat(200)}")
        assertEquals("请求失败 cdn.example", TransferMessages.errorText(error))
        assertEquals("请求失败 [链接已隐藏]", TransferMessages.errorText(IOException("请求失败 https://%invalid/file?signature=secret")))
    }

    @Test fun sanitizesPersistedMessagesInBothCardAndDetails() {
        val legacy = "请求失败 https://cdn.example/private/file?signature=secret；将自动重试"
        assertEquals("请求失败 cdn.example；将自动重试", TransferMessages.displayMessage(legacy))
        assertEquals("等待重试", TransferMessages.queuedStatus(legacy))
    }

    @Test fun preservesSessionApiAndGenericFailureMessages() {
        assertEquals("登录已过期，请重新登录后继续", TransferMessages.errorText(SessionExpiredException()))
        assertEquals("接口暂时不可用", TransferMessages.errorText(ApiException(503, "接口暂时不可用")))
        assertEquals("传输失败", TransferMessages.errorText(IOException()))
    }
}
