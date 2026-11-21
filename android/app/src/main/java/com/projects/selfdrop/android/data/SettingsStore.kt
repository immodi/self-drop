package com.projects.selfdrop.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.selfdropDataStore: DataStore<Preferences> by preferencesDataStore(name = "selfdrop")

data class StoredSettings(
    val baseUrl: String? = null,
    val serverId: String? = null,
    val workerUrl: String = SettingsStore.DEFAULT_WORKER_URL,
    val selectedChatId: String? = null,
) {
    val session: Session?
        get() {
            val url = baseUrl ?: return null
            val id = serverId ?: return null
            return Session(url, id)
        }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val serverId = stringPreferencesKey("server_id")
        val workerUrl = stringPreferencesKey("worker_url")
        val selectedChatId = stringPreferencesKey("selected_chat_id")
    }

    val settings: Flow<StoredSettings> = context.selfdropDataStore.data.map { prefs ->
        StoredSettings(
            baseUrl = prefs[Keys.baseUrl],
            serverId = prefs[Keys.serverId],
            workerUrl = prefs[Keys.workerUrl] ?: DEFAULT_WORKER_URL,
            selectedChatId = prefs[Keys.selectedChatId],
        )
    }

    suspend fun saveSession(baseUrl: String, serverId: String) {
        context.selfdropDataStore.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl
            prefs[Keys.serverId] = serverId
        }
    }

    suspend fun saveWorkerUrl(url: String) {
        context.selfdropDataStore.edit { prefs -> prefs[Keys.workerUrl] = url }
    }

    suspend fun saveSelectedChat(chatId: String?) {
        context.selfdropDataStore.edit { prefs ->
            if (chatId == null) prefs.remove(Keys.selectedChatId) else prefs[Keys.selectedChatId] = chatId
        }
    }

    suspend fun clearServer() {
        context.selfdropDataStore.edit { prefs ->
            prefs.remove(Keys.baseUrl)
            prefs.remove(Keys.serverId)
            prefs.remove(Keys.selectedChatId)
        }
    }

    companion object {
        /**
         * Public rendezvous record for the reference deployment. The server has no
         * secret here, and users can change this under Advanced settings.
         */
        const val DEFAULT_WORKER_URL = "https://selfdrop-rendezvous.ay727468a.workers.dev/r"
    }
}
