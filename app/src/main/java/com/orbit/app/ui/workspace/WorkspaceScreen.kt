package com.orbit.app.ui.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.data.workspace.WorkspaceEntry
import com.orbit.app.ui.components.ConfirmDialog
import com.orbit.app.ui.components.EmptyState
import com.orbit.app.ui.components.NameDialog
import com.orbit.app.ui.orbitViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(onOpenFile: (String) -> Unit) {
    val viewModel: WorkspaceViewModel = orbitViewModel { WorkspaceViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.adopt(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.workspace?.name?.takeIf { it.isNotBlank() } ?: "Workspace",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.workspace != null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showNewFolder = true }) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Workspace options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.workspace == null) "Choose folder" else "Change folder") },
                                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    pickFolder.launch(null)
                                },
                            )
                            if (state.workspace != null) {
                                DropdownMenuItem(
                                    text = { Text("Disconnect folder") },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.forget()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state.workspace != null) {
                ExtendedFloatingActionButton(
                    onClick = { showNewFile = true },
                    icon = { Icon(Icons.Outlined.NoteAdd, contentDescription = null) },
                    text = { Text("New file") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.workspace == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        title = "Open a workspace",
                        message = "Choose a folder to browse, edit and hand to the coding agent. ORBIT only sees the folder you pick.",
                    )
                    TextButton(onClick = { pickFolder.launch(null) }) { Text("Choose folder") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Breadcrumbs(
                rootName = state.workspace?.name?.takeIf { it.isNotBlank() } ?: "Workspace",
                path = state.currentPath,
                onOpen = viewModel::open,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            state.error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                        )
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }

            if (state.loading && state.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This folder is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.path }) { entry ->
                        WorkspaceRow(
                            entry = entry,
                            onOpen = {
                                if (entry.isDirectory) viewModel.open(entry.path)
                                else onOpenFile(entry.path)
                            },
                            onRename = { renameTarget = entry },
                            onDelete = { deleteTarget = entry },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 56.dp),
                        )
                    }
                }
            }
        }
    }

    if (showNewFile) {
        NameDialog(
            title = "New file",
            label = "File name",
            confirmLabel = "Create",
            onDismiss = { showNewFile = false },
            onConfirm = { name ->
                viewModel.createFile(name)
                showNewFile = false
            },
        )
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showNewFolder = false
            },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename",
            label = "Name",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.rename(target, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = if (target.isDirectory) "Delete folder?" else "Delete file?",
            message = if (target.isDirectory) {
                "“${target.name}” and everything inside it will be deleted from the workspace."
            } else {
                "“${target.name}” will be deleted from the workspace."
            },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.delete(target)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun Breadcrumbs(
    rootName: String,
    path: String,
    onOpen: (String) -> Unit,
) {
    val segments = remember(path) { path.split('/').filter { it.isNotBlank() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = rootName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onOpen("") }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        segments.forEachIndexed { index, segment ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val target = segments.take(index + 1).joinToString("/")
            Text(
                text = segment,
                style = MaterialTheme.typography.labelLarge,
                color = if (index == segments.lastIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .clickable { onOpen(target) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun WorkspaceRow(
    entry: WorkspaceEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Outlined.Folder
            else Icons.Outlined.Description,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isDirectory) "Folder" else describeFile(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Options for ${entry.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = {
                        Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

private fun describeFile(entry: WorkspaceEntry): String {
    val size = when {
        entry.size < 1024 -> "${entry.size} B"
        entry.size < 1024 * 1024 -> "${entry.size / 1024} KB"
        else -> "${entry.size / (1024 * 1024)} MB"
    }
    val date = runCatching {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.lastModified))
    }.getOrDefault("")
    return if (date.isBlank()) size else "$size · $date"
}
