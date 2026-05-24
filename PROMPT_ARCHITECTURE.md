# KBot — System Prompt Architecture

> **For AI coding assistants (Claude Code, Cursor, Gemini, etc.)**
> This document explains how LLM system prompts are structured, stored, and injected so you can modify them quickly without reading the full codebase.

---

## Quick Reference

| What | File | Lines | Purpose |
|------|------|-------|---------|
| **Local (Gemma) system prompt** | `ChatViewModel.kt` | `generateWithLlm()` ~L337–L366 | Builds system prompt for on-device Gemma 3 1B |
| **Cloud (Nemotron) system prompt** | `ChatViewModel.kt` | `generateWithCloudStreaming()` ~L368–L421 | Builds system prompt for NVIDIA cloud API |
| **Default fallback prompt** | `ChatViewModel.kt` | L338–339, L369–370 | `"You are a helpful assistant. Be concise."` |
| **Tool definitions (Cloud)** | `ChatViewModel.kt` | L375–L382 | 6 tools with JSON format examples |
| **Tool definitions (Local)** | `ChatViewModel.kt` | L344 | Compact one-liner tool list |
| **User-editable prompt (DB)** | `SystemPromptEntity.kt` | — | Room entity storing user's custom prompt |
| **Prompt editor UI** | `ChatScreen.kt` | ~L115–L180 | AlertDialog for editing prompt |
| **Prompt display row (header)** | `ChatScreen.kt` | ~L236–L260 | Always-visible tappable row in chat header |
| **Prompt save logic** | `ChatViewModel.kt` | `updateSystemPrompt()` ~L512–L527 | Persists to Room DB |
| **Tool executor (runtime)** | `ToolExecutor.kt` | — | Executes tool JSON after user approval |
| **Tool call parser** | `ChatViewModel.kt` | `extractAndQueueToolCall()` ~L426–L461 | Extracts tool JSON from LLM response |

---

## Prompt Assembly Pipeline

The system prompt is assembled in **3 layers** at runtime, in this order:

```
┌─────────────────────────────────────────────────────────┐
│  LAYER 1: User-Editable Base Prompt                     │
│  Source: Room DB → SystemPromptEntity (isActive=true)    │
│  Default: "You are a helpful assistant. Be concise."     │
│  Edited via: Chat header → System Prompt row → tap       │
├─────────────────────────────────────────────────────────┤
│  LAYER 2: Tool Instructions (auto-appended)              │
│  Source: Hardcoded in ChatViewModel.kt                   │
│  Cloud: Full JSON examples for each tool (L375–382)      │
│  Local: One-liner function signatures (L344)             │
├─────────────────────────────────────────────────────────┤
│  LAYER 3: Memory Context (auto-appended when relevant)   │
│  Source: searchMemories() → embedding + keyword search   │
│  Format: "--- Memory #N ---\nType: ...\nContent: ..."    │
│  Max: 10 memories per query                              │
└─────────────────────────────────────────────────────────┘
```

### Final prompt structure sent to LLM:

```
[Layer 1: user prompt]

[Layer 2: tool instructions]

[Layer 3: memories (if found)]
```

---

## File-by-File Guide

### `ChatViewModel.kt`
**Path:** `app/src/main/java/com/offlinebot/ui/chat/ChatViewModel.kt`

This is the **single source of truth** for prompt assembly. All prompt changes should be made here.

#### Key functions:

| Function | What it does |
|----------|-------------|
| `generateWithLlm()` | Builds full prompt for **local Gemma** model. Simpler tool instructions. |
| `generateWithCloudStreaming()` | Builds full prompt for **cloud Nemotron**. Detailed JSON tool examples. |
| `searchMemories()` | Retrieves relevant memories via embedding similarity + keyword search. Returns formatted context string. |
| `buildChatHistory()` | Returns last 10 messages as `"User: ...\nAssistant: ..."` for conversation continuity. |
| `extractAndQueueToolCall()` | Parses LLM response for tool call JSON. If found, queues it for user approval instead of showing raw JSON. |
| `updateSystemPrompt()` | Saves user's edited prompt to Room DB. Called from the UI editor. |

#### How the prompt is built (Cloud example):

```kotlin
// L368–L400 in ChatViewModel.kt
val sysPrompt = _state.value.activeSystemPrompt.ifBlank {
    "You are a helpful assistant. Be concise."  // ← DEFAULT PROMPT
}
val systemPrompt = buildString {
    append(sysPrompt)                           // ← LAYER 1: user prompt
    append("\n\nYou have access to these tools...")  // ← LAYER 2: tool instructions
    if (context.isNotEmpty()) {
        append("\n\nUser's stored memories:\n")  // ← LAYER 3: memories
        append(context)
    }
}
```

---

### `ChatScreen.kt`
**Path:** `app/src/main/java/com/offlinebot/ui/chat/ChatScreen.kt`

UI only. Contains:

1. **System Prompt row** (~L236–L260): Always-visible tappable card in chat header showing current prompt preview (truncated to 80 chars). Tap opens the editor dialog.

2. **System Prompt editor dialog** (~L115–L180): `AlertDialog` with:
   - Explanation text
   - `OutlinedTextField` for editing (6–15 lines)
   - Read-only box showing auto-appended tool instructions
   - Save/Cancel buttons

