package io.github.azx8788a.pan123.service

import java.io.File
import io.github.azx8788a.pan123.api.PanApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var engine: DownloadEngine

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        engine = DownloadEngine(PanApi({ null }, { "test" }, server.url("/"), null), TransferHttp())
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun resumesCorrectByteRange() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
        engine.downloadTo(server.url("/file").toString(), file, 6)
        assertEquals("abcdef", file.readText())
        val request = server.takeRequest()
        assertEquals("bytes=3-", request.getHeader("Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun restartsWhenServerIgnoresRange() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setBody("abcdef"))
        engine.downloadTo(server.url("/file").toString(), file, 6)
        assertEquals("abcdef", file.readText())
    }

    @Test fun refusesWrongOffsetWithoutCorruptingPartialFile() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 2-5/6").setBody("cdef"))
        try { engine.downloadTo(server.url("/file").toString(), file, 6); fail("Expected range error") }
        catch (_: java.io.IOException) { }
        assertEquals("abc", file.readText())
    }

    @Test fun retriesUnusableRangeFromZero() = runBlocking {
        val file = temp.newFile().apply { writeText("old") }
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */6"))
        server.enqueue(MockResponse().setBody("abcdef"))
        engine.downloadTo(server.url("/file").toString(), file, 6)
        assertEquals("abcdef", file.readText())
        assertNotNull(server.takeRequest().getHeader("Range"))
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test fun streamsOnlyTheRequestedUploadSegment() {
        val file = temp.newFile().apply { writeText("0123456789") }
        val buffer = Buffer()
        val body = FilePartBody(file, 3, 4)
        body.writeTo(buffer)
        assertEquals(4L, body.contentLength())
        assertEquals("3456", buffer.readUtf8())
    }

    @Test fun hashesWithoutLoadingWholeFile() = runBlocking {
        val file = temp.newFile().apply { writeText("abc") }
        assertEquals("900150983cd24fb0d6963f7d28e17f72", fileMd5(file))
    }

    @Test fun concurrentNameReservationsDoNotOverwrite() {
        val dir = temp.newFolder()
        assertEquals("报告.pdf", reserveOutput(dir, "报告.pdf").name)
        assertEquals("报告 (1).pdf", reserveOutput(dir, "报告.pdf").name)
        assertEquals("_.._foo", safeFileName("/../foo"))
    }

    @Test fun rewritesUrlsAndPreservesEncodedSignature() {
        val original = "https://download.example/file?name=a%2Fb&signature=ab%2Bcd"
        val proxy = DownloadUrls.rewrite(original)
        assertTrue(proxy.startsWith("https://web-pro2.123952.com/download-v2/"))
        assertEquals("$original&auto_redirect=0", DownloadUrls.decodeParams(proxy))
        assertEquals(DownloadUrls.decodeParams(proxy), DownloadUrls.decodeParams(DownloadUrls.rewrite(proxy)))
    }
}
