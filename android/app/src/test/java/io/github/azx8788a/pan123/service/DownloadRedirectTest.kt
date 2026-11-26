package io.github.azx8788a.pan123.service

import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import io.github.azx8788a.pan123.api.AccountSession
import io.github.azx8788a.pan123.api.ApiException
import io.github.azx8788a.pan123.api.CloudFile
import io.github.azx8788a.pan123.api.PanApi
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadRedirectTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var engine: DownloadEngine

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val base = server.url("/")
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .followRedirects(false).retryOnConnectionFailure(false).readTimeout(5, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (hostname != base.host) throw UnknownHostException(hostname)
                    return Dns.SYSTEM.lookup(hostname)
                }
            }).build()
        val session = AccountSession("test", "test-token", "test-device")
        engine = DownloadEngine(PanApi({ session }, { session.loginUuid }, base, null, client), TransferHttp(client))
    }
    @After fun tearDown() { server.shutdown() }

    private fun jsonRedirect(target: String, key: String = "redirect_url", code: Int = 0) =
        MockResponse().setHeader("Content-Type", "application/json")
            .setBody("""{"code":$code,"data":{"$key":"$target"}}""")

    @Test fun startsFromProviderLinkWithoutProxyOrQueryRewrite() = runBlocking {
        val destination = temp.newFile()
        val staging = temp.newFile()
        val path = "/link?name=a%2Fb&signature=ab%2Bcd&part=1&part=2"
        val original = server.url(path).toString()
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"DownloadUrl":"$original"}}"""))
        server.enqueue(jsonRedirect("/file?signature=a%2Bb"))
        server.enqueue(MockResponse().setBody("abcdef"))
        assertEquals(6L, engine.run(CloudFile(1, "file.txt", 0, 6, "e80b5017098950fc58aad83c8c14978e"), destination, staging) {})
        assertEquals("abcdef", destination.readText())
        assertFalse(staging.exists())
        val apiRequest = server.takeRequest()
        assertEquals("/a/api/file/download_info", apiRequest.path)
        assertEquals("Bearer test-token", apiRequest.getHeader("Authorization"))
        val downloadRequest = server.takeRequest()
        assertEquals(path, downloadRequest.path)
        assertNull(downloadRequest.getHeader("Authorization"))
        val redirectedRequest = server.takeRequest()
        assertEquals("/file?signature=a%2Bb", redirectedRequest.path)
        assertNull(redirectedRequest.getHeader("Authorization"))
    }

    @Test fun doesNotDownloadWhenApiReportsQuotaExceeded() = runBlocking {
        for (code in listOf(5113, 5114)) {
            val destination = temp.newFile()
            val staging = temp.newFile()
            val url = server.url("/unavailable")
            server.enqueue(MockResponse().setBody("""{"code":$code,"message":"下载额度不足","data":{"DownloadUrl":"$url"}}"""))
            try {
                engine.run(CloudFile(1, "file.txt", 0, 6), destination, staging) {}
                fail("Expected API quota error")
            } catch (error: ApiException) {
                assertEquals(code, error.code)
            }
            assertEquals("/a/api/file/download_info", server.takeRequest().path)
            assertEquals(0L, destination.length())
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun preservesPartialFileWhenProviderHostCannotResolve() = runBlocking {
        val destination = temp.newFile()
        val staging = temp.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"RedirectUrl":"https://cdn.example.invalid/file"}}"""))
        try {
            engine.run(CloudFile(1, "file.txt", 0, 6), destination, staging) {}
            fail("Expected provider DNS failure")
        } catch (_: UnknownHostException) { }
        assertEquals("abc", staging.readText())
        assertEquals(0L, destination.length())
        assertEquals(1, server.requestCount)
    }

    @Test fun preservesRangeAndHeadersAcrossJsonRedirects() = runBlocking {
        listOf("redirect_url", "RedirectUrl", "redirectUrl").forEach { key ->
            val file = temp.newFile().apply { writeText("abc") }
            server.enqueue(jsonRedirect("/file", key))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
            engine.downloadTo(server.url("/link").toString(), file, 6)
            assertEquals("abcdef", file.readText())
            repeat(2) {
                val request = server.takeRequest()
                assertEquals("bytes=3-", request.getHeader("Range"))
                assertEquals("identity", request.getHeader("Accept-Encoding"))
                assertNull(request.getHeader("Authorization"))
            }
        }
    }

    @Test fun followsMixedHttpAndJsonRedirects() = runBlocking {
        val file = temp.newFile()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/bridge"))
        server.enqueue(jsonRedirect("/file"))
        server.enqueue(MockResponse().setBody("abcdef"))
        engine.downloadTo(server.url("/start").toString(), file, 6)
        assertEquals("abcdef", file.readText())
        assertEquals(listOf("/start", "/bridge", "/file"), List(3) { server.takeRequest().path })
    }

    @Test fun leavesOrdinaryJsonDownloadsIntact() = runBlocking {
        val content = """{"name":"report","data":[1,2,3]}"""
        val file = temp.newFile()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(content))
        engine.downloadTo(server.url("/file.json").toString(), file, -1)
        assertEquals(content, file.readText())
    }

    @Test fun keepsJsonOfTheExpectedSizeEvenIfItLooksLikeARedirect() = runBlocking {
        val content = """{"code":0,"data":{"redirect_url":"https://example.invalid/file"}}"""
        val length = content.toByteArray().size.toLong()
        val fixed = MockResponse().setBody(content)
        val chunked = MockResponse().setChunkedBody(content, 12)
        for (response in listOf(fixed, chunked)) {
            val file = temp.newFile()
            server.enqueue(response.setHeader("Content-Type", "application/json"))
            engine.downloadTo(server.url("/file.json").toString(), file, length)
            assertEquals(content, file.readText())
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun doesNotInterpretAnAttachedJsonFileAsControlResponse() = runBlocking {
        val content = """{"code":0,"data":{"redirect_url":"https://example.invalid/file"}}"""
        val file = temp.newFile()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setHeader("Content-Disposition", "attachment; filename=report.json").setBody(content))
        engine.downloadTo(server.url("/file.json").toString(), file, content.toByteArray().size.toLong())
        assertEquals(content, file.readText())
    }

    @Test fun refusesInvalidJsonRedirectWithoutModifyingPartialFile() = runBlocking {
        listOf("javascript:alert(1)", "https://user:password@example.com/file").forEach { target ->
            val file = temp.newFile().apply { writeText("abc") }
            server.enqueue(jsonRedirect(target))
            try { engine.downloadTo(server.url("/link").toString(), file, 6); fail("Expected invalid redirect") }
            catch (_: DownloadProtocolException) { }
            assertEquals("abc", file.readText())
        }
    }

    @Test fun refusesJsonErrorRedirectWithoutModifyingPartialFile() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        server.enqueue(jsonRedirect("/file", code = 403))
        try { engine.downloadTo(server.url("/link").toString(), file, 6); fail("Expected control response error") }
        catch (_: DownloadProtocolException) { }
        assertEquals("abc", file.readText())
        assertEquals(1, server.requestCount)
    }

    @Test fun refusesMissingHttpLocationWithoutModifyingPartialFile() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(302))
        try { engine.downloadTo(server.url("/link").toString(), file, 6); fail("Expected missing redirect") }
        catch (_: DownloadProtocolException) { }
        assertEquals("abc", file.readText())
        assertEquals(1, server.requestCount)
    }

    @Test fun boundsCombinedHttpAndJsonRedirectLoops() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        repeat(10) { index ->
            server.enqueue(if (index % 2 == 0) jsonRedirect("/loop")
                else MockResponse().setResponseCode(302).setHeader("Location", "/loop"))
        }
        try { engine.downloadTo(server.url("/loop").toString(), file, 6); fail("Expected redirect limit") }
        catch (error: DownloadProtocolException) { assertEquals("下载重定向次数过多", error.message) }
        assertEquals(10, server.requestCount)
        assertEquals("abc", file.readText())
    }
}
