package com.projects.selfdrop.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.projects.selfdrop.android.R
import com.projects.selfdrop.android.data.Item
import com.projects.selfdrop.android.data.Session
import com.projects.selfdrop.android.util.formatBytes
import com.projects.selfdrop.android.util.formatTimestamp
import com.projects.selfdrop.android.util.linkify

@Composable
fun Timeline(
    state: UiState,
    viewModel: AppViewModel,
    onImageClick: (Item) -> Unit,
    onDownload: (Item, Boolean) -> Unit,
    videosSuppressed: Boolean = false,
) {
    if (state.items.isEmpty()) {
        EmptyTimeline(state)
        return
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.items.asReversed(), key = { it.id }) { item ->
            ItemCard(
                item = item,
                session = state.session,
                previewLoaded = item.id in state.loadedPreviews,
                isDownloading = state.downloadProgress.containsKey(item.id),
                downloadProgress = state.downloadProgress[item.id],
                downloadSaving = item.id in state.downloadSaving,
                onLoadPreview = { viewModel.loadPreview(item.id) },
                onPreviewError = { video -> viewModel.previewFailed(item.id, video) },
                onImageClick = { onImageClick(item) },
                onDownload = { onDownload(item, false) },
                onOpen = { onDownload(item, true) },
                onDelete = { viewModel.deleteItem(item) },
                videosSuppressed = videosSuppressed,
            )
        }
    }

    val newest = state.items.lastOrNull()?.id
    LaunchedEffect(newest) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }
}

@Composable
private fun EmptyTimeline(state: UiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.connection is ConnectionState.Live) {
                    "Send a message, a link, or attach a file."
                } else {
                    "Waiting for a connection to this chat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemCard(
    item: Item,
    session: Session?,
    previewLoaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    downloadSaving: Boolean,
    onLoadPreview: () -> Unit,
    onPreviewError: (Boolean) -> Unit,
    onImageClick: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    videosSuppressed: Boolean,
) {
    val context = LocalContext.current
    val previewUrl = session?.let { "${it.baseUrl}/preview/${item.id}" }

    Column(Modifier.fillMaxWidth()) {
        when {
            item.isText -> TextBubble(item)

            item.isImage && previewUrl != null -> {
                if (previewLoaded) {
                    ImageBubble(
                        item = item,
                        url = previewUrl,
                        onClick = onImageClick,
                        onError = { onPreviewError(false) },
                    )
                } else {
                    MediaPlaceholder(item, video = false, onLoad = onLoadPreview)
                }
            }

            item.isVideo && previewUrl != null -> {
                if (previewLoaded) {
                    VideoBubble(
                        url = previewUrl,
                        onError = { onPreviewError(true) },
                        suppressed = videosSuppressed,
                    )
                } else {
                    MediaPlaceholder(item, video = true, onLoad = onLoadPreview)
                }
            }

            else -> FileBubble(item, onOpen)
        }

        if (isDownloading) {
            DownloadProgressBar(
                progress = downloadProgress,
                savingToDownloads = downloadSaving,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTimestamp(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))

            if (item.isText) {
                SmallAction(
                    icon = Icons.Outlined.ContentCopy,
                    description = "Copy text",
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("Selfdrop", item.text.orEmpty()),
                        )
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                )
            } else {
                SmallAction(
                    icon = Icons.Outlined.Download,
                    description = "Download",
                    onClick = onDownload,
                    enabled = !isDownloading,
                )
                SmallAction(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    description = "Open",
                    onClick = onOpen,
                    enabled = !isDownloading,
                )
            }

            SmallAction(
                icon = Icons.Outlined.DeleteOutline,
                description = "Delete",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun TextBubble(item: Item) {
    Surface(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = linkify(
                text = item.text.orEmpty(),
                linkColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The same click-to-load placeholder as the browser UI, so media is not fetched. */
@Composable
private fun MediaPlaceholder(item: Item, video: Boolean, onLoad: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clickable(onClick = onLoad),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (video) Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (video) "Preview video" else "Preview image",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                formatBytes(item.size).takeIf { it.isNotBlank() },
                item.mime?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImageBubble(
    item: Item,
    url: String,
    onClick: () -> Unit,
    onError: () -> Unit,
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = item.displayName,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit,
        loading = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            // Match the web UI: forget the preview and show the placeholder again.
            LaunchedEffect(item.id) { onError() }
            PreviewUnavailable(item.displayName)
        },
    )
}

@Composable
private fun VideoBubble(url: String, onError: () -> Unit, suppressed: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentError by rememberUpdatedState(onError)

    val exoPlayer = remember(url) {
        val exo = ExoPlayer.Builder(context).build()
        exo.setAudioAttributes(AudioAttributes.DEFAULT, true)
        exo.setHandleAudioBecomingNoisy(true)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        // Lazy loaded previews should not start talking on their own when they
        // scroll back into view; the player's own controls start playback.
        exo.playWhenReady = false
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) VideoPlayback.started(exo)
            }

            override fun onPlayerError(error: PlaybackException) {
                currentError()
            }
        })
        exo
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VideoPlayback.released(exoPlayer)
            exoPlayer.release()
        }
    }

    // An AndroidView (even a TextureView) is a child of the ComposeView and can
    // draw over Compose overlays such as the navigation drawer. Remove the view
    // while the drawer is open so the drawer is always on top.
    LaunchedEffect(suppressed) {
        if (suppressed) exoPlayer.pause()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (suppressed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }
        } else {
            AndroidView(
                factory = { viewContext ->
                    val root = FrameLayout(viewContext)
                    (LayoutInflater.from(viewContext)
                        .inflate(R.layout.view_video_player, root, false) as PlayerView).apply {
                        this.player = exoPlayer
                    }
                },
                update = { view -> view.player = exoPlayer },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FileBubble(item: Item, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    item.mime?.takeIf { it.isNotBlank() },
                    formatBytes(item.size).takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open")
            }
        }
    }
}

@Composable
private fun DownloadProgressBar(progress: Float?, savingToDownloads: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (savingToDownloads) "Saving to Downloads…" else "Downloading…",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!savingToDownloads && progress != null) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (!savingToDownloads && progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun PreviewUnavailable(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
