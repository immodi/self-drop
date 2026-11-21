package com.projects.selfdrop.android

import android.content.ContentResolver
import android.net.Uri
import com.projects.selfdrop.android.data.ApiException
import com.projects.selfdrop.android.data.Discovery
import com.projects.selfdrop.android.data.DiscoveryResult
import com.projects.selfdrop.android.data.Http
import com.projects.selfdrop.android.data.Item
import com.projects.selfdrop.android.data.SelfdropApi
import com.projects.selfdrop.android.data.Session
import com.projects.selfdrop.android.data.SseClient
import com.projects.selfdrop.android.data.UploadFile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class SelfdropApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SelfdropApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = SelfdropApi(
            client = Http.client,
            resolver = Mockito.mock(ContentResolver::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun session(serverId: String = "server-1"): Session =
        Session(server.url("/").toString().trimEnd('/'), serverId)

    @Test
    fun pingReadsServerId() = runBlocking {
        server.enqueue(json("""{"id":"server-1"}"""))

        assertEquals("server-1", api.ping(session().baseUrl))
    }

    @Test
    fun chatsAreDecoded() = runBlocking {
        server.enqueue(
            json("""[{"seq":1,"id":"chat-1","name":"General","createdAt":1761300000}]"""),
        )

        val chats = api.chats(session())

        assertEquals(1, chats.size)
        assertEquals("General", chats.single().name)
        assertEquals("/chat", server.takeRequest().path)
    }

    @Test
    fun textUploadFollowsLocationAndLoadsTheItem() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Location", "/file/item-1")
                .setBody("""{"text":"hello"}"""),
        )
        server.enqueue(json(itemJson("item-1", "hello")))

        val item = api.uploadText(session(), "chat-1", "hello")

        assertEquals("item-1", item?.id)
        assertEquals("/upload/text?chatId=chat-1", server.takeRequest().path)
        assertEquals("/file/item-1", server.takeRequest().path)
    }

    @Test
    fun problemDetailIsSurfaced() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(
                    """{"type":"about:blank","title":"Chat not found","status":404,"detail":"No chat"}""",
                ),
        )

        try {
            api.chats(session())
            fail("Expected ApiException")
        } catch (exception: ApiException) {
            assertEquals(404, exception.status)
            assertEquals("No chat", exception.problem?.detail)
        }
    }

    @Test
    fun sseReplaysItemEventsAndIgnoresHeartbeats() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: heartbeat
                    data:

                    event: item-added
                    id: 7
                    data: {"type":"item-added","id":"item-1","chatId":"chat-1","seq":7,"item":${itemJson("item-1", "hello")}}

                    """.trimIndent(),
                ),
        )

        val events = mutableListOf<String>()
        var opened = false
        SseClient(Http.client).listen(
            baseUrl = session().baseUrl,
            since = 3,
            onOpen = { opened = true },
            onEvent = { event -> events += event.id.orEmpty() },
        )

        assertTrue(opened)
        assertEquals(listOf("item-1"), events)
        assertEquals("/messages?since=3", server.takeRequest().path)
    }

    @Test
    fun discoveryVerifiesTheCachedServerId() = runBlocking {
        server.enqueue(json("""{"id":"server-1"}"""))
        val discovery = Discovery(api, Mockito.mock(android.content.Context::class.java))

        val found = discovery.discover(session().baseUrl, knownId = "server-1", workerUrl = "")

        assertEquals(
            DiscoveryResult.Found(session().baseUrl, "server-1", "saved address"),
            found,
        )
    }

    @Test
    fun discoveryAsksBeforeSwitchingToADifferentId() = runBlocking {
        server.enqueue(json("""{"id":"server-2"}"""))
        val discovery = Discovery(api, Mockito.mock(android.content.Context::class.java))

        val result = discovery.discover(session().baseUrl, knownId = "server-1", workerUrl = "")

        assertTrue(result is DiscoveryResult.DifferentServer)
        assertEquals("server-2", (result as DiscoveryResult.DifferentServer).serverId)
    }

    @Test
    fun fileUploadStreamsMultipartParts() = runBlocking {
        server.enqueue(json("[]"))
        val uri = Mockito.mock(Uri::class.java)
        val resolver = Mockito.mock(ContentResolver::class.java)
        Mockito.`when`(resolver.openInputStream(uri))
            .thenAnswer { ByteArrayInputStream("hello".toByteArray()) }
        Mockito.`when`(resolver.getType(uri)).thenReturn("text/plain")
        val uploadApi = SelfdropApi(Http.client, resolver)

        val created = uploadApi.uploadFiles(
            session = session(),
            chatId = "chat-1",
            files = listOf(UploadFile(uri, "a.txt", "text/plain", 5)),
        )

        assertTrue(created.isEmpty())
        val request = server.takeRequest()
        assertEquals("/upload/file?chatId=chat-1", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("filename=\"a.txt\""))
        assertTrue(body.contains("hello"))
    }

    @Test
    fun downloadResumesFromAPartialFile() = runBlocking {
        val directory = Files.createTempDirectory("selfdrop-download").toFile()
        try {
            File(directory, "a.txt.part").writeText("hello")
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 5-9/10")
                    .setHeader("Content-Length", "5")
                    .setBody("world"),
            )

            val item = Item(
                seq = 1,
                id = "item-1",
                chatId = "chat-1",
                kind = "file",
                name = "a.txt",
                mime = "text/plain",
                size = 10,
                createdAt = 1761300000,
            )
            val file = api.download(session(), item, directory)

            assertEquals("helloworld", file.readText())
            assertEquals("bytes=5-", server.takeRequest().getHeader("Range"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)

    private fun itemJson(id: String, text: String): String =
        """{"seq":7,"id":"$id","chatId":"chat-1","kind":"text","text":"$text","name":null,"mime":null,"size":${text.length},"createdAt":1761300000}"""
}
