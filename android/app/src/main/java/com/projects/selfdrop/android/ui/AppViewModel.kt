package com.projects.selfdrop.android.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projects.selfdrop.android.data.ApiException
import com.projects.selfdrop.android.data.Chat
import com.projects.selfdrop.android.data.Discovery
import com.projects.selfdrop.android.data.DiscoveryResult
import com.projects.selfdrop.android.data.DownloadsPublisher
import com.projects.selfdrop.android.data.Http
import com.projects.selfdrop.android.data.Item
import com.projects.selfdrop.android.data.ItemEvent
import com.projects.selfdrop.android.data.Pairing
import com.projects.selfdrop.android.data.SavedDownload
import com.projects.selfdrop.android.data.SelfdropApi
import com.projects.selfdrop.android.data.Session
import com.projects.selfdrop.android.data.SettingsStore
import com.projects.selfdrop.android.data.SharePayload
import com.projects.selfdrop.android.data.SseClient
import com.projects.selfdrop.android.data.StoredSettings
import com.projects.selfdrop.android.data.UploadWorker
import com.projects.selfdrop.android.util.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Live : ConnectionState
    data object Reconnecting : ConnectionState
    data class Offline(val reason: String) : ConnectionState
}

data class UiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val chats: List<Chat> = emptyList(),
    val selectedChatId: String? = null,
    val items: List<Item> = emptyList(),
    val connection: ConnectionState = ConnectionState.Offline("Not connected"),
    val discovering: Boolean = false,
    val discoveryMessage: String? = null,
    val uploadsInFlight: Int = 0,
    val uploadProgress: Float? = null,
    val uploadedBytes: Long = 0,
    val uploadLabel: String? = null,
    val downloadProgress: Map<String, Float?> = emptyMap(),
    val downloadSaving: Set<String> = emptySet(),
    val loadedPreviews: Set<String> = emptySet(),
    val differentServer: DiscoveryResult.DifferentServer? = null,
    val pendingShare: SharePayload? = null,
    val workerUrl: String = SettingsStore.DEFAULT_WORKER_URL,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val api = SelfdropApi(Http.client, application.contentResolver)
    private val discovery = Discovery(api, application)
    private val sse = SseClient(Http.client)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    @Volatile
    private var settings = StoredSettings()

    @Volatile
    private var streamJob: Job? = null

    @Volatile
    private var discoveryJob: Job? = null

    private val maxSeq = AtomicLong(0)

    init {
        viewModelScope.launch {
            settings = settingsStore.settings.first()
            _state.update { it.copy(workerUrl = settings.workerUrl, loading = false) }

            val session = settings.session
            if (session != null) {
                _state.update { it.copy(session = session) }
                loadSession(session)
            } else {
                runDiscovery()
            }
        }
    }

    // ---- Connection and discovery ---------------------------------------

    fun retryNow() {
        runDiscovery()
    }

    fun runDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            _state.update {
                it.copy(discovering = true, discoveryMessage = "Looking for your server…")
            }

            val result = discovery.discover(
                cachedUrl = settings.baseUrl,
                knownId = settings.serverId,
                workerUrl = _state.value.workerUrl,
            )

            when (result) {
                is DiscoveryResult.Found -> acceptSession(Session(result.baseUrl, result.serverId))
                is DiscoveryResult.DifferentServer -> _state.update {
                    it.copy(discovering = false, discoveryMessage = null, differentServer = result)
                }

                is DiscoveryResult.NotFound -> _state.update {
                    it.copy(discovering = false, discoveryMessage = result.detail)
                }
            }
        }
    }

    fun connectTo(rawAddress: String, expectedServerId: String? = null) {
        val baseUrl = Pairing.normalizeBaseUrl(rawAddress)
        if (baseUrl == null) {
            notice("Enter an address like 192.168.1.50:8080")
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(discovering = true, discoveryMessage = "Checking $baseUrl…")
            }
            try {
                val id = api.ping(baseUrl, timeoutMillis = 5000)
                if (expectedServerId != null && expectedServerId != id) {
                    _state.update { it.copy(discovering = false, discoveryMessage = null) }
                    notice("The pairing code belongs to a different server than the one that answered.")
                    return@launch
                }

                val knownId = _state.value.session?.serverId ?: settings.serverId
                if (knownId != null && knownId != id) {
                    _state.update {
                        it.copy(
                            discovering = false,
                            discoveryMessage = null,
                            differentServer = DiscoveryResult.DifferentServer(baseUrl, id, knownId),
                        )
                    }
                    return@launch
                }

                acceptSession(Session(baseUrl, id))
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Could not reach $baseUrl: ${friendly(exception)}")
                _state.update { it.copy(discovering = false, discoveryMessage = null) }
            }
        }
    }

    fun handlePairPayload(raw: String) {
        val target = Pairing.parse(raw)
        if (target == null) {
            notice("That does not look like a Selfdrop pairing code.")
            return
        }
        connectTo(target.baseUrl, target.serverId)
    }

    private suspend fun acceptSession(session: Session) {
        val session = session.copy(baseUrl = session.baseUrl.trimEnd('/'))
        settingsStore.saveSession(session.baseUrl, session.serverId)
        settings = settings.copy(baseUrl = session.baseUrl, serverId = session.serverId)

        _state.update {
            it.copy(
                session = session,
                discovering = false,
                discoveryMessage = null,
                differentServer = null,
                connection = ConnectionState.Connecting,
                loadedPreviews = emptySet(),
            )
        }

        try {
            loadChatsAndTimeline(session)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            _state.update { it.copy(connection = ConnectionState.Offline(friendly(exception))) }
        }

        startStream(session)
    }

    private suspend fun loadSession(session: Session) {
        val session = session.copy(baseUrl = session.baseUrl.trimEnd('/'))
        _state.update { it.copy(connection = ConnectionState.Connecting) }

        // Verify the server identity before reading or writing to a cached address.
        val detectedId = runCatching { api.ping(session.baseUrl, timeoutMillis = 2500) }.getOrNull()
        if (detectedId != null && detectedId != session.serverId) {
            _state.update {
                it.copy(
                    session = null,
                    connection = ConnectionState.Offline("A different server answered"),
                    discoveryMessage = "The saved server now reports a different id. " +
                        "Scan its pairing code again or forget it.",
                    differentServer = DiscoveryResult.DifferentServer(
                        baseUrl = session.baseUrl,
                        serverId = detectedId,
                        previousId = session.serverId,
                    ),
                )
            }
            return
        }

        try {
            loadChatsAndTimeline(session)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            _state.update { it.copy(connection = ConnectionState.Offline(friendly(exception))) }
        }
        startStream(session)
    }

    private fun startStream(session: Session) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var attempt = 0

            while (isActive) {
                val current = _state.value.session
                if (current != session) break

                _state.update {
                    it.copy(
                        connection = if (attempt == 0) {
                            ConnectionState.Connecting
                        } else {
                            ConnectionState.Reconnecting
                        },
                    )
                }

                if (attempt > 0) {
                    // The server may have been reset while we were disconnected.
                    val answeredId = runCatching {
                        api.ping(session.baseUrl, timeoutMillis = 1500)
                    }.getOrNull()
                    if (answeredId != null && answeredId != session.serverId) {
                        _state.update {
                            it.copy(
                                connection = ConnectionState.Offline("Server identity changed"),
                                differentServer = DiscoveryResult.DifferentServer(
                                    baseUrl = session.baseUrl,
                                    serverId = answeredId,
                                    previousId = session.serverId,
                                ),
                            )
                        }
                        return@launch
                    }
                }

                try {
                    sse.listen(
                        baseUrl = session.baseUrl,
                        since = maxSeq.get().takeIf { it > 0 },
                        onOpen = {
                            attempt = 0
                            _state.update { it.copy(connection = ConnectionState.Live) }
                            // The initial GET /chat can fail transiently while the
                            // stream is already healthy; recover without a reconnect.
                            if (_state.value.chats.isEmpty()) {
                                viewModelScope.launch { runCatching { reconcile(session) } }
                            }
                        },
                        onEvent = { event -> handleEvent(event, session) },
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // The loop below backs off and retries.
                }

                if (!isActive) break

                attempt++
                _state.update {
                    it.copy(
                        connection = if (attempt >= 3) {
                            ConnectionState.Offline("Can't reach ${session.baseUrl}")
                        } else {
                            ConnectionState.Reconnecting
                        },
                    )
                }

                // A changed LAN address is not fixed by retrying the old URL.
                // After a few failures, run discovery again (saved address,
                // Worker, then LAN scan) and switch if a different address or a
                // reset server answers.
                if (attempt >= 3) {
                    when (
                        val found = discovery.discover(
                            cachedUrl = session.baseUrl,
                            knownId = session.serverId,
                            workerUrl = _state.value.workerUrl,
                        )
                    ) {
                        is DiscoveryResult.Found -> {
                            if (found.baseUrl != session.baseUrl || found.serverId != session.serverId) {
                                acceptSession(Session(found.baseUrl, found.serverId))
                                return@launch
                            }
                        }

                        is DiscoveryResult.DifferentServer -> {
                            _state.update {
                                it.copy(
                                    connection = ConnectionState.Offline("Server identity changed"),
                                    differentServer = found,
                                )
                            }
                            return@launch
                        }

                        is DiscoveryResult.NotFound -> Unit
                    }
                }

                // Deletes and updates are not replayed by the server.
                runCatching { reconcile(session) }

                delay(minOf(15_000L, 1_000L * (1 shl attempt.coerceAtMost(4))))
            }
        }
    }

    private suspend fun reconcile(session: Session) {
        if (_state.value.session != session) return

        runCatching {
            val chats = loadChats(session)
            _state.update { it.copy(chats = chats) }

            if (chats.isEmpty()) {
                _state.update { it.copy(selectedChatId = null, items = emptyList()) }
                return@runCatching
            }

            val selected = _state.value.selectedChatId
            if (chats.none { it.id == selected }) {
                val next = chats.first().id
                settingsStore.saveSelectedChat(next)
                settings = settings.copy(selectedChatId = next)
                _state.update { it.copy(selectedChatId = next, items = emptyList()) }
            }
            _state.value.selectedChatId?.let { loadItems(session, it) }
        }
    }

    private fun handleEvent(event: ItemEvent, session: Session) {
        if (_state.value.session != session) return
        val seq = event.seq
        if (seq != null) maxSeq.updateAndGet { current -> maxOf(current, seq) }

        val chatId = event.chatId ?: event.item?.chatId ?: return
        if (chatId != _state.value.selectedChatId) return

        when (event.type) {
            "item-added", "item-updated" -> {
                val item = event.item ?: return
                upsert(item.copy(chatId = item.chatId ?: chatId))
            }

            "item-deleted" -> {
                val id = event.id ?: return
                _state.update { state ->
                    state.copy(
                        items = state.items.filterNot { it.id == id },
                        loadedPreviews = state.loadedPreviews - id,
                    )
                }
            }
        }
    }

    // ---- Chats -----------------------------------------------------------

    private suspend fun loadChats(session: Session): List<Chat> {
        var chats = api.chats(session)
        if (chats.isEmpty()) {
            // A fresh API database has no chats; the browser UI makes one too.
            runCatching { api.createChat(session, "General") }
            chats = api.chats(session)
        }
        return chats
    }

    private suspend fun loadChatsAndTimeline(session: Session) {
        val chats = loadChats(session)
        _state.update { it.copy(chats = chats) }

        val desired = settings.selectedChatId
            ?.takeIf { saved -> chats.any { it.id == saved } }
            ?: chats.firstOrNull()?.id

        if (desired != null) {
            settingsStore.saveSelectedChat(desired)
            settings = settings.copy(selectedChatId = desired)
            _state.update { it.copy(selectedChatId = desired) }
            loadItems(session, desired)
        }

        flushPendingShare()
    }

    private suspend fun loadItems(session: Session, chatId: String) {
        val loaded = api.items(session, chatId)
            .sortedWith(compareBy({ it.createdAt }, { it.seq }))

        if (_state.value.selectedChatId != chatId || _state.value.session != session) return

        val highest = loaded.maxOfOrNull { it.seq } ?: 0L
        maxSeq.updateAndGet { current -> maxOf(current, highest) }
        _state.update { it.copy(items = loaded) }
    }

    fun selectChat(chatId: String) {
        val session = _state.value.session ?: return
        if (_state.value.selectedChatId == chatId) return

        _state.update { it.copy(selectedChatId = chatId, items = emptyList()) }
        viewModelScope.launch {
            settingsStore.saveSelectedChat(chatId)
            settings = settings.copy(selectedChatId = chatId)
            try {
                loadItems(session, chatId)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Could not load chat: ${friendly(exception)}")
            }
            flushPendingShare()
        }
    }

    fun createChat(name: String) {
        val session = _state.value.session ?: return
        val cleaned = name.trim().ifBlank { "New chat" }.take(120)
        viewModelScope.launch {
            try {
                val created = api.createChat(session, cleaned)
                _state.update { it.copy(chats = it.chats + created) }
                selectChat(created.id)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Could not create chat: ${friendly(exception)}")
            }
        }
    }

    fun renameChat(chatId: String, name: String) {
        val session = _state.value.session ?: return
        val cleaned = name.trim().ifBlank { "Chat" }.take(120)
        viewModelScope.launch {
            try {
                api.renameChat(session, chatId, cleaned)
                _state.update { state ->
                    state.copy(chats = state.chats.map { if (it.id == chatId) it.copy(name = cleaned) else it })
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Could not rename chat: ${friendly(exception)}")
            }
        }
    }

    fun deleteChat(chatId: String) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            try {
                api.deleteChat(session, chatId)
                _state.update { state -> state.copy(chats = state.chats.filterNot { it.id == chatId }) }

                if (_state.value.selectedChatId == chatId) {
                    val next = _state.value.chats.firstOrNull()
                    if (next == null) {
                        // Never leave the app without a place to send things.
                        val created = api.createChat(session, "General")
                        _state.update { state -> state.copy(chats = listOf(created)) }
                        selectChat(created.id)
                    } else {
                        _state.update { it.copy(selectedChatId = null, items = emptyList()) }
                        selectChat(next.id)
                    }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Could not delete chat: ${friendly(exception)}")
            }
        }
    }

    // ---- Items and uploads ----------------------------------------------

    fun sendText(text: String) {
        if (text.isBlank()) return
        val session = _state.value.session ?: return
        val chatId = _state.value.selectedChatId ?: return

        viewModelScope.launch {
            _state.update { it.copy(uploadsInFlight = it.uploadsInFlight + 1, uploadLabel = "Message") }
            try {
                val created = api.uploadText(session, chatId, text)
                if (created != null) {
                    upsert(created)
                } else {
                    loadItems(session, chatId)
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Send failed: ${friendly(exception)}")
            } finally {
                _state.update {
                    it.copy(
                        uploadsInFlight = (it.uploadsInFlight - 1).coerceAtLeast(0),
                        uploadProgress = null,
                        uploadedBytes = 0,
                        uploadLabel = null,
                    )
                }
            }
        }
    }

    fun uploadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val session = _state.value.session ?: return
        val chatId = _state.value.selectedChatId ?: return

        viewModelScope.launch {
            _state.update { it.copy(uploadsInFlight = it.uploadsInFlight + 1) }
            try {
                val files = uris.map {
                    SelfdropApi.uploadFileFor(getApplication<Application>().contentResolver, it)
                }
                val knownBytes = files.mapNotNull { it.size }
                val sizeLabel = if (knownBytes.isNotEmpty()) formatBytes(knownBytes.sum()) else ""
                val label = when {
                    files.size == 1 && sizeLabel.isNotEmpty() -> "${files.first().name} · $sizeLabel"
                    files.size == 1 -> files.first().name
                    sizeLabel.isNotEmpty() -> "${files.size} files · $sizeLabel"
                    else -> "${files.size} files"
                }
                _state.update { it.copy(uploadLabel = label) }
                val created = api.uploadFiles(session, chatId, files) { sent, total ->
                    _state.update {
                        it.copy(
                            uploadedBytes = sent,
                            uploadProgress = if (total != null && total > 0) {
                                (sent.toFloat() / total).coerceIn(0f, 1f)
                            } else {
                                null
                            },
                        )
                    }
                }
                created.forEach(::upsert)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Upload failed: ${friendly(exception)}")
            } finally {
                _state.update {
                    it.copy(
                        uploadsInFlight = (it.uploadsInFlight - 1).coerceAtLeast(0),
                        uploadProgress = null,
                        uploadedBytes = 0,
                        uploadLabel = null,
                    )
                }
            }
        }
    }

    fun onShare(payload: SharePayload) {
        val session = _state.value.session
        if (session == null) {
            _state.update {
                it.copy(
                    pendingShare = payload,
                    discoveryMessage = "A shared item is waiting. Connect to your server to send it.",
                )
            }
            runDiscovery()
            return
        }

        val chatId = _state.value.selectedChatId
        if (chatId == null) {
            _state.update { it.copy(pendingShare = payload) }
            return
        }
        enqueueShared(session, chatId, payload)
    }

    /**
     * Share-sheet items are uploaded by WorkManager so they survive the activity
     * going away. Uploads are not idempotent, so only one path ever sends them.
     */
    private fun enqueueShared(session: Session, chatId: String, payload: SharePayload) {
        runCatching {
            UploadWorker.enqueue(
                context = getApplication(),
                session = session,
                chatId = chatId,
                payload = payload,
                workerUrl = _state.value.workerUrl,
            )
        }.onSuccess {
            notice("Shared item queued for upload")
        }.onFailure {
            notice("Could not queue the shared item: ${it.message ?: "unknown error"}")
        }
    }

    private fun flushPendingShare() {
        val payload = _state.value.pendingShare ?: return
        val session = _state.value.session ?: return
        val chatId = _state.value.selectedChatId ?: return
        _state.update { it.copy(pendingShare = null) }
        enqueueShared(session, chatId, payload)
    }

    private fun upsert(item: Item) {
        val selectedChatId = _state.value.selectedChatId
        if (item.chatId != null && item.chatId != selectedChatId) return
        val normalized = item.copy(chatId = item.chatId ?: selectedChatId)
        maxSeq.updateAndGet { current -> maxOf(current, normalized.seq) }

        _state.update { state ->
            val items = (state.items.filterNot { it.id == normalized.id } + normalized)
                .sortedWith(compareBy({ it.createdAt }, { it.seq }))
            state.copy(items = items)
        }
    }

    fun deleteItem(item: Item) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            try {
                api.deleteItem(session, item.id)
                _state.update { state ->
                    state.copy(
                        items = state.items.filterNot { it.id == item.id },
                        loadedPreviews = state.loadedPreviews - item.id,
                    )
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Delete failed: ${friendly(exception)}")
            }
        }
    }

    // ---- Media previews --------------------------------------------------

    /** Media is only requested after the user explicitly asks to preview it. */
    fun loadPreview(itemId: String) {
        _state.update { it.copy(loadedPreviews = it.loadedPreviews + itemId) }
    }

    fun previewFailed(itemId: String, video: Boolean) {
        _state.update { it.copy(loadedPreviews = it.loadedPreviews - itemId) }
        notice(if (video) "Could not load video preview" else "Could not load image preview")
    }

    // ---- Downloads -------------------------------------------------------

    fun download(item: Item, open: Boolean) {
        val session = _state.value.session ?: return
        if (_state.value.downloadProgress.containsKey(item.id)) return

        viewModelScope.launch {
            val app = getApplication<Application>()
            val cacheRoot = app.externalCacheDir ?: app.cacheDir
            val staging = File(cacheRoot, "downloads/${item.id}")

            _state.update { it.copy(downloadProgress = it.downloadProgress + (item.id to null)) }
            try {
                var lastFraction = -1f
                val file = api.download(session, item, staging) { downloaded, total ->
                    val fraction = if (total != null && total > 0) {
                        (downloaded.toFloat() / total).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    if (fraction == null || fraction - lastFraction >= 0.02f || fraction >= 1f) {
                        if (fraction != null) lastFraction = fraction
                        _state.update {
                            it.copy(downloadProgress = it.downloadProgress + (item.id to fraction))
                        }
                    }
                }

                // The bytes are complete; copy them into Downloads now.
                _state.update {
                    it.copy(
                        downloadSaving = it.downloadSaving + item.id,
                        downloadProgress = it.downloadProgress + (item.id to null),
                    )
                }
                val saved = DownloadsPublisher.publish(app, file, item)
                runCatching {
                    file.delete()
                    file.parentFile?.delete()
                }

                notice("Saved ${saved.displayName} to Downloads")
                if (open) openDownloaded(saved)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                notice("Download failed: ${friendly(exception)}")
            } finally {
                _state.update {
                    it.copy(
                        downloadProgress = it.downloadProgress - item.id,
                        downloadSaving = it.downloadSaving - item.id,
                    )
                }
            }
        }
    }

    private fun openDownloaded(saved: SavedDownload) {
        val context = getApplication<Application>()
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(saved.uri, saved.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.clipData = ClipData.newRawUri(saved.displayName, saved.uri)
            context.startActivity(intent)
        }.onFailure {
            notice("No app can open ${saved.displayName}")
        }
    }

    // ---- Settings and lifecycle -----------------------------------------

    fun setWorkerUrl(raw: String) {
        val value = raw.trim()
        if (value.isNotEmpty() && value.toHttpUrlOrNull() == null) {
            notice("Enter an http:// or https:// Worker URL, or leave it empty.")
            return
        }
        val stored = if (value.isEmpty()) "" else SelfdropApi.workerRecordUrl(value)
        viewModelScope.launch {
            settingsStore.saveWorkerUrl(stored)
            settings = settings.copy(workerUrl = stored)
            _state.update { it.copy(workerUrl = stored) }
            notice("Worker URL saved")
        }
    }

    fun forgetServer() {
        streamJob?.cancel()
        streamJob = null
        discoveryJob?.cancel()
        maxSeq.set(0)

        viewModelScope.launch {
            settingsStore.clearServer()
            settings = settings.copy(baseUrl = null, serverId = null, selectedChatId = null)
            _state.value = UiState(
                loading = false,
                workerUrl = settings.workerUrl,
                discoveryMessage = "Enter an address, scan the pairing code, or find the server again.",
            )
        }
    }

    fun switchToDifferentServer() {
        val different = _state.value.differentServer ?: return
        _state.update { it.copy(differentServer = null) }
        viewModelScope.launch { acceptSession(Session(different.baseUrl, different.serverId)) }
    }

    fun dismissDifferentServer() {
        _state.update { it.copy(differentServer = null) }
    }

    fun onForeground() {
        val session = _state.value.session ?: return
        if (streamJob?.isActive == true) return

        _state.update { it.copy(connection = ConnectionState.Connecting) }
        viewModelScope.launch {
            // Reconcile changes that happened while the stream was closed,
            // including deletes that the server does not replay.
            runCatching { reconcile(session) }
            if (_state.value.session == session) startStream(session)
        }
    }

    fun onBackground() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(connection = ConnectionState.Offline("App is in the background")) }
    }

    // ---- Helpers ---------------------------------------------------------

    private fun notice(message: String) {
        _messages.trySend(message)
    }

    private fun friendly(exception: Throwable): String = when (exception) {
        is ApiException -> exception.problem?.message() ?: "Server error (${exception.status})"
        is java.net.ConnectException -> "Connection refused"
        is java.net.SocketTimeoutException -> "The server timed out"
        is java.net.UnknownHostException -> "Server not reachable"
        is IOException -> exception.message ?: "Network error"
        else -> exception.message ?: "Something went wrong"
    }
}