3. **Context viewer dialog** (in the green context bar): Shows `fullSystemPrompt` and `fullUserMessage` — the **exact** text sent to the LLM after assembly. Useful for debugging.

---

### `ToolExecutor.kt`
**Path:** `app/src/main/java/com/offlinebot/ai/cloud/ToolExecutor.kt`

Executes tool calls after user approval. If you **add a new tool**:

1. Add the tool's JSON format to the system prompt in `ChatViewModel.kt` (both `generateWithLlm` and `generateWithCloudStreaming`)
2. Add the tool name + description to `extractAndQueueToolCall()` toolNames map (~L427–L434)
3. Add execution logic in `ToolExecutor.kt`
4. Add result message formatting in `approveToolCall()` (~L467–L474)

---

### `SystemPromptEntity.kt`
**Path:** `app/src/main/java/com/offlinebot/data/database/entities/SystemPromptEntity.kt`

Room database entity:

```kotlin
@Entity(tableName = "system_prompts")
data class SystemPromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,          // Display name (e.g., "System Prompt")
    val content: String,       // The actual prompt text
    val isActive: Boolean,     // Only one should be active at a time
    val createdAt: Long = System.currentTimeMillis()
)
```

### `SystemPromptDao.kt`
**Path:** `app/src/main/java/com/offlinebot/data/database/dao/SystemPromptDao.kt`

Key queries:
- `observeActive()` → Flow that emits whenever active prompt changes
- `getActiveNow()` → One-shot fetch of current active prompt
- `deactivateAll()` → Sets all prompts to `isActive = false`

---

## Common Tasks

### "I want to change the default system prompt"

Edit the fallback string in **two places** in `ChatViewModel.kt`:

```kotlin
// Line ~338 (local)
val sysPrompt = _state.value.activeSystemPrompt.ifBlank {
    "You are a helpful assistant. Be concise."  // ← CHANGE THIS
}

// Line ~369 (cloud)
val sysPrompt = _state.value.activeSystemPrompt.ifBlank {
    "You are a helpful assistant. Be concise."  // ← AND THIS
}
```

### "I want to add a new tool"

4 places to update in `ChatViewModel.kt`:

1. **Cloud tool instructions** (~L375–L382): Add a new `append()` line with JSON format
2. **Local tool instructions** (~L344): Add to the one-liner
3. **Tool name map** in `extractAndQueueToolCall()` (~L427–L434): Add entry
4. **Result formatting** in `approveToolCall()` (~L467–L474): Add case

Plus implement execution in `ToolExecutor.kt`.

### "I want to change how memories are formatted"

Edit `searchMemories()` in `ChatViewModel.kt` (~L268–L335). The `buildString` block at L323–L332 controls the format:

```kotlin
buildString {
    append("--- Memory #${rec.id} ---\n")
    append("Type: ${rec.inputType}\n")
    append("Content: $content\n")
    append("Created: ${dateFormat.format(Date(rec.createdAt))}\n")
    // Add more fields here
}
```

### "I want to change the chat history format"

Edit `buildChatHistory()` in `ChatViewModel.kt` (~L489–L496):

```kotlin
return allMsgs.takeLast(10).joinToString("\n") { msg ->
    if (msg.isUser) "User: ${msg.text}" else "Assistant: ${msg.text}"
}
```

### "I want to change how the prompt editor looks"

Edit `ChatScreen.kt`:
- **Header row**: ~L236–L260
- **Editor dialog**: ~L119–L179

---

## Architecture Diagram

```
User types message
       │
       ▼
  sendMessage()
       │
       ├── searchMemories()  ──→  [embedding search + keyword + transcript]
       │        │                         │
       │        ▼                         ▼
       │   context string          "--- Memory #1 ---\n..."
       │
       ├── Is Cloud?
       │     ├─ YES → generateWithCloudStreaming()
       │     │         ├── Layer 1: activeSystemPrompt (from DB)
       │     │         ├── Layer 2: Tool JSON definitions (hardcoded)
       │     │         ├── Layer 3: memories context
       │     │         └── → NVIDIA API (streaming)
       │     │
       │     └─ NO → generateWithLlm()
       │               ├── Layer 1: activeSystemPrompt (from DB)
       │               ├── Layer 2: Tool one-liner (hardcoded)
       │               ├── Layer 3: memories context
       │               └── → LlamaCpp engine (local)
       │
       ▼
  LLM Response
       │
       ▼
  extractAndQueueToolCall()
       │
       ├── Has tool JSON? → Show permission card [Allow Once] [Always Allow] [Deny]
       │                          │
       │                     User approves → ToolExecutor.execute()
       │
       └── No tool → Display as chat bubble with Markdown rendering
```

---

## Environment Notes

- **Local model**: Gemma 3 1B (Q4 quantized, ~658MB RAM)
- **Cloud model**: NVIDIA Nemotron via `NvidiaApiClient`
- **Embedding model**: MiniLM-L6 ONNX for semantic memory search
- **Database**: Room (SQLite) for messages, prompts, recordings, embeddings
- **DI**: Hilt
- **Build**: Gradle with Kotlin DSL, minSdk 29, targetSdk 35

---

*Last updated: 2026-05-24*
