package com.projects.selfdrop.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A small Server-Sent Events reader for `/messages`.
 *
 * The call blocks until the stream closes, fails, or the surrounding coroutine is
 * cancelled. A dedicated client has no read timeout so the 10-second server
 * heartbeats are enough to keep the connection alive.
 */
class SseClient(baseClient: OkHttpClient) {

    private val client = baseClient.newBuilder()
        // The server sends a heartbeat every 10 seconds; a longer silence means
        // the connection is dead and should be re-established.
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun listen(
        baseUrl: String,
        since: Long?,
        onOpen: () -> Unit,
        onEvent: (ItemEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/messages".toHttpUrl().newBuilder()
            .apply {
                if (since != null && since > 0) addQueryParameter("since", since.toString())
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val call = client.newCall(request)
        val job = currentCoroutineContext()[Job]
        val cancellation = job?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, null)
                }
                onOpen()

                val source = response.body?.source() ?: throw IOException("Empty SSE body")
                var eventName: String? = null
                val data = StringBuilder()

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty() && eventName != null) {
                                decode(eventName, data.toString())?.let(onEvent)
                            }
                            eventName = null
                            data.clear()
                        }

                        line.startsWith("event:") -> {
                            eventName = line.substringAfter("event:").trim()
                        }

                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substringAfter("data:").trimStart())
                        }

                        line.startsWith(":") -> Unit // comment / keep-alive

                        else -> Unit
                    }
                }

                // Dispatch a final event if the stream ended without its blank line.
                if (data.isNotEmpty() && eventName != null) {
                    decode(eventName, data.toString())?.let(onEvent)
                }
            }
        } finally {
            cancellation?.dispose()
        }
    }

    private fun decode(eventName: String, data: String): ItemEvent? {
        if (eventName == "heartbeat") return null
        return runCatching { json.decodeFromString<ItemEvent>(data) }.getOrNull()
    }
}
