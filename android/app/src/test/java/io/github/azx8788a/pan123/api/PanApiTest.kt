package io.github.azx8788a.pan123.api

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PanApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PanApi
    private val session = AccountSession("test-user", "test-token", "test-device")

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = PanApi({ session }, { "test-device" }, server.url("/"), null)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun passwordLoginParsesSmallJsonAndOmitsOldToken() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"token":"new-token"}}"""))
        assertEquals("new-token", api.login("user", "password"))
        val request = server.takeRequest()
        assertEquals("/b/api/user/sign_in", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("android", request.getHeader("platform"))
        assertTrue(request.body.readUtf8().contains("\"passport\":\"user\""))
    }

    @Test fun listsUppercaseFieldsAndUsesOneBasedPage() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"Total":201,"InfoList":[{"FileId":7,"FileName":"测试目录","Type":1,"Size":0}]}}"""))
        val result = api.listFiles(12, page = 2)
        assertEquals(7L, result.files.single().id)
        assertTrue(result.files.single().isDirectory)
        assertTrue(result.hasMore)
        val request = server.takeRequest()
        assertEquals("2", request.requestUrl!!.queryParameter("Page"))
        assertEquals("12", request.requestUrl!!.queryParameter("parentFileId"))
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test fun rejectsExpiredSession() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":2,"message":"session_expired"}"""))
        try { api.listFiles(0); fail("Expected session expiry") } catch (_: SessionExpiredException) { }
    }

    @Test fun rejectsHtmlAndMissingStatus() = runBlocking {
        listOf("<html>error</html>", "{}").forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            try { api.listFiles(0); fail("Expected invalid response") } catch (_: ApiException) { }
        }
    }

    @Test fun parsesUpperAndLowerCasePartLists() = runBlocking {
        val upload = UploadSession("bucket", "key", "upload", "node", 1, 1, 1, "md5")
        listOf("Parts", "parts").forEach { key ->
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"$key":[{"PartNumber":1},{"PartNumber":3}]}}"""))
            assertEquals(setOf(1, 3), api.uploadedParts(upload))
        }
    }

    @Test fun finalizesSmallFilesAsMultipartUsingV2() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"file_info":{"fileId":77}}}"""))
        val result = api.finalizeUpload(UploadSession("b", "k", "u", "s", 7, 1, 1, "md5"))
        assertEquals(77, result.obj("file_info").number("fileId").toInt())
        val request = server.takeRequest()
        assertEquals("/b/api/file/upload_complete/v2", request.path)
        assertTrue(request.body.readUtf8().contains("\"isMultipart\":true"))
    }

    @Test fun sessionDebugTextDoesNotIncludeToken() {
        assertFalse(session.toString().contains("test-token"))
    }

    @Test fun loginFallsBackWhenPrimaryHostCannotConnect() = runBlocking {
        val dead = MockWebServer().apply { start() }
        val primaryUrl = dead.url("/")
        dead.shutdown()
        val client = PanApi({ null }, { "test-device" }, primaryUrl, server.url("/"))
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"token":"fallback-token"}}"""))
        assertEquals("fallback-token", client.login("user", "password"))
    }
}
