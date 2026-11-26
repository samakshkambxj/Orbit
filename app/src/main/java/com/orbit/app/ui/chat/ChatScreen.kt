package com.orbit.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.orbit.app.chat.DiffKind
import com.orbit.app.chat.decodeStoredToolCalls
import com.orbit.app.chat.describeToolCall
import com.orbit.app.chat.diffLines
import com.orbit.app.data.db.MessageEntity
import com.orbit.app.data.model.MessageStatus
import com.orbit.app.data.model.ToolCall
import com.orbit.app.ui.components.ConfirmDialog
import com.orbit.app.ui.components.MarkdownText
import com.orbit.app.ui.components.RenameChatDialog
import com.orbit.app.ui.components.SystemPromptDialog
import com.orbit.app.ui.orbitViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    val viewModel: ChatViewModel = orbitViewModel(key = conversationId) {
        ChatViewModel(conversationId, it)
    }
    val context = LocalContext.current
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val pendingImages by viewModel.pendingImages.collectAsStateWithLifecycle()
    val imageError by viewModel.imageError.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.addImage(uri)
    }

    var modelMenuOpen by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSystemPrompt by remember { mutableStateOf(false) }

    val modelName = models.firstOrNull {
        it.providerId == conversation?.providerId && it.modelId == conversation?.modelId
    }?.displayName ?: conversation?.modelId.orEmpty()

    val currentConversation = conversation
    val providerAvailable = currentConversation == null ||
        providers.any { it.id == currentConversation.providerId }
    val lastAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
    val pendingApproval = messages.lastOrNull {
        it.status == MessageStatus.AWAITING_APPROVAL.name
    }
    val pendingCalls = remember(pendingApproval?.id, pendingApproval?.toolCalls) {
        decodeStoredToolCalls(pendingApproval?.toolCalls.orEmpty())
    }
    val toolPreviews by viewModel.toolPreviews.collectAsStateWithLifecycle()
    LaunchedEffect(pendingApproval?.id) {
        viewModel.loadToolPreviews(pendingCalls)
    }
    val agentNeedsWorkspace = conversation?.agentEnabled == true && workspace == null
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= messages.lastIndex - 1) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = conversation?.title ?: "Chat",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { modelMenuOpen = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = modelName.ifBlank { "Select a model" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                if (conversation?.agentEnabled == true) {
                                    Text(
                                        text = "· Agent",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false },
                                modifier = Modifier.heightIn(max = 380.dp),
                            ) {
                                models.forEach { model ->
                                    val selected = model.providerId == conversation?.providerId &&
                                        model.modelId == conversation?.modelId
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.displayName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = providers.firstOrNull { it.id == model.providerId }?.name
                                                        ?: "Provider",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (selected) {
                                                Icon(Icons.Outlined.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectModel(model.providerId, model.modelId)
                                            modelMenuOpen = false
                                        },
                                    )
                                }
                                if (models.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No models yet") },
                                        onClick = {
                                            modelMenuOpen = false
                                            onOpenProviders()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { actionsOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Chat options")
                        }
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    actionsOpen = false
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share chat") },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = {
                                    actionsOpen = false
                                    shareChat(context, conversation?.title.orEmpty(), messages)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("System prompt") },
                                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                onClick = {
                                    actionsOpen = false
                                    showSystemPrompt = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (conversation?.agentEnabled == true) {
                                            "Agent mode: on"
                                        } else {
                                            "Agent mode: off"
                                        },
                                    )
                                },
                                leadingIcon = { Icon(Icons.Outlined.Build, contentDescription = null) },
                                onClick = {
                                    actionsOpen = false
                                    viewModel.setAgentEnabled(conversation?.agentEnabled != true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    actionsOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column {
                if (!providerAvailable) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "This chat's provider is missing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenProviders) { Text("Fix") }
                        }
                    }
                }
                if (agentNeedsWorkspace) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Agent mode needs a workspace folder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenWorkspace) { Text("Open") }
                        }
                    }
                }
                if (pendingApproval != null && pendingCalls.isNotEmpty() && !isGenerating) {
                    ApprovalCard(
                        calls = pendingCalls,
                        previews = toolPreviews,
                        onApprove = viewModel::approveToolCalls,
                        onDeny = viewModel::denyToolCalls,
                    )
                }
                Composer(
                    value = input,
                    onValueChange = viewModel::setInput,
                    isGenerating = isGenerating,
                    canSend = (input.isNotBlank() || pendingImages.isNotEmpty()) &&
                        pendingApproval == null,
                    pendingImages = pendingImages,
                    imageError = imageError,
                    onAttach = {
                        viewModel.clearImageError()
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemoveImage = viewModel::removeImage,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = "Ask ORBIT anything",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "Your messages stay on this device. Model requests go straight to your provider.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(messages, key = { it.id }) { message ->
                    when (message.role) {
                        "user" -> UserMessage(message)
                        "assistant" -> AssistantMessage(
                            message = message,
                            canRetry = !isGenerating && message.id == lastAssistantId,
                            onRetry = viewModel::retry,
                        )
                        "tool" -> ToolResultMessage(message)
                        else -> Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        RenameChatDialog(
            initialTitle = conversation?.title.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { title ->
                viewModel.rename(title)
                showRename = false
            },
        )
    }

    if (showSystemPrompt) {
        SystemPromptDialog(
            initialPrompt = conversation?.systemPrompt.orEmpty(),
            onDismiss = { showSystemPrompt = false },
            onConfirm = { prompt ->
                viewModel.setSystemPrompt(prompt)
                showSystemPrompt = false
            },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete chat?",
            message = "This chat and its messages will be removed from this device.",
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                viewModel.delete(onDeleted = onBack)
            },
        )
    }
}

