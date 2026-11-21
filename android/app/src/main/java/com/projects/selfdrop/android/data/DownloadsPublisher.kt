package com.projects.selfdrop.android.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.projects.selfdrop.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class SavedDownload(
    val uri: Uri,
    val displayName: String,
    val mime: String,
)

/**
 * Publishes a completed download into the phone's public Downloads folder.
 *
 * Android 10+ uses MediaStore, so no storage permission is needed. Android 9
 * and older write the file directly and need WRITE_EXTERNAL_STORAGE, requested
 * by the UI before the download starts.
 */
object DownloadsPublisher {

    suspend fun publish(context: Context, source: File, item: Item): SavedDownload =
        withContext(Dispatchers.IO) {
            val mime = item.mime?.takeIf { it.contains('/') } ?: "application/octet-stream"
            val desired = SelfdropApi.sanitizeFileName(item.displayName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishWithMediaStore(context, source, desired, mime)
            } else {
                publishToLegacyDownloads(context, source, desired, mime)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishWithMediaStore(
        context: Context,
        source: File,
        desiredName: String,
        mime: String,
    ): SavedDownload {
        val resolver = context.contentResolver
        val name = uniqueName(desiredName, existingMediaStoreNames(context))

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create the file in Downloads")

        try {
            openStream(context, uri, source)
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(uri, done, null, null) <= 0) {
                throw IOException("Could not finish the Downloads file")
            }
        } catch (exception: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw exception
        }

        return SavedDownload(uri, name, mime)
    }

    private fun openStream(context: Context, uri: Uri, source: File) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open the Downloads file")
        output.use { target ->
            source.inputStream().use { input -> input.copyTo(target) }
        }
    }

    @Suppress("DEPRECATION")
    private fun publishToLegacyDownloads(
        context: Context,
        source: File,
        desiredName: String,
        mime: String,
    ): SavedDownload {
        val directory = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the Downloads directory")
        }

        val existing = directory.list()?.toSet().orEmpty()
        val name = uniqueName(desiredName, existing)
        val target = File(directory, name)

        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.files",
            target,
        )
        return SavedDownload(uri, name, mime)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun existingMediaStoreNames(context: Context): Set<String> = runCatching {
        val names = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val column = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                cursor.getString(column)?.let(names::add)
            }
        }
        names
    }.getOrDefault(emptySet())

    internal fun uniqueName(desired: String, existing: Set<String>): String {
        if (desired !in existing) return desired

        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val extension = if (dot > 0) desired.substring(dot) else ""

        var index = 1
        while ("$base ($index)$extension" in existing) index++
        return "$base ($index)$extension"
    }
}
