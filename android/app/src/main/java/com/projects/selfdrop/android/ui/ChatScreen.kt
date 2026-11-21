package com.projects.selfdrop.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.projects.selfdrop.android.data.Chat
import com.projects.selfdrop.android.data.Item
import com.projects.selfdrop.android.data.Session
import com.projects.selfdrop.android.util.hostOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: UiState,
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var newChatOpen by rememberSaveable { mutableStateOf(false) }
    var chatToRename by remember { mutableStateOf<Chat?>(null) }
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<Item?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<Item, Boolean>?>(null) }
    val context = LocalContext.current

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingDownload
        pendingDownload = null
        if (granted && request != null) {
            viewModel.download(request.first, request.second)
        } else if (!granted) {
            Toast.makeText(
                context,
                "Storage access is needed to save files in Downloads",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val downloadWithPermission: (Item, Boolean) -> Unit = { item, open ->
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = item to open
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.download(item, open)
        }
    }

    val selectedChat = state.chats.firstOrNull { it.id == state.selectedChatId }
    val videosSuppressed = drawerState.currentValue != DrawerValue.Closed ||
        drawerState.targetValue != DrawerValue.Closed

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ChatDrawer(
                    state = state,
                    onSelect = { chatId ->
                        viewModel.selectChat(chatId)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        newChatOpen = true
                        scope.launch { drawerState.close() }
                    },
                    onRename = { chat ->
                        chatToRename = chat
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { chat ->
                        chatToDelete = chat
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Chats")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = selectedChat?.name ?: "Selfdrop",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            ConnectionLabel(state.connection)
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Server settings")
                        }
                    },
                )
            },
            bottomBar = { Composer(state, viewModel) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.connection !is ConnectionState.Live) {
                    ConnectionBanner(state.connection, viewModel)
                }
                Timeline(
                    state = state,
                    viewModel = viewModel,
                    onImageClick = { previewItem = it },
                    onDownload = downloadWithPermission,
                    videosSuppressed = videosSuppressed,
                )
            }
        }
    }

    if (newChatOpen) {
        NewChatDialog(
            onDismiss = { newChatOpen = false },
            onCreate = { name ->
                newChatOpen = false
                viewModel.createChat(name)
            },
        )
    }

    chatToRename?.let { chat ->
        RenameChatDialog(
            chat = chat,
            onDismiss = { chatToRename = null },
            onRename = { name ->
                chatToRename = null
                viewModel.renameChat(chat.id, name)
            },
        )
    }

    chatToDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Delete chat?") },
            text = {
                Text(
                    "\"${chat.name}\" and every item and file in it will be removed from the server.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatToDelete = null
                        viewModel.deleteChat(chat.id)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (settingsOpen) {
        ServerSettingsDialog(
            state = state,
            onDismiss = { settingsOpen = false },
            onSaveWorkerUrl = viewModel::setWorkerUrl,
            onForget = {
                settingsOpen = false
                viewModel.forgetServer()
            },
        )
    }

    previewItem?.let { item ->
        ImagePreviewDialog(
            item = item,
            session = state.session,
            onDismiss = { previewItem = null },
        )
    }
}

@Composable
private fun ChatDrawer(
    state: UiState,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (Chat) -> Unit,
    onDelete: (Chat) -> Unit,
) {
    Column(Modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Selfdrop", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.session?.baseUrl?.let(::hostOf).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FilledTonalButton(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New chat")
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            items(state.chats, key = { it.id }) { chat ->
                ChatDrawerRow(
                    chat = chat,
                    selected = chat.id == state.selectedChatId,
                    onClick = { onSelect(chat.id) },
                    onRename = { onRename(chat) },
                    onDelete = { onDelete(chat) },
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Connected to ${state.session?.baseUrl?.let(::hostOf).orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatDrawerRow(
    chat: Chat,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chat.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Chat options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionLabel(connection: ConnectionState) {
    val (label, color) = when (connection) {
        ConnectionState.Live -> "Live" to Color(0xFF16A34A)
        ConnectionState.Connecting -> "Connecting" to Color(0xFFD97706)
        ConnectionState.Reconnecting -> "Reconnecting" to Color(0xFFD97706)
        is ConnectionState.Offline -> "Offline" to MaterialTheme.colorScheme.error
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionBanner(connection: ConnectionState, viewModel: AppViewModel) {
    val offline = connection is ConnectionState.Offline
    val text = when (connection) {
        ConnectionState.Connecting -> "Connecting to your server…"
        ConnectionState.Reconnecting -> "Reconnecting…"
        is ConnectionState.Offline -> connection.reason
        ConnectionState.Live -> ""
    }

    Surface(
        color = if (offline) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!offline) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = viewModel::retryNow) { Text("Retry") }
        }
    }
}

@Composable
private fun NewChatDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("New chat") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chat") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameChatDialog(chat: Chat, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable(chat.id) { mutableStateOf(chat.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ServerSettingsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onSaveWorkerUrl: (String) -> Unit,
    onForget: () -> Unit,
) {
    val session = state.session ?: return
    var workerUrl by remember(state.workerUrl) { mutableStateOf(state.workerUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This server") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Address", style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(
                        text = session.baseUrl,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("Server id", style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(
                        text = session.serverId,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("Rendezvous Worker", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = workerUrl,
                    onValueChange = { workerUrl = it },
                    label = { Text("Worker record URL") },
                    singleLine = true,
                )
                Button(onClick = { onSaveWorkerUrl(workerUrl) }) {
                    Text("Save Worker URL")
                }
                Text(
                    text = "The Worker only publishes this server's LAN address. Its write " +
                        "token never leaves the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(
                    onClick = onForget,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Forget this server")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ImagePreviewDialog(item: Item, session: Session?, onDismiss: () -> Unit) {
    val url = session?.let { "${it.baseUrl}/preview/${item.id}" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (url != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Preview unavailable", color = Color.White)
                        }
                    },
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
