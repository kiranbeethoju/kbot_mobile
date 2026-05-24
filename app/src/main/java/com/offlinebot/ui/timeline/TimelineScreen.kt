package com.offlinebot.ui.timeline

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    paddingValues: PaddingValues,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(paddingValues).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

            // View toggle
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !state.useListView, onClick = { if (state.useListView) viewModel.toggleView() },
                    label = { Text("Cards") },
                    leadingIcon = { Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) })
                FilterChip(selected = state.useListView, onClick = { if (!state.useListView) viewModel.toggleView() },
                    label = { Text("List") },
                    leadingIcon = { Icon(Icons.Outlined.ViewList, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }

            Spacer(Modifier.width(8.dp))
            IconButton(onClick = viewModel::exportDatabase) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Export DB")
            }
        }

        if (state.recordings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Voice memories, text notes, and photos will appear here after capture.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.recordings) { recording ->
                    if (state.useListView) {
                        CompactRecordingRow(recording, state, viewModel)
                    } else {
                        RecordingCard(recording, state, viewModel)
                    }
                }
            }
        }

        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CalendarDateBadge(timestamp: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val month = SimpleDateFormat("MMM", Locale.getDefault()).format(Date(timestamp))
    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
        Text(day, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordingCard(
    recording: RecordingUi,
    state: TimelineState,
    viewModel: TimelineViewModel
) {
    val typeIcon = when (recording.inputType) { "text" -> Icons.Outlined.TextFields; "photo" -> Icons.Outlined.CameraAlt; else -> Icons.Outlined.AudioFile }
    val typeLabel = when (recording.inputType) { "text" -> "Note"; "photo" -> "Photo"; else -> "Recording" }
    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val isEditing = state.editingId == recording.id
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete memory?") },
            text = { Text("This action cannot be undone. Delete this ${typeLabel.lowercase()} permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(recording.id)
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(Modifier.fillMaxWidth().clickable(onClick = { viewModel.toggleExpand(recording.id) }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalendarDateBadge(recording.createdAt)
                Spacer(Modifier.width(10.dp))
                Icon(typeIcon, contentDescription = typeLabel, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(typeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(dateFormat.format(Date(recording.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (recording.inputType == "audio") {
                    Text(formatDuration(recording.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Location tag
            if (recording.latitude != null && recording.longitude != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = "Location", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    Spacer(Modifier.width(2.dp))
                    Text("%.4f, %.4f".format(recording.latitude, recording.longitude),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            if (!isEditing) {
                recording.textContent?.let { text ->
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = if (state.expandedId == recording.id) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
                recording.transcript?.let { transcript ->
                    Text(transcript, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (state.expandedId == recording.id) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedTextField(value = state.editingText, onValueChange = viewModel::updateEditText,
                    minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::saveEdit) { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Save") }
                    TextButton(onClick = viewModel::cancelEdit) { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel") }
                }
            }

            AnimatedVisibility(visible = state.expandedId == recording.id) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recording.inputType == "audio" && recording.filePath.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.togglePlayback(recording.id, recording.filePath) }) {
                                Icon(if (state.playingId == recording.id) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                    contentDescription = if (state.playingId == recording.id) "Stop" else "Play")
                            }
                            if (state.playingId == recording.id) Text("${state.playbackPositionMs / 1000}s", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            if (recording.transcript.isNullOrBlank()) {
                                TextButton(onClick = { viewModel.transcribeRecording(recording.id, recording.filePath) }, enabled = state.transcribingId != recording.id) {
                                    if (state.transcribingId == recording.id) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (state.transcribingId == recording.id) "Transcribing..." else "Transcribe")
                                }
                            }
                        }
                    }

                    if (recording.photos.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            recording.photos.take(5).forEach { photoPath ->
                                val bitmap = runCatching { BitmapFactory.decodeFile(photoPath)?.asImageBitmap() }.getOrNull()
                                if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Photo",
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (!isEditing) {
                            TextButton(onClick = { viewModel.startEdit(recording) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactRecordingRow(
    recording: RecordingUi,
    state: TimelineState,
    viewModel: TimelineViewModel
) {
    val typeIcon = when (recording.inputType) { "text" -> Icons.Outlined.TextFields; "photo" -> Icons.Outlined.CameraAlt; else -> Icons.Outlined.AudioFile }
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete memory?") },
            text = { Text("This action cannot be undone. Delete permanently?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteRecording(recording.id); showDeleteConfirm = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Card(Modifier.fillMaxWidth().clickable(onClick = { viewModel.toggleExpand(recording.id) }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarDateBadge(recording.createdAt)
            Spacer(Modifier.width(10.dp))
            Icon(typeIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(recording.textContent ?: recording.transcript ?: "No content", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateFormat.format(Date(recording.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recording.inputType == "audio") Text(formatDuration(recording.durationMs), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val remainSec = seconds % 60
    return "%d:%02d".format(minutes, remainSec)
}
