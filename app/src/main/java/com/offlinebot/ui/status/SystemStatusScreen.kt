package com.offlinebot.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinebot.ai.llm.LlmLoadState
import com.offlinebot.utils.PerformanceMode
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SystemStatusScreen(
    paddingValues: PaddingValues,
    viewModel: SystemStatusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(
        Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("System Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Visible Intelligence — everything your device is doing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ── Active Models ──
        SectionHeader("Active Models")
        StatusCard {
            ModelRow("Gemma 3 1B", state.gemmaState, state.gemmaMetrics)
            Divider(Modifier.padding(vertical = 4.dp))
            StatusRow("Whisper.cpp", state.whisperStatus, if (state.whisperStatus == "READY") Color(0xFF43A047) else Color(0xFFFFA000))
            Divider(Modifier.padding(vertical = 4.dp))
            StatusRow("MiniLM Embeddings", if (state.embeddingQueueSize == 0) "IDLE" else "PROCESSING", Color(0xFF4ECDC4))
            StatusRow("Queue", "${state.embeddingQueueSize} pending", Color(0xFF6B7280))
        }

        // ── Device Health ──
        SectionHeader("Device Health")
        StatusCard {
            val ramColor = when { state.ramPressurePercent > 80 -> Color(0xFFE53935); state.ramPressurePercent > 60 -> Color(0xFFFFA000); else -> Color(0xFF43A047) }
            HealthBar("RAM", "${state.availableRamMb}MB free / ${state.totalRamMb}MB", state.ramPressurePercent, ramColor)
            HealthBar("Storage", "${state.storageFreeMb}MB free / ${state.storageTotalMb}MB", ((1f - state.storageFreeMb.toFloat()/state.storageTotalMb)*100).toInt(), Color(0xFF2196F3))
            StatusRow("App Storage", "${state.appStorageMb} MB", Color(0xFF6B7280))
            StatusRow("Battery Temp", "${state.batteryTemp}°C", if (state.batteryTemp > 40f) Color(0xFFE53935) else Color(0xFF43A047))
        }

        // ── Performance Mode ──
        SectionHeader("Performance Mode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PerformanceMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.performanceMode == mode,
                    onClick = { viewModel.setPerformanceMode(mode) },
                    label = { Text(mode.label, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Statistics ──
        SectionHeader("Memory Health")
        StatusCard {
            StatRow("Total Recordings", "${state.totalRecordings}")
            StatRow("Transcripts", "${state.totalTranscripts}")
            StatRow("Embeddings", "${state.totalEmbeddings}")
            StatRow("Local Inferences", "${state.totalLocalInferences}")
            StatRow("Cloud Requests", "${state.totalCloudRequests}")
        }

        // ── Storage Breakdown ──
        SectionHeader("Storage Breakdown")
        StatusCard {
            StatRow("App Total", "${state.appStorageMb} MB")
            StatRow("Models", "~${state.gemmaMetrics.modelRamEstimateMb + 86 + 141} MB (Gemma+Whisper+MiniLM)")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun ModelRow(name: String, state: LlmLoadState, metrics: com.offlinebot.ai.llm.ModelMetrics) {
    val stateColor = when(state) { LlmLoadState.Ready -> Color(0xFF43A047); LlmLoadState.Loading -> Color(0xFFFFA000); LlmLoadState.Failed -> Color(0xFFE53935); LlmLoadState.IdleCached -> Color(0xFF2196F3); else -> Color(0xFF9E9E9E) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(stateColor, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(8.dp))
            Text(name, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(state.name, style = MaterialTheme.typography.labelSmall, color = stateColor, fontFamily = FontFamily.Monospace)
        }
        if (state == LlmLoadState.Ready || state == LlmLoadState.IdleCached) {
            Text("RAM: ~${metrics.modelRamEstimateMb}MB | Context: ${metrics.contextTokens} tok | Speed: %.0f tok/s".format(metrics.tokensPerSec),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HealthBar(label: String, detail: String, percent: Int, color: Color) {
    Column { Row(Modifier.fillMaxWidth()) { Text(label, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f)); Text("$percent%", style = MaterialTheme.typography.labelSmall, color = color) }
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = color, trackColor = color.copy(alpha = 0.15f))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