@Composable
private fun UserMessage(message: MessageEntity) {
    val images = remember(message.attachments) {
        message.attachments.lines().filter { it.isNotBlank() }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (images.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        images.forEach { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = "Attached image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(132.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                }
                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: MessageEntity,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "ORBIT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
            if (canRetry) {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Retry response",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (message.content.isNotBlank() && message.status != MessageStatus.STREAMING.name) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.content)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        if (message.content.isNotBlank()) {
            MarkdownText(text = message.content)
        }
        val calls = remember(message.toolCalls) {
            decodeStoredToolCalls(message.toolCalls)
        }
        if (calls.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                calls.forEach { call -> ToolCallRow(call) }
            }
        }
        when (message.status) {
            MessageStatus.AWAITING_APPROVAL.name -> {
                Text(
                    text = "Waiting for your approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            MessageStatus.STREAMING.name -> {
                if (message.content.isBlank()) {
                    Text(
                        text = "Thinking…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .widthIn(max = 48.dp),
                    )
                }
            }
            MessageStatus.ERROR.name -> {
                Text(
                    text = message.error ?: "Generation failed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            MessageStatus.CANCELLED.name -> {
                Text(
                    text = message.error ?: "Generation stopped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolCallRow(call: ToolCall) {
    val summary = remember(call) { describeToolCall(call) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = call.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    calls: List<ToolCall>,
    previews: List<ToolPreview>,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Approve tool calls",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = "Review the proposed changes to your workspace.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                calls.forEach { call ->
                    ToolCallRow(call)
                    previews.firstOrNull { it.call.id == call.id }?.let { preview ->
                        if (preview.oldText != null || !preview.newText.isNullOrEmpty()) {
                            DiffPreview(preview)
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDeny) { Text("Deny") }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Approve") }
            }
        }
    }
}

@Composable
private fun DiffPreview(preview: ToolPreview) {
    var expanded by remember(preview.call.id, preview.oldText, preview.newText) {
        mutableStateOf(false)
    }
    val diff = remember(preview.oldText, preview.newText) {
        diffLines(preview.oldText.orEmpty(), preview.newText.orEmpty())
    }
    val changed = diff.count { it.kind != DiffKind.SAME }
    if (changed == 0) {
        Text(
            text = "No text changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        return
    }
    val limit = if (expanded) diff.size else 24
    val shown = diff.take(limit)

    Column {
        shown.forEach { line -> DiffRow(line) }
        if (diff.size > shown.size) {
            TextButton(onClick = { expanded = true }) {
                Text("Show full diff (${diff.size} lines)")
            }
        }
    }
}

@Composable
private fun DiffRow(line: com.orbit.app.chat.DiffLine) {
    val background = when (line.kind) {
        DiffKind.ADDED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        DiffKind.REMOVED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        DiffKind.SAME -> Color.Transparent
    }
    val foreground = when (line.kind) {
        DiffKind.ADDED -> MaterialTheme.colorScheme.onPrimaryContainer
        DiffKind.REMOVED -> MaterialTheme.colorScheme.onErrorContainer
        DiffKind.SAME -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val prefix = when (line.kind) {
        DiffKind.ADDED -> "+"
        DiffKind.REMOVED -> "-"
        DiffKind.SAME -> " "
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = foreground,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = line.text.ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = foreground,
        )
    }
}

@Composable
private fun ToolResultMessage(message: MessageEntity) {
    var expanded by remember { mutableStateOf(false) }
    val lines = remember(message.content) { message.content.lines() }
    val preview = if (expanded) message.content else lines.take(6).joinToString("\n")
    val accent = if (message.toolError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = message.toolName.ifBlank { "tool" },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = if (message.toolError) "Failed" else "Done",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
            if (message.content.isNotBlank()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (lines.size > 6) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(if (expanded) "Show less" else "Show more")
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    canSend: Boolean,
    pendingImages: List<String>,
    imageError: String?,
    onAttach: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (imageError != null) {
                Text(
                    text = imageError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (pendingImages.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    pendingImages.forEach { path ->
                        PendingImage(path = path, onRemove = { onRemoveImage(path) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "Attach image",
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message ORBIT") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                FilledIconButton(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating || canSend,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isGenerating) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Outlined.Stop
                        else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "Stop generating" else "Send",
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingImage(path: String, onRemove: () -> Unit) {
    Box {
        AsyncImage(
            model = File(path),
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove image",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun shareChat(
    context: android.content.Context,
    title: String,
    messages: List<MessageEntity>,
) {
    val body = buildString {
        appendLine(title.ifBlank { "ORBIT chat" })
        appendLine()
        messages.forEach { message ->
            val label = when (message.role) {
                "user" -> "You"
                "assistant" -> "ORBIT"
                else -> message.role
            }
            appendLine("$label:")
            appendLine(message.content.ifBlank { message.error.orEmpty() })
            appendLine()
        }
    }.trim()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "ORBIT chat" })
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share chat"))
}
