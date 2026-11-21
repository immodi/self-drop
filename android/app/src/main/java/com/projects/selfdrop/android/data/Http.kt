package com.projects.selfdrop.android.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    /** Shared client for API calls, SSE and uploads. SSE uses its own read timeout. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Per-write timeout, not a total upload deadline: a stalled socket
            // should fail, but a large file on a slow LAN may take many minutes.
            .writeTimeout(60, TimeUnit.SECONDS)
            // Uploads are not idempotent; do not let OkHttp silently re-send them.
            .retryOnConnectionFailure(false)
            .build()
    }
}
