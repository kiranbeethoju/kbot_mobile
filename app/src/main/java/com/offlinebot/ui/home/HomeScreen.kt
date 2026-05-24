package com.offlinebot.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            state.capturedPhotoPaths.lastOrNull()?.let {
                viewModel.addPhotoPaths(listOf(it))
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val paths = uris.mapNotNull { uri -> copyUriToAppStorage(context, uri) }
        if (paths.isNotEmpty()) viewModel.addPhotoPaths(paths)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Private Offline Second Brain",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::updateQuery,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
            placeholder = { Text("Search forgotten thoughts") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
        )

        // Search results
        if (state.searchResults.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.searchResults.forEach { result ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text(result, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
            }
        }

        // Input mode tabs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedInputTab == 0,
                onClick = { viewModel.setInputTab(0) },
                label = { Text("Voice") },
                leadingIcon = { Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = state.selectedInputTab == 1,
                onClick = { viewModel.setInputTab(1) },
                label = { Text("Text") },
                leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = state.selectedInputTab == 2,
                onClick = { viewModel.setInputTab(2) },
                label = { Text("Camera") },
                leadingIcon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Tab content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state.selectedInputTab) {
                0 -> VoiceInputTab(state, viewModel::toggleRecording, viewModel::pauseRecording, viewModel::resumeRecording)
                1 -> TextInputTab(state, viewModel::updateTextNote, viewModel::saveTextNote)
                2 -> CameraInputTab(state, viewModel, cameraLauncher, galleryLauncher, context)
            }
        }

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeMetricCard("Recent", "${state.recentCount}", Modifier.weight(1f))
            HomeMetricCard("This week", "${state.weekCount}", Modifier.weight(1f))
        }

        // Saved confirmation
        state.savedMessage?.let { msg ->
            LaunchedEffect(msg) {
                delay(2500)
                viewModel.dismissSavedMessage()
            }
            Text(
                msg,
                color = Color(0xFF2E7D32),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Medium
            )
        }

        // Error
        state.errorMessage?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = viewModel::queueOfflineProcessing,
            enabled = state.pendingProcessingCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Process ${state.pendingProcessingCount} offline memories")
        }
    }
}

@Composable
private fun VoiceInputTab(
    state: HomeState,
    onToggle: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val pulse1 = infiniteTransition.animateFloat(1f, 0.2f, infiniteRepeatable(tween(1500)), label = "p1")
    val pulse2 = infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(2000)), label = "p2")
    val pulse3 = infiniteTransition.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(1700)), label = "p3")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Mic button with gradient rings
        Box(contentAlignment = Alignment.Center) {
            if (state.isRecording) {
                // Animated rings
                val ringSizes = listOf(1.8f to pulse1.value, 2.4f to pulse2.value, 3.0f to pulse3.value)
                ringSizes.forEach { (sizeRatio, alpha) ->
                    Canvas(Modifier.size((128 * sizeRatio).dp)) {
                        drawCircle(
                            color = Color(0xFFFF6B6B).copy(alpha = alpha * 0.3f),
                            radius = size.width / 2,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF4ECDC4).copy(alpha = alpha * 0.15f),
                            radius = size.width / 2 - 8.dp.toPx(),
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                        )
                    }
                }
            }

            LargeFloatingActionButton(
                onClick = onToggle,
                shape = CircleShape,
                modifier = Modifier.size(128.dp),
                containerColor = if (state.isRecording) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (state.isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (state.isRecording) "Stop recording" else "Start recording",
                    modifier = Modifier.size(52.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Recording duration
        if (state.isRecording) {
            Text(
                text = recordingDurationText(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B)
            )
            Spacer(Modifier.height(4.dp))
        }

        Text(
            text = when {
                state.isRecording && state.isPaused -> "Recording paused"
                state.isRecording -> "Recording locally"
                else -> "Tap to capture"
            },
            style = MaterialTheme.typography.titleMedium
        )

        if (state.isRecording) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.isPaused) {
                    OutlinedButton(onClick = onResume) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(20.dp))
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Outlined.Pause, contentDescription = "Pause", modifier = Modifier.size(20.dp))
                        Text("Pause")
                    }
                }
            }
        }
    }

    state.errorMessage?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

// Simple elapsed timer for recording
@Composable
private fun recordingDurationText(): String {
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsed++ }
    }
    val min = elapsed / 60
    val sec = elapsed % 60
    return "%02d:%02d".format(min, sec)
}

@Composable
private fun TextInputTab(
    state: HomeState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.TextFields,
            contentDescription = "Text note",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("Type a note", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = state.textNoteContent,
            onValueChange = onTextChange,
            placeholder = { Text("Write your thought here...") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onSave,
            enabled = state.textNoteContent.isNotBlank() && !state.saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.saving) "Saving..." else "Save as Note")
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CameraInputTab(
    state: HomeState,
    viewModel: HomeViewModel,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    galleryLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.CameraAlt,
            contentDescription = "Photo note",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("Capture a moment", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                val photoFile = File(
                    context.getExternalFilesDir(null),
                    "media/photo-${System.currentTimeMillis()}.jpg"
                ).apply { parentFile?.mkdirs() }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                viewModel.addPhotoPaths(listOf(photoFile.absolutePath))
                cameraLauncher.launch(uri)
            }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Take Photo")
            }

            OutlinedButton(onClick = {
                galleryLauncher.launch("image/*")
            }) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gallery")
            }
        }

        // Show captured photos
        if (state.capturedPhotoPaths.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.capturedPhotoPaths) { path ->
                    Box(modifier = Modifier.size(80.dp)) {
                        val bitmap = runCatching {
                            BitmapFactory.decodeFile(path)?.asImageBitmap()
                        }.getOrNull()
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Captured photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removePhotoPath(path) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Caption field
        if (state.capturedPhotoPaths.isNotEmpty()) {
            OutlinedTextField(
                value = state.photoCaption,
                onValueChange = viewModel::updatePhotoCaption,
                placeholder = { Text("What would you like to note about this?") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::savePhotoNote,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saving) "Saving..." else "Save ${state.capturedPhotoPaths.size} photo(s)")
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

private fun copyUriToAppStorage(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val dir = File(context.getExternalFilesDir(null), "media").apply { mkdirs() }
        val destFile = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    } catch (_: Exception) {
        null
    }
}
