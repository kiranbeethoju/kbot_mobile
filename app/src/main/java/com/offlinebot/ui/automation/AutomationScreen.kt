package com.offlinebot.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinebot.data.database.entities.AutomationRuleEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    paddingValues: PaddingValues,
    viewModel: AutomationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(scaffoldPadding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Header with battery indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automation Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val batColor = when { state.isCharging -> Color(0xFF43A047); state.batteryLevel < 20 -> Color(0xFFE53935); state.batteryLevel < 50 -> Color(0xFFFFA000); else -> Color(0xFF43A047) }
            Icon(Icons.Outlined.BatteryFull, contentDescription = null, tint = batColor, modifier = Modifier.size(16.dp))
            Text("${state.batteryLevel}%", style = MaterialTheme.typography.labelSmall, color = batColor)
        }

        // Battery + job status bar
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${state.pendingJobs}", fontWeight = FontWeight.Bold) }
                Column { Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${state.activeJobs}", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3)) }
                Column { Text("Battery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (state.isCharging) "Charging" else "${state.batteryLevel}%", fontWeight = FontWeight.Bold) }
                Column { Text("Rules", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${state.rules.size}", fontWeight = FontWeight.Bold, color = Color(0xFF4ECDC4)) }
            }
        }

        // Rule editor
        if (state.editingRule) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New Rule", fontWeight = FontWeight.Bold)
                    OutlinedTextField(state.editingRuleName, viewModel::updateEditName, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.editingRuleTrigger == "time", { viewModel.updateEditTrigger("time") }, label = { Text("Time") })
                        FilterChip(state.editingRuleTrigger == "event", { viewModel.updateEditTrigger("event") }, label = { Text("Event") })
                        FilterChip(state.editingRuleTrigger == "nlp", { viewModel.updateEditTrigger("nlp") }, label = { Text("NLP") })
                    }
                    if (state.editingRuleTrigger == "time") {
                        OutlinedTextField(state.editingRuleSchedule, viewModel::updateEditSchedule, label = { Text("Schedule (daily_21, weekly_sun, etc)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(state.editingRuleCondition, viewModel::updateEditCondition, label = { Text("Condition (e.g. contains 'meeting')") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.editingRuleAction, viewModel::updateEditAction, label = { Text("Action (summary, resurface, reminder, trend)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(viewModel::saveRule) { Text("Save") }
                        OutlinedButton(viewModel::cancelEdit) { Text("Cancel") }
                    }
                }
            }
        } else {
            OutlinedButton(viewModel::startNewRule, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Add Rule")
            }
        }

        // Rules list
        Text("Scheduled Intelligence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        state.rules.forEach { rule -> RuleCard(rule, viewModel) }

        // Execution History
        Text("Execution History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        state.logs.take(15).forEach { log ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (log.status) { "success" -> Icons.Outlined.CheckCircle; "failed" -> Icons.Outlined.Error; else -> Icons.Outlined.SkipNext },
                        contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = when (log.status) { "success" -> Color(0xFF43A047); "failed" -> Color(0xFFE53935); else -> Color(0xFFFFA000) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(log.output ?: "No output", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Text("${timeFormat.format(Date(log.createdAt))} • ${log.durationMs}ms • ${log.batteryState ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Enqueue daily workers button
        OutlinedButton(viewModel::enqueueDailyWorkers, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Schedule Daily Intelligence")
        }
    }
    }
}

@Composable
private fun RuleCard(rule: AutomationRuleEntity, viewModel: AutomationViewModel) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (rule.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(when(rule.triggerType) { "time" -> Icons.Outlined.Schedule; "event" -> Icons.Outlined.Notifications; else -> Icons.Outlined.Psychology }, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(rule.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggleRule(rule) })
            }
            Row {
                Text("Trigger: ${rule.triggerType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                if (rule.schedule != null) { Text(" • ${rule.schedule}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace) }
                Spacer(Modifier.weight(1f))
                Text("${rule.permissionLevel}", style = MaterialTheme.typography.labelSmall, color = when(rule.permissionLevel) { "experimental" -> Color(0xFFE53935); "advanced" -> Color(0xFFFFA000); else -> Color(0xFF43A047) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.runRuleNow(rule) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run Now", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.deleteRule(rule) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontSize = 13.sp)
                }
            }
        }
    }
}
