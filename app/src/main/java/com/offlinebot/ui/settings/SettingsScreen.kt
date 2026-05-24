package com.offlinebot.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.importPhoneContext() }

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        // ---- Active System Prompt (current) ----
        Text("Active System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("This prompt is sent to the AI with every message. Edit directly below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (state.editingActivePrompt) {
            OutlinedTextField(value = state.editingActivePromptContent, onValueChange = viewModel::updateActivePromptContent,
                minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveActivePrompt) { Text("Save") }
                OutlinedButton(onClick = viewModel::cancelEditActivePrompt) { Text("Cancel") }
            }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text(state.editingActivePromptContent.ifBlank { "Default: You are a helpful assistant..." },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = viewModel::startEditActivePrompt) { Text("Edit Prompt") }
                }
            }
        }

        // ---- LLM Context Toggles ----
        Text("LLM Context", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Include additional data in the AI context. Toggle what the AI can access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Include Contacts", fontWeight = FontWeight.Medium)
                        Text("AI can reference your contacts by name and number", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.includeContactsInContext, onCheckedChange = { viewModel.toggleContactsContext() })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Include Messages", fontWeight = FontWeight.Medium)
                        Text("AI can reference recent SMS messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.includeMessagesInContext, onCheckedChange = { viewModel.toggleMessagesContext() })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Tool Calls", fontWeight = FontWeight.Medium)
                        Text("AI can suggest calling numbers, reading notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = true, onCheckedChange = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Map Location Tags", fontWeight = FontWeight.Medium)
                        Text("Auto-tag entries with GPS location. Off = use last known location.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.mapsEnabled, onCheckedChange = { viewModel.toggleMapsEnabled() })
                }
            }
        }

        // ---- System Prompt Library ----
        Text("Prompt Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Save and switch between system prompts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        state.prompts.forEach { prompt ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(prompt.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (prompt.isActive) {
                            Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.startEditPrompt(prompt) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.deletePrompt(prompt) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(prompt.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    if (!prompt.isActive) {
                        TextButton(onClick = { viewModel.setActivePrompt(prompt) }) { Text("Set as active") }
                    }
                }
            }
        }

        // Prompt editor
        if (state.editingPromptId != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.editingPromptId == 0L) "New Prompt" else "Edit Prompt", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = state.editingPromptName, onValueChange = viewModel::updateEditName,
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.editingPromptContent, onValueChange = viewModel::updateEditContent,
                        label = { Text("System prompt content") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::savePrompt) { Text("Save") }
                        OutlinedButton(onClick = viewModel::cancelEdit) { Text("Cancel") }
                    }
                }
            }
        } else {
            OutlinedButton(onClick = viewModel::startNewPrompt, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add System Prompt")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- Local models ----
        Text("Local models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        state.models.forEach { model ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.label, style = MaterialTheme.typography.titleSmall)
                    Text(if (model.installed) model.sizeLabel else "Missing")
                    Text(model.path, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- Phone context ----
        Text("Phone context", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(state.phoneContextStatus)
        Button(
            onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS)) },
            enabled = !state.importingPhoneContext, modifier = Modifier.fillMaxWidth()
        ) { Text("Import contacts and SMS locally") }

        Text("Offline mode")
        Switch(checked = true, onCheckedChange = null)
        Text("No internet permission is declared in this MVP manifest.")

        Spacer(Modifier.height(8.dp))
        Text("App Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Version: 0.2.0 (Build 2)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("DB Schema: v6 | Models: Whisper, MiniLM, Gemma, Nemotron", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
