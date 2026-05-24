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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Create Automation Rule", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    // 1. Name
                    OutlinedTextField(
                        state.editingRuleName, viewModel::updateEditName,
                        label = { Text("Rule Name") },
                        placeholder = { Text("e.g. Morning Summary") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Trigger Type
                    Text("When should this rule run?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.editingRuleTrigger == "time",
                            { viewModel.updateEditTrigger("time") },
                            label = { Text("🕐 Time") },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp)) })
                        FilterChip(state.editingRuleTrigger == "event",
                            { viewModel.updateEditTrigger("event") },
                            label = { Text("🔔 Event") },
                            leadingIcon = { Icon(Icons.Outlined.Notifications, null, Modifier.size(16.dp)) })
                        FilterChip(state.editingRuleTrigger == "nlp",
                            { viewModel.updateEditTrigger("nlp") },
                            label = { Text("🧠 NLP") },
                            leadingIcon = { Icon(Icons.Outlined.Psychology, null, Modifier.size(16.dp)) })
                    }

                    // 3. Time Picker (only for time-based rules)
                    if (state.editingRuleTrigger == "time") {
                        Text("At what time?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { viewModel.toggleTimePicker() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "%02d:%02d".format(state.editingRuleHour, state.editingRuleMinute),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            "Rule will run daily at this time. Battery-safe: skips if <20% and not charging.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Time picker dialog
                    if (state.showTimePicker) {
                        AlertDialog(
                            onDismissRequest = { viewModel.toggleTimePicker() },
                            title = { Text("Select Time") },
                            text = {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                        // Hour picker
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(onClick = { viewModel.updateEditHour((state.editingRuleHour + 1) % 24) }) {
                                                Icon(Icons.Outlined.KeyboardArrowUp, "Up")
                                            }
                                            Text(
                                                "%02d".format(state.editingRuleHour),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            IconButton(onClick = { viewModel.updateEditHour((state.editingRuleHour - 1 + 24) % 24) }) {
                                                Icon(Icons.Outlined.KeyboardArrowDown, "Down")
                                            }
                                        }
                                        Text(":", style = MaterialTheme.typography.headlineMedium)
                                        // Minute picker (5-min steps)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(onClick = { viewModel.updateEditMinute((state.editingRuleMinute + 5) % 60) }) {
                                                Icon(Icons.Outlined.KeyboardArrowUp, "Up")
                                            }
                                            Text(
                                                "%02d".format(state.editingRuleMinute),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            IconButton(onClick = { viewModel.updateEditMinute((state.editingRuleMinute - 5 + 60) % 60) }) {
                                                Icon(Icons.Outlined.KeyboardArrowDown, "Down")
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { viewModel.toggleTimePicker() }) { Text("Done") }
                            }
                        )
                    }

                    // 4. Condition (for event/NLP triggers)
                    if (state.editingRuleTrigger != "time") {
                        OutlinedTextField(
                            state.editingRuleCondition, viewModel::updateEditCondition,
                            label = { Text("Condition") },
                            placeholder = { Text("e.g. keyword 'meeting' or 5 mentions") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("What triggers this rule? A keyword, a count, or a pattern in your memories.") }
                        )
                    }

                    // 5. Action
                    Text("What should it do?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("summary" to "Summary", "resurface" to "Resurface", "reminder" to "Remind", "cleanup" to "Clean").forEach { (value, label) ->
                            FilterChip(
                                selected = state.editingRuleAction.contains(value),
                                onClick = { viewModel.updateEditAction(value) },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        state.editingRuleAction, viewModel::updateEditAction,
                        label = { Text("Action") },
                        placeholder = { Text("summary, resurface, reminder, trend, cleanup, tag, reflection") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                when {
                                    state.editingRuleAction.contains("summary") -> "Counts today's memories and gives you a summary."
                                    state.editingRuleAction.contains("resurface") -> "Finds forgotten memories from weeks ago."
                                    state.editingRuleAction.contains("reminder") -> "Scans for reminder keywords like 'remind', 'todo', 'don't forget'."
                                    state.editingRuleAction.contains("cleanup") -> "Counts unprocessed recordings and checks storage."
                                    state.editingRuleAction.contains("tag") -> "Labels memories matching your keywords."
                                    state.editingRuleAction.contains("trend") -> "Analyzes topic patterns across recent memories."
                                    state.editingRuleAction.contains("reflection") -> "Reflects on the past day's memories."
                                    else -> "Choose an action above or type a custom one."
                                }
                            )
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(viewModel::saveRule) { Text("Save Rule") }
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

        // Scheduler heartbeat
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val blink = (System.currentTimeMillis() / 1000) % 2 == 0L
                    Icon(
                        Icons.Outlined.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = if (state.pendingJobs > 0 || state.activeJobs > 0) Color(0xFF43A047)
                               else if (blink) Color(0xFF2196F3)
                               else Color(0xFF666666)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.pendingJobs > 0) "Scheduler Active — ${state.pendingJobs} jobs pending"
                            else if (state.activeJobs > 0) "Scheduler Running — ${state.activeJobs} active"
                            else "Scheduler Idle — listening for jobs",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Polling every ${state.schedulerIntervalMin}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Interval picker
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1L to "1m", 5L to "5m", 15L to "15m", 30L to "30m", 60L to "1h").forEach { (mins, label) ->
                        FilterChip(
                            selected = state.schedulerIntervalMin == mins,
                            onClick = { viewModel.setSchedulerInterval(mins) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

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
