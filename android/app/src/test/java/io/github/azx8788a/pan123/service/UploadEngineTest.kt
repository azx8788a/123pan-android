package io.github.azx8788a.pan123.service

import io.github.azx8788a.pan123.api.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UploadEngineTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var engine: UploadEngine
    private val uploaded = ConcurrentHashMap.newKeySet<Int>()
    private val requested = ConcurrentHashMap.newKeySet<Int>()
    private val requests = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())
    @Volatile private var reuse = false
    @Volatile private var rejectedPart = false

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                val payload = when {
                    path == "/b/api/file/upload_request" -> if (reuse) """{"Reuse":true,"FileId":123}""" else
                        """{"Bucket":"b","Key":"k","UploadId":"u","StorageNode":"n","FileId":7}"""
                    path == "/b/api/file/s3_list_upload_parts" -> """{"Parts":[${uploaded.sorted().joinToString(",") { "{\"PartNumber\":$it}" }}]}"""
                    path == "/b/api/file/s3_repare_upload_parts_batch" -> {
                        val number = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject.number("partNumberStart").toInt()
                        requested.add(number)
                        """{"presignedUrls":{"$number":"${server.url("/put/$number")}"}}"""
                    }
                    path.startsWith("/put/") -> {
                        if (rejectedPart) return MockResponse().setResponseCode(403).setBody("part rejected")
                        uploaded.add(path.substringAfterLast('/').toInt())
                        return MockResponse().setResponseCode(200)
                    }
                    path == "/b/api/file/s3_complete_multipart_upload" -> "{}"
                    path == "/b/api/file/upload_complete/v2" -> """{"file_info":{"fileId":777}}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody("""{"code":0,"data":$payload}""")
            }
        }
        server.start()
        val session = AccountSession("test", "fake-api-token", "test-device")
        val api = PanApi({ session }, { "test-device" }, server.url("/"), null)
        engine = UploadEngine(api, TransferHttp())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun uploadsSmallFileAndConfirmsActualCloudRecord() = runBlocking {
        val file = temp.newFile().apply { writeText("hello") }
        val states = mutableListOf<UploadSession>()
        val id = engine.run(file, 0, null, saveState = { states.add(it) }, onProgress = {})
        assertEquals(777L, id)
        assertEquals(setOf(1), uploaded)
        assertEquals("done", states.last().phase)
        assertEquals(777L, states.last().fileId)
        val put = requests.single { it.path!!.startsWith("/put/") }
        assertNull(put.getHeader("Authorization"))
        assertEquals("hello", put.body.readUtf8())
        val final = requests.single { it.path == "/b/api/file/upload_complete/v2" }
        assertTrue(final.body.readUtf8().contains("\"isMultipart\":true"))
    }

    @Test fun resumesNoncontiguousPartsWithoutReuploadingCompletedPart() = runBlocking {
        val file = temp.newFile().apply { java.io.RandomAccessFile(this, "rw").use { it.setLength(UploadEngine.PART_SIZE * 2 + 1) } }
        uploaded.add(2)
        val resume = UploadSession("b", "k", "u", "n", 7, file.length(), file.lastModified(), "hash")
        assertEquals(777L, engine.run(file, 0, resume, threads = 2, saveState = {}, onProgress = {}))
        assertEquals(setOf(1, 3), requested)
        assertEquals(setOf(1, 2, 3), uploaded)
    }

    @Test fun neverFinalizesRejectedPut() = runBlocking {
        rejectedPart = true
        val file = temp.newFile().apply { writeText("hello") }
        try { engine.run(file, 0, null, saveState = {}, onProgress = {}); fail("Expected rejected PUT") }
        catch (error: TransferHttpException) { assertEquals(403, error.status) }
        assertFalse(requests.any { it.path == "/b/api/file/upload_complete/v2" })
        assertFalse(requests.any { it.path == "/b/api/file/s3_complete_multipart_upload" })
    }

    @Test fun handlesReuseWithoutSendingFileData() = runBlocking {
        reuse = true
        val file = temp.newFile().apply { writeText("hello") }
        assertEquals(123L, engine.run(file, 0, null, saveState = {}, onProgress = {}))
        assertTrue(requested.isEmpty())
        assertTrue(requests.none { it.path!!.startsWith("/put/") })
    }

    @Test fun uploadsEmptyFileAsOneZeroLengthPart() = runBlocking {
        val file = temp.newFile()
        assertEquals(777L, engine.run(file, 0, null, saveState = {}, onProgress = {}))
        assertEquals(setOf(1), uploaded)
        assertEquals(0L, requests.single { it.path!!.startsWith("/put/") }.bodySize)
    }
}
