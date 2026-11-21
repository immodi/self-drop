package com.projects.selfdrop.android.data

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads an item shared from another app. WorkManager provides the network
 * constraint and backoff; the worker re-resolves the server address before
 * uploading so a changed LAN address does not strand the queue.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return@withContext Result.failure()
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return@withContext Result.failure()
        val serverId = inputData.getString(KEY_SERVER_ID).orEmpty()
        val workerUrl = inputData.getString(KEY_WORKER_URL).orEmpty()
        val api = SelfdropApi(Http.client, applicationContext.contentResolver)

        // The LAN address may have changed since the share was queued. Resolve
        // it before uploading so retries do not keep hitting a stale address.
        var session = Session(baseUrl, serverId)
        val answeredId = runCatching { api.ping(session.baseUrl, timeoutMillis = 3000) }.getOrNull()
        if (answeredId != session.serverId) {
            when (
                val found = Discovery(api, applicationContext).discover(
                    cachedUrl = session.baseUrl,
                    knownId = session.serverId.takeIf { it.isNotBlank() },
                    workerUrl = workerUrl,
                )
            ) {
                is DiscoveryResult.Found -> session = Session(found.baseUrl, found.serverId)
                is DiscoveryResult.DifferentServer -> return@withContext Result.failure()
                is DiscoveryResult.NotFound -> return@withContext retryOrGiveUp()
            }
        }

        try {
            val text = inputData.getString(KEY_TEXT)
            if (text != null) {
                api.uploadText(session, chatId, text)
            } else {
                val uris = inputData.getStringArray(KEY_URIS).orEmpty()
                if (uris.isEmpty()) return@withContext Result.failure()
                val files = uris.map { SelfdropApi.uploadFileFor(applicationContext.contentResolver, Uri.parse(it)) }
                api.uploadFiles(session, chatId, files)
            }
            Result.success()
        } catch (exception: ApiException) {
            // A 4xx will not succeed on retry.
            if (exception.status in 400..499) Result.failure() else retryOrGiveUp()
        } catch (exception: IOException) {
            retryOrGiveUp()
        } catch (exception: Exception) {
            Result.failure()
        }
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount < 8) Result.retry() else Result.failure()

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_WORKER_URL = "worker_url"
        private const val KEY_TEXT = "text"
        private const val KEY_URIS = "uris"

        fun enqueue(
            context: Context,
            session: Session,
            chatId: String,
            payload: SharePayload,
            workerUrl: String,
        ) {
            val data: Data = when (payload) {
                is SharePayload.Text -> workDataOf(
                    KEY_BASE_URL to session.baseUrl,
                    KEY_SERVER_ID to session.serverId,
                    KEY_CHAT_ID to chatId,
                    KEY_WORKER_URL to workerUrl,
                    KEY_TEXT to payload.text,
                )

                is SharePayload.Files -> workDataOf(
                    KEY_BASE_URL to session.baseUrl,
                    KEY_SERVER_ID to session.serverId,
                    KEY_CHAT_ID to chatId,
                    KEY_WORKER_URL to workerUrl,
                    KEY_URIS to payload.uris.map { it.toString() }.toTypedArray(),
                )
            }

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag("selfdrop-upload")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
