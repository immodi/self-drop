package com.projects.selfdrop.android

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.projects.selfdrop.android.data.SharePayload
import com.projects.selfdrop.android.ui.AppViewModel
import com.projects.selfdrop.android.ui.SelfdropApp

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.retryNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureLocalNetworkPermission()
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            SelfdropApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }

    /**
     * Android 16 (API 36) gates local-network access behind a runtime permission.
     * Older releases allow LAN traffic with only INTERNET.
     */
    private fun ensureLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT < 36) return
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermissionLauncher.launch(permission)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> handleSend(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleSendMultiple(intent)
            Intent.ACTION_VIEW -> {
                if (intent.data?.scheme == "selfdrop") {
                    intent.dataString?.let(viewModel::handlePairPayload)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleSend(intent: Intent) {
        val type = intent.type.orEmpty()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val stream = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        when {
            stream != null -> {
                persistReadPermission(stream)
                viewModel.onShare(SharePayload.Files(listOf(stream)))
            }

            type.startsWith("text/") && !text.isNullOrBlank() -> {
                viewModel.onShare(SharePayload.Text(text))
            }

            !text.isNullOrBlank() -> {
                viewModel.onShare(SharePayload.Text(text))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleSendMultiple(intent: Intent) {
        val uris = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }.orEmpty().filterNotNull()

        if (uris.isNotEmpty()) {
            uris.forEach(::persistReadPermission)
            viewModel.onShare(SharePayload.Files(uris))
            return
        }

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            viewModel.onShare(SharePayload.Text(text))
        }
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
