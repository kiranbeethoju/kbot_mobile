package com.offlinebot.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinebot.ai.llm.LlmLoadState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    paddingValues: PaddingValues,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Voice input launcher
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spokenText = matches?.firstOrNull() ?: ""
        if (spokenText.isNotBlank()) {
            viewModel.updateInput(state.inputText + " " + spokenText)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // System prompt editor dialog state — always accessible
    var showPromptEditor by remember { mutableStateOf(false) }
    var editingPrompt by remember(state.activeSystemPrompt) { mutableStateOf(state.activeSystemPrompt) }

    // System prompt editor dialog
    if (showPromptEditor) {
        AlertDialog(
            onDismissRequest = { showPromptEditor = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("System Prompt")
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(Modifier.verticalScroll(scrollState)) {
                    Text(
                        "This prompt is prepended to every message sent to the LLM. Tool instructions are appended automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingPrompt,
                        onValueChange = { editingPrompt = it },
                        label = { Text("Your system prompt") },
                        placeholder = { Text("You are a helpful assistant...") },
                        minLines = 6,
                        maxLines = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    // Show what gets auto-appended
                    Text("Auto-appended tool instructions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            "Tools: show_notification, set_alarm, call_contact, send_message, get_call_log, get_contacts\n\n" +
                            "+ Your stored memories are injected as context when relevant.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSystemPrompt(editingPrompt)
                    showPromptEditor = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingPrompt = state.activeSystemPrompt
                    showPromptEditor = false
                }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues)
    ) {
        // Header
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModelStatusDot(state.llmLoadState, state.useCloudModel)
                        Spacer(Modifier.width(8.dp))
                        Text("Chat with your data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.totalMemories} memories | ${llmStatusLabel(state)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.useCloudModel || state.llmLoadState == LlmLoadState.Ready) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        // Privacy badge
                        val (badgeText, badgeColor) = if (state.useCloudModel) "CLOUD" to Color(0xFF2196F3) else "100% OFFLINE" to Color(0xFF43A047)
                        Text(badgeText,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (state.messages.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearChat) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear chat")
                    }
                }
            }

            // Nickname row
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                var showNickEdit by remember { mutableStateOf(false) }
                var nickText by remember { mutableStateOf(state.assistantNickname) }
                if (showNickEdit) {
                    OutlinedTextField(nickText, { nickText = it }, singleLine = true,
                        modifier = Modifier.weight(1f).height(48.dp),
                        placeholder = { Text("Assistant name") },
                        trailingIcon = { IconButton(onClick = { viewModel.setAssistantNickname(nickText); showNickEdit = false }) { Icon(Icons.Outlined.Check, "Save", Modifier.size(18.dp)) } })
                } else {
                    TextButton(onClick = { nickText = state.assistantNickname; showNickEdit = true }) {
                        Text("Assistant: ${state.assistantNickname}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ---- Always-visible System Prompt row ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { editingPrompt = state.activeSystemPrompt; showPromptEditor = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("System Prompt", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        state.activeSystemPrompt.ifBlank { "Default: You are a helpful assistant. Be concise." }.take(80) +
                            if ((state.activeSystemPrompt.ifBlank { "Default: You are a helpful assistant. Be concise." }).length > 80) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(Icons.Outlined.Edit, contentDescription = "Edit system prompt", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = !state.useCloudModel, onClick = { if (state.useCloudModel) viewModel.toggleModelSource() },
                    label = { Text("On-Device") },
                    leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) })
                FilterChip(selected = state.useCloudModel, onClick = { if (!state.useCloudModel) viewModel.toggleModelSource() },
                    label = { Text("Cloud AI") },
                    leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
        }

        if (!state.useCloudModel && state.llmLoadState == LlmLoadState.Loading) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Loading Gemma 3 1B (~700MB RAM)...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!state.useCloudModel && state.llmLoadState == LlmLoadState.Unloaded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gemma 3 1B — load on demand", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFA000))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::loadModel, modifier = Modifier.fillMaxWidth()) {
                    Text("Load Gemma Now", fontWeight = FontWeight.Bold)
                }
                Text("658MB • auto-unloads after 5min idle",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!state.useCloudModel && state.llmLoadState == LlmLoadState.Failed) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gemma load failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::loadModel) { Text("Retry") }
            }
        }

        // RAM / Metrics bar when model loaded
        if (!state.useCloudModel && state.llmLoadState == LlmLoadState.Ready) {
            val m = state.modelMetrics
            val ramColor = when {
                m.availableRamMb < 1024 -> Color(0xFFE53935)
                m.availableRamMb < 2048 -> Color(0xFFFFA000)
                else -> Color(0xFF43A047)
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0D2137)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("RAM: ${m.availableRamMb}MB / ${m.deviceRamMb}MB", style = MaterialTheme.typography.labelSmall, color = ramColor)
                Spacer(Modifier.width(8.dp))
                Text("|", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text("Model: ${m.modelRamEstimateMb}MB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.weight(1f))
                Text("State: ${state.llmLoadState.name}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4FC3F7))
            }
        }

        // Context bar (appears after first message with memory search results)
        if (state.lastContext.isNotEmpty()) {
            var showFullContext by remember { mutableStateOf(false) }

            // Full context viewer dialog
            if (showFullContext) {
                AlertDialog(
                    onDismissRequest = { showFullContext = false },
                    title = { Text("Full LLM Context (${state.lastContextLen + state.fullSystemPrompt.length + state.fullUserMessage.length} chars)") },
                    text = {
                        val scrollState = rememberScrollState()
                        Column(Modifier.verticalScroll(scrollState)) {
                            Text("── System Prompt ──", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(state.fullSystemPrompt.ifBlank { "(none)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(8.dp))
                            Text("── User Message ──", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(state.fullUserMessage.ifBlank { "(none)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    },
                    confirmButton = { TextButton(onClick = { showFullContext = false }) { Text("Close") } }
                )
            }

            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1B5E20)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Context: ${state.lastContextLen}c", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(6.dp))
                Text("•", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Text("${state.totalMemories} searched", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showFullContext = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)) {
                    Text("View Full Context", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4FC3F7))
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.messages.isEmpty() && !state.loading) {
                item {
                    if (state.memoryNodes.isNotEmpty()) {
                        MemoryBrainGraph(recordingDao = viewModel.recordingDao, transcriptDao = viewModel.transcriptDao)
                    } else {
                        EmptyChatPlaceholder()
                    }
                }
            }
            items(state.messages, key = { it.timestamp }) { message ->
                ChatBubble(message)
            }
            // Tool call permission inline card
            if (state.pendingToolCall != null) {
                item {
                    val pending = state.pendingToolCall!!
                    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Assistant wants to: ${pending.description}",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::approveToolCall,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))) {
                                    Text("Allow Once", color = Color.White)
                                }
                                OutlinedButton(onClick = viewModel::approveToolCall) {
                                    Text("Always Allow")
                                }
                                TextButton(onClick = viewModel::denyToolCall) {
                                    Text("Deny", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                // Streaming response bubble
                if (state.streamingMessage.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Icon(Icons.Outlined.Storage, contentDescription = null,
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(4.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.widthIn(max = 340.dp)) {
                                Box(Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                                    MarkdownText(state.streamingMessage)
                                }
                                // Token speed
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${state.streamingTokens} tokens @ %.0f t/s".format(state.tokensPerSecond),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        var tick by remember { mutableStateOf(0) }
                        LaunchedEffect(state.streamingStartedAt) {
                            while (state.streamingStartedAt > 0L) {
                                delay(1000); tick++
                            }
                        }
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(state.loadingText.ifBlank { "Thinking..." }, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.streamingStartedAt > 0L) {
                                Text("Elapsed: ${tick}s", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 30.dp))
                            }
                        }
                    }
                }
            }
        }

        // Error
        state.errorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
        }

        // Input
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            // Voice input mic
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                    }
                    voiceLauncher.launch(intent)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = "Voice input", tint = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = state.inputText, onValueChange = viewModel::updateInput,
                placeholder = { Text("Ask about your memories...") },
                modifier = Modifier.weight(1f), maxLines = 3, shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))

            if (state.loading) {
                // Stop button
                IconButton(
                    onClick = viewModel::stopGeneration,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE53935), CircleShape)
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop generating", tint = Color.White)
                }
            } else {
                // Send button
                IconButton(
                    onClick = viewModel::sendMessage,
                    enabled = state.inputText.isNotBlank() && state.canSend,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(
                        if (state.inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send",
                        tint = if (state.inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun llmStatusLabel(state: ChatState): String {
    if (state.useCloudModel) return "Cloud (Nemotron Omni)"
    return when (state.llmLoadState) {
        LlmLoadState.Ready -> "Gemma ready"
        LlmLoadState.Loading -> "Loading Gemma..."
        LlmLoadState.Failed -> "Gemma load failed"
        LlmLoadState.Unloaded -> if (state.llmReady) "Gemma available" else "No model"
        LlmLoadState.IdleCached -> "Gemma idle"
        LlmLoadState.Unloading -> "Unloading..."
    }
}

@Composable
private fun ModelStatusDot(loadState: LlmLoadState, useCloud: Boolean) {
    val color = if (useCloud) Color(0xFF2196F3)
    else when (loadState) {
        LlmLoadState.Ready -> Color(0xFF43A047)
        LlmLoadState.Loading -> Color(0xFFFFA000)
        LlmLoadState.Failed -> Color(0xFFE53935)
        LlmLoadState.IdleCached -> Color(0xFF2196F3)
        LlmLoadState.Unloading -> Color(0xFFFFA000)
        else -> Color(0xFF9E9E9E)
    }
    Box(Modifier.size(12.dp).clip(CircleShape).background(color)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape))
}

@Composable
private fun EmptyChatPlaceholder() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Storage, contentDescription = null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Text("Chat with your memories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Ask questions about your recorded thoughts, saved notes, and captured moments.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(message: ChatMessage) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val context = LocalContext.current

    Row(Modifier.fillMaxWidth().combinedClickable(
        onClick = {},
        onLongClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KBot Message", message.text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    ), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
        if (!message.isUser) {
            Icon(Icons.Outlined.Storage, contentDescription = null,
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(4.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }

        Column(Modifier.widthIn(max = 340.dp),
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
            Box(Modifier.clip(RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp))
                .background(if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
            ) {
                if (message.isUser) {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    MarkdownText(message.text)
                }
            }
            Text(timeFormat.format(Date(message.timestamp)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
        }

        if (message.isUser) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Person, contentDescription = null,
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)).padding(4.dp),
                tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val annotated = buildAnnotatedString {
        val lines = text.split("\n")
        var inCodeBlock = false

        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                if (!inCodeBlock) append("\n")
                continue
            }

            if (inCodeBlock) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    background = Color(0x1A000000))) {
                    append(line)
                    append("\n")
                }
                continue
            }

            // Headers
            when {
                line.trimStart().startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                        append(line.trimStart().removePrefix("### "))
                        append("\n")
                    }
                }
                line.trimStart().startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.trimStart().removePrefix("## "))
                        append("\n")
                    }
                }
                line.trimStart().startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                        append(line.trimStart().removePrefix("# "))
                        append("\n")
                    }
                }
                else -> {
                    // Inline markdown: bold, italic, code
                    var remaining = line
                    while (remaining.isNotEmpty()) {
                        when {
                            remaining.startsWith("**") -> {
                                val end = remaining.indexOf("**", 2)
                                if (end > 2) {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(remaining.substring(2, end))
                                    }
                                    remaining = remaining.substring(end + 2)
                                } else { append(remaining[0]); remaining = remaining.substring(1) }
                            }
                            remaining.startsWith("*") && remaining.length > 1 && remaining[1] != '*' -> {
                                val end = remaining.indexOf("*", 1)
                                if (end > 1) {
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                        append(remaining.substring(1, end))
                                    }
                                    remaining = remaining.substring(end + 1)
                                } else { append(remaining[0]); remaining = remaining.substring(1) }
                            }
                            remaining.startsWith("`") -> {
                                val end = remaining.indexOf("`", 1)
                                if (end > 1) {
                                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                        background = Color(0x1A000000))) {
                                        append(remaining.substring(1, end))
                                    }
                                    remaining = remaining.substring(end + 1)
                                } else { append(remaining[0]); remaining = remaining.substring(1) }
                            }
                            else -> {
                                append(remaining[0])
                                remaining = remaining.substring(1)
                            }
                        }
                    }
                    append("\n")
                }
            }
        }
    }

    Text(text = annotated, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}
