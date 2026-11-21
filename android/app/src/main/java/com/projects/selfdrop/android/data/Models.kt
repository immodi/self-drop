package com.projects.selfdrop.android.data

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val seq: Long = 0,
    val id: String,
    val name: String,
    val createdAt: Long = 0,
)

@Serializable
data class Item(
    val seq: Long = 0,
    val id: String,
    val chatId: String? = null,
    val kind: String = "file",
    val text: String? = null,
    val name: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val createdAt: Long = 0,
) {
    val isText: Boolean get() = kind == "text"

    val isImage: Boolean
        get() = kind == "file" &&
            mime?.startsWith("image/") == true &&
            mime != "image/svg+xml"

    val isVideo: Boolean
        get() = kind == "file" && mime?.startsWith("video/") == true

    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class ItemEvent(
    val type: String,
    val id: String? = null,
    val chatId: String? = null,
    val seq: Long? = null,
    val item: Item? = null,
)

@Serializable
data class ServerIdDto(val id: String)

@Serializable
data class WorkerRecord(val url: String, val id: String)

@Serializable
data class CreateChatRequest(val name: String)

@Serializable
data class RenameChatRequest(val id: String, val name: String)

@Serializable
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: Map<String, String>? = null,
) {
    fun message(): String {
        val fieldErrors = errors?.entries?.joinToString("; ") { (field, error) -> "$field: $error" }
        return listOfNotNull(detail, fieldErrors).firstOrNull { it.isNotBlank() }
            ?: title
            ?: "Request failed"
    }
}

/** A verified server endpoint. [serverId] is an identity check, not a secret. */
data class Session(val baseUrl: String, val serverId: String)

/** A file selected from a [android.content.ContentResolver] and ready to stream. */
data class UploadFile(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val size: Long?,
)

/** Payload received from an Android share sheet. */
sealed interface SharePayload {
    data class Text(val text: String) : SharePayload
    data class Files(val uris: List<Uri>) : SharePayload
}
