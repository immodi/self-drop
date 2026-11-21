package com.projects.selfdrop.android.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val problem: ProblemDetail?) :
    IOException(problem?.message() ?: "HTTP $status")

class SelfdropApi(
    private val client: OkHttpClient,
    private val resolver: ContentResolver,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val textMedia = "text/plain; charset=utf-8".toMediaType()

    // ---- Discovery -------------------------------------------------------

    suspend fun ping(baseUrl: String, timeoutMillis: Long = 2000): String =
        withContext(Dispatchers.IO) {
            val quick = client.newBuilder()
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder().url("$baseUrl/ping".toHttpUrl()).build()
            quick.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw error(response.code, body)
                json.decodeFromString<ServerIdDto>(body).id
            }
        }

    suspend fun workerRecord(workerUrl: String, timeoutMillis: Long = 6000): WorkerRecord =
        withContext(Dispatchers.IO) {
            val quick = client.newBuilder()
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder().url(workerRecordUrl(workerUrl).toHttpUrl()).build()
            quick.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw error(response.code, body)
                json.decodeFromString<WorkerRecord>(body)
            }
        }

    // ---- Chats -----------------------------------------------------------

    suspend fun chats(session: Session): List<Chat> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/chat".toHttpUrl()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, body)
            json.decodeFromString<List<Chat>>(body)
        }
    }

    suspend fun createChat(session: Session, name: String): Chat = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CreateChatRequest(name))
        val request = Request.Builder()
            .url("${session.baseUrl}/chat".toHttpUrl())
            .post(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, text)
            json.decodeFromString<Chat>(text)
        }
    }

    suspend fun renameChat(session: Session, id: String, name: String) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RenameChatRequest(id, name))
        val request = Request.Builder()
            .url("${session.baseUrl}/chat".toHttpUrl())
            .put(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, text)
        }
    }

    suspend fun deleteChat(session: Session, id: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${session.baseUrl}/chat/$id".toHttpUrl())
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, text)
        }
    }

    // ---- Items -----------------------------------------------------------

    suspend fun items(session: Session, chatId: String): List<Item> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/file".toHttpUrl().newBuilder()
            .addQueryParameter("chatId", chatId)
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, body)
            json.decodeFromString<List<Item>>(body)
        }
    }

    suspend fun item(session: Session, id: String): Item = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${session.baseUrl}/file/$id".toHttpUrl()).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, body)
            json.decodeFromString<Item>(body)
        }
    }

    suspend fun deleteItem(session: Session, id: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${session.baseUrl}/file/$id".toHttpUrl())
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, text)
        }
    }

    suspend fun uploadText(session: Session, chatId: String, text: String): Item? =
        withContext(Dispatchers.IO) {
            val url = "${session.baseUrl}/upload/text".toHttpUrl().newBuilder()
                .addQueryParameter("chatId", chatId)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(text.toRequestBody(textMedia))
                .build()
            val location = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw error(response.code, body)
                response.header("Location")
            }
            val id = location?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            runCatching { item(session, id) }.getOrNull()
        }

    suspend fun uploadFiles(
        session: Session,
        chatId: String,
        files: List<UploadFile>,
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> },
    ): List<Item> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext emptyList()

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                files.forEach { file ->
                    addFormDataPart("files", file.name, UriRequestBody(resolver, file))
                }
            }
            .build()

        val url = "${session.baseUrl}/upload/file".toHttpUrl().newBuilder()
            .addQueryParameter("chatId", chatId)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(ProgressRequestBody(multipart, onProgress))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, body)
            json.decodeFromString<List<Item>>(body)
        }
    }

    // ---- Download --------------------------------------------------------

    suspend fun download(
        session: Session,
        item: Item,
        directory: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val name = sanitizeFileName(item.displayName).ifBlank { item.id }
        val target = File(directory, name)
        val partial = File(directory, "$name.part")

        var existing = if (partial.exists()) partial.length() else 0L
        if (existing > 0 && target.exists()) {
            // A previous attempt completed but the rename was interrupted.
            if (target.length() > 0) return@withContext target
            target.delete()
        }

        val requestBuilder = Request.Builder()
            .url("${session.baseUrl}/download/${item.id}".toHttpUrl())
            .get()
        if (existing > 0) requestBuilder.header("Range", "bytes=$existing-")

        client.newCall(requestBuilder.build()).execute().use { response ->
            when (response.code) {
                416 -> {
                    val total = parseTotal(response.header("Content-Range"))
                    if (total != null && existing == total && existing > 0) {
                        finishPartial(partial, target)
                        return@withContext target
                    }
                    throw ApiException(416, null)
                }

                200 -> {
                    existing = 0
                    partial.delete()
                }

                206 -> Unit

                else -> {
                    if (!response.isSuccessful) throw error(response.code, response.body?.string())
                }
            }

            val total = if (response.code == 206) {
                parseTotal(response.header("Content-Range"))
            } else {
                response.header("Content-Length")?.toLongOrNull()
            }

            onProgress(existing, total)
            var downloaded = existing
            response.body?.byteStream()?.use { input ->
                FileOutputStream(partial, response.code == 206).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReportNanos = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.nanoTime()
                        if (now - lastReportNanos > 120_000_000L || downloaded == total) {
                            lastReportNanos = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }

            if (total != null && partial.length() != total) {
                throw IOException("Download incomplete: ${partial.length()} of $total bytes")
            }
            finishPartial(partial, target)
            target
        }
    }

    fun previewUrl(session: Session, item: Item): String = "${session.baseUrl}/preview/${item.id}"

    private fun finishPartial(partial: File, target: File) {
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    private fun parseTotal(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()

    private fun error(code: Int, body: String?): ApiException {
        val problem = runCatching {
            json.decodeFromString<ProblemDetail>(body ?: "")
        }.getOrNull()
        return ApiException(code, problem)
    }

    companion object {
        fun workerRecordUrl(raw: String): String {
            val value = raw.trim().trimEnd('/')
            return if (value.endsWith("/r")) value else "$value/r"
        }

        fun sanitizeFileName(name: String): String {
            val cleaned = name
                .map { character ->
                    if (character.isISOControl() || character in "\\/:*?\"<>|") '_' else character
                }
                .joinToString("")
                .trim()
                .trim('.')
                .take(180)
            return cleaned.ifBlank { "file" }
        }

        fun uploadFileFor(resolver: ContentResolver, uri: Uri): UploadFile {
            var name: String? = null
            var size: Long? = null
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
            }
            return UploadFile(
                uri = uri,
                name = name?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?: "file",
                mime = resolver.getType(uri),
                size = size,
            )
        }
    }
}

private class UriRequestBody(
    private val resolver: ContentResolver,
    private val file: UploadFile,
) : RequestBody() {
    override fun contentType(): MediaType =
        file.mime?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

    override fun contentLength(): Long = file.size ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(file.uri)
            ?: throw IOException("Cannot open ${file.name}")
        input.use {
            it.source().use { source -> sink.writeAll(source) }
        }
    }
}

/** Reports bytes written for a whole multipart body. */
private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (sent: Long, total: Long?) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().takeIf { it > 0 }
        var sent = 0L
        var lastReportNanos = 0L
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                sent += byteCount
                val now = System.nanoTime()
                if (now - lastReportNanos > 120_000_000L || sent == total) {
                    lastReportNanos = now
                    onProgress(sent, total)
                }
            }
        }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
