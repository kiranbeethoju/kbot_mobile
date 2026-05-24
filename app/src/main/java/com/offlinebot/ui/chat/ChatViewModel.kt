package com.offlinebot.ui.chat

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinebot.ai.ModelPaths
import com.offlinebot.ai.cloud.NvidiaApiClient
import com.offlinebot.ai.cloud.ToolExecutor
import com.offlinebot.ai.embeddings.EmbeddingEngine
import com.offlinebot.ai.llm.LlamaCppEngine
import com.offlinebot.ai.llm.LlmLoadState
import com.offlinebot.ai.llm.LocalLlmEngine
import com.offlinebot.ai.llm.ModelMetrics
import com.offlinebot.data.database.dao.ChatMessageDao
import com.offlinebot.data.database.dao.EmbeddingDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.PhoneContextDao
import com.offlinebot.data.database.dao.SystemPromptDao
import com.offlinebot.data.database.dao.TranscriptDao
import com.offlinebot.data.database.entities.ChatMessageEntity
import com.offlinebot.utils.cosineSimilarity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.LinkedHashSet
import java.util.Date
import java.util.Locale

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val loading: Boolean = false,
    val loadingText: String = "",
    val totalMemories: Int = 0,
    val llmReady: Boolean = false,
    val llmLoadState: LlmLoadState = LlmLoadState.Unloaded,
    val errorMessage: String? = null,
    val useCloudModel: Boolean = false,
    val activeSystemPrompt: String = "",
    val memoryNodes: List<MemoryNode> = emptyList(),
    val lastContext: String = "",
    val lastContextLen: Int = 0,
    val streamingMessage: String = "",
    val streamingTokens: Int = 0,
    val streamingStartedAt: Long = 0L,
    val fullSystemPrompt: String = "",
    val fullUserMessage: String = "",
    val modelMetrics: ModelMetrics = ModelMetrics(),
    val pendingToolCall: PendingToolCall? = null,
    val assistantNickname: String = "KBot"
) {
    val canSend: Boolean
        get() = useCloudModel || !llmReady || llmLoadState == LlmLoadState.Ready
    val tokensPerSecond: Float
        get() {
            if (streamingStartedAt == 0L || streamingTokens == 0) return 0f
            val elapsed = (System.currentTimeMillis() - streamingStartedAt) / 1000f
            return if (elapsed > 0) streamingTokens / elapsed else 0f
        }
}

data class PendingToolCall(
    val toolJson: String,
    val toolName: String,
    val description: String
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val sources: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingDao: EmbeddingDao,
    val transcriptDao: TranscriptDao,
    val recordingDao: RecordingDao,
    private val chatMessageDao: ChatMessageDao,
    private val systemPromptDao: SystemPromptDao,
    private val phoneContextDao: PhoneContextDao,
    private val modelPaths: ModelPaths,
    private val llmEngine: LocalLlmEngine,
    private val toolExecutor: ToolExecutor
) : ViewModel() {
    private val llm = llmEngine as? LlamaCppEngine
    private val cloudApi = NvidiaApiClient()
    private var generationJob: Job? = null

    private val _state = MutableStateFlow(
        ChatState(llmReady = modelPaths.gemma3nQ4.exists())
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            chatMessageDao.observeAll().collect { stored ->
                _state.update {
                    it.copy(
                        messages = stored.map { row ->
                            ChatMessage(
                                text = row.text,
                                isUser = row.isUser,
                                timestamp = row.createdAt
                            )
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            recordingDao.countAll().collect { count ->
                _state.update {
                    it.copy(
                        totalMemories = count,
                        llmReady = modelPaths.gemma3nQ4.exists()
                    )
                }
            }
        }

        if (llm != null) {
            viewModelScope.launch {
                llm.loadState.collect { loadState ->
                    _state.update {
                        it.copy(
                            llmLoadState = loadState,
                            llmReady = modelPaths.gemma3nQ4.exists()
                        )
                    }
                }
            }
            viewModelScope.launch {
                llm.metrics.collect { metrics ->
                    _state.update { it.copy(modelMetrics = metrics) }
                }
            }
        }

        // Load active system prompt
        viewModelScope.launch {
            systemPromptDao.observeActive().collect { prompt ->
                _state.update { it.copy(activeSystemPrompt = prompt?.content ?: "") }
            }
        }

        // Load memory nodes for brain graph
        viewModelScope.launch {
            recordingDao.allRecordings().collect { recordings ->
                val nodes = recordings.map { rec ->
                    MemoryNode(
                        id = rec.id,
                        label = rec.textContent?.take(30)
                            ?: "Memory #${rec.id}",
                        type = when (rec.inputType) { "text" -> "text"; "photo" -> "photo"; else -> "audio" }
                    )
                }
                _state.update { it.copy(memoryNodes = nodes) }
            }
        }
    }

    fun updateInput(text: String) {
        _state.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun toggleModelSource() {
        _state.update { it.copy(useCloudModel = !it.useCloudModel, errorMessage = null) }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _state.update { it.copy(loading = false, loadingText = "", errorMessage = "Generation stopped") }
    }

    fun sendMessage() {
        val query = _state.value.inputText.trim()
        if (query.isBlank()) return

        val snapshot = _state.value
        if (!snapshot.useCloudModel && snapshot.llmReady && snapshot.llmLoadState != LlmLoadState.Ready) {
            _state.update {
                it.copy(
                    errorMessage = when (snapshot.llmLoadState) {
                        LlmLoadState.Loading -> "Gemma is still loading. Wait for the green ready indicator."
                        LlmLoadState.Failed -> "Gemma failed to load. Restart the app."
                        else -> "Gemma model is not ready yet. Switch to Cloud or load the model."
                    }
                )
            }
            return
        }

        viewModelScope.launch {
            chatMessageDao.insert(ChatMessageEntity(text = query, isUser = true))
        }

        _state.update {
            it.copy(inputText = "", loading = true, loadingText = "Searching memories...", errorMessage = null)
        }

        generationJob = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                // Search memories + contacts in parallel
                val contextDeferred = async(Dispatchers.IO) {
                    val t1 = System.currentTimeMillis()
                    val result = try {
                        searchMemories(query)
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "Memory search failed", e)
                        ""
                    }
                    Log.i("ChatViewModel", "searchMemories took ${System.currentTimeMillis() - t1}ms, result=${result.length} chars")
                    result
                }

                val contactsDeferred = async(Dispatchers.IO) {
                    try {
                        searchContactsForQuery(query)
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "Contact search failed", e)
                        ""
                    }
                }

                // While search runs, update UI
                _state.update { it.copy(loadingText = "Searching memories...") }

                val memoryContext = contextDeferred.await()
                val contactsContext = contactsDeferred.await()

                val context = buildString {
                    if (memoryContext.isNotEmpty()) append(memoryContext)
                    if (contactsContext.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append(contactsContext)
                    }
                }
                _state.update { it.copy(lastContext = context, lastContextLen = context.length) }

                val response: String
                if (_state.value.useCloudModel) {
                    _state.update { it.copy(loadingText = "Cloud AI generating...", streamingMessage = "", streamingTokens = 0, streamingStartedAt = System.currentTimeMillis()) }
                    val t2 = System.currentTimeMillis()
                    response = generateWithCloudStreaming(query, context)
                    Log.i("ChatViewModel", "Cloud generation took ${System.currentTimeMillis() - t2}ms")
                } else if (_state.value.llmReady) {
                    _state.update { it.copy(loadingText = "Gemma is thinking...", streamingMessage = "", streamingTokens = 0, streamingStartedAt = System.currentTimeMillis()) }
                    val t2 = System.currentTimeMillis()
                    response = generateWithLlm(query, context)
                    Log.i("ChatViewModel", "Local generation took ${System.currentTimeMillis() - t2}ms")
                } else if (context.isNotEmpty()) {
                    response = context
                } else {
                    response = "No matching memories found. Add voice/text notes on the Home tab, or switch to Cloud model for general chat."
                }

                // Check for tool calls in response
                val (cleanResponse, hasTool) = extractAndQueueToolCall(response)
                chatMessageDao.insert(ChatMessageEntity(text = cleanResponse, isUser = false))
                Log.i("ChatViewModel", "Total sendMessage took ${System.currentTimeMillis() - t0}ms, tool=$hasTool")
                _state.update { it.copy(loading = false, loadingText = "", streamingMessage = "") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(loading = false, loadingText = "") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, loadingText = "", errorMessage = e.message ?: "Generation failed")
                }
            }
        }
    }

    private suspend fun searchMemories(query: String): String = withContext(Dispatchers.IO) {
        val results = LinkedHashSet<Long>() // recording IDs to avoid duplicates
        val dateFormat = SimpleDateFormat("EEEE, MMM d yyyy 'at' h:mm a", Locale.getDefault())
        val queryLower = query.lowercase()
        val keywords = queryLower.split(" ").filter { it.length > 2 }

        // 1. Embedding search
        if (modelPaths.miniLm.exists() && modelPaths.miniLmVocab.exists()) {
            try {
                val queryVector = embeddingEngine.embed(query)
                val allEmbeddings = embeddingDao.all()
                if (allEmbeddings.isNotEmpty()) {
                    allEmbeddings.map { emb -> emb to cosineSimilarity(queryVector, emb.vector) }
                        .filter { it.second > 0.15f }
                        .sortedByDescending { it.second }
                        .take(10)
                        .forEach { (emb, _) -> results.add(emb.recordingId) }
                }
            } catch (e: Exception) { Log.w("ChatViewModel", "Embedding search failed", e) }
        }

        // 2. Keyword search across all recordings
        try {
            recordingDao.allRecordingsSnapshot().forEach { rec ->
                if (results.size >= 10) return@forEach
                val matchText = rec.textContent ?: ""
                if (matchText.isNotBlank() && keywords.any { matchText.lowercase().contains(it) }) {
                    results.add(rec.id)
                }
            }
        } catch (e: Exception) { Log.w("ChatViewModel", "Text search failed", e) }

        // 3. Transcript search
        try {
            transcriptDao.allTranscripts().forEach { t ->
                if (results.size >= 10) return@forEach
                if (keywords.any { t.transcript.lowercase().contains(it) }) {
                    results.add(t.recordingId)
                }
            }
        } catch (e: Exception) { Log.w("ChatViewModel", "Transcript search failed", e) }

        // 4. Fallback: recent memories
        if (results.isEmpty()) {
            recordingDao.allRecordingsSnapshot().take(10).forEach { results.add(it.id) }
        }

        // Build rich context with full metadata
        val ids = results.take(10)
        recordingDao.allRecordingsSnapshot()
            .filter { it.id in ids }
            .sortedByDescending { it.createdAt }
            .mapNotNull { rec ->
                val transcript = transcriptDao.getByRecordingId(rec.id).firstOrNull()?.transcript
                val content = transcript ?: rec.textContent ?: return@mapNotNull null
                buildString {
                    append("--- Memory #${rec.id} ---\n")
                    append("Type: ${rec.inputType}\n")
                    append("Content: $content\n")
                    append("Created: ${dateFormat.format(Date(rec.createdAt))}\n")
                    if (rec.inputType == "audio") append("Duration: ${rec.durationMs / 1000}s\n")
                    if (rec.latitude != null && rec.longitude != null)
                        append("Location: ${rec.latitude}, ${rec.longitude}\n")
                    if (rec.filePath.isNotBlank()) append("File: ${rec.filePath}\n")
                }
            }
            .joinToString("\n\n")
    }

    /** Search contacts for names mentioned in the query — extracts name after trigger words like "call"/"text" */
    private suspend fun searchContactsForQuery(query: String): String {
        val queryLower = query.lowercase()
        val contactTriggers = listOf("call", "text", "message", "contact", "dial", "phone", "sms", "ring")
        val isContactQuery = contactTriggers.any { queryLower.contains(it) }

        // Extract name candidates: words after a contact trigger (e.g. "call John Smith" → ["John", "Smith"])
        val words = query.split("\\s+".toRegex()).filter { it.length > 1 }
        val nameCandidates = mutableListOf<String>()
        val stopWords = setOf("about", "for", "to", "the", "a", "at", "on", "in", "and", "or", "with", "from", "please", "now", "later", "today", "tomorrow")

        for (i in words.indices) {
            if (contactTriggers.any { words[i].equals(it, ignoreCase = true) }) {
                for (j in (i + 1) until words.size) {
                    val word = words[j]
                    if (word.lowercase() in stopWords) break
                    nameCandidates.add(word.replace(Regex("[^a-zA-Z]"), ""))
                }
            }
        }

        // Fallback: proper-looking capitalized words
        val commonWords = setOf("I", "I'm", "I'll", "I've", "The", "A", "This", "That", "What", "Who", "How", "When", "Where", "Why", "Can", "Could", "Would", "Will", "Is", "Are", "Was", "Were", "Do", "Does", "Did", "Show", "Tell", "Find", "Get", "Search", "Look", "Open", "Please", "Hey", "Hi", "Hello", "OK", "Okay", "Yes", "No", "Maybe", "Need", "Want", "Going", "Just", "Like", "Know", "Think", "Really", "Still")
        if (nameCandidates.isEmpty()) {
            words.filter { word ->
                word.firstOrNull()?.isUpperCase() == true && word.length > 1 && word !in commonWords
            }.forEach { nameCandidates.add(it) }
        }

        // If it's a contact query but no name found, use any non-common word
        if (isContactQuery && nameCandidates.isEmpty()) {
            words.filter { it.length > 2 && it !in commonWords && it !in contactTriggers && it !in stopWords }
                .take(2).forEach { nameCandidates.add(it) }
        }

        if (!isContactQuery && nameCandidates.isEmpty()) return ""

        // Check runtime permission before querying system contacts
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w("ChatViewModel", "READ_CONTACTS not granted — trying local DB contacts instead")
            // Fall back to locally-imported contacts from Room DB
            val localMsg = searchLocalContacts(nameCandidates, isContactQuery)
            if (localMsg.isNotEmpty()) return localMsg
            return "--- Contact Search ---\nREAD_CONTACTS permission needed to search phone contacts. Grant it in Settings > Apps > KBot > Permissions, or use 'Import contacts' in the Settings tab.\n"
        }

        val contacts = mutableListOf<Pair<String, String>>()
        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER),
                null, null, "display_name ASC LIMIT 50"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (cursor.getInt(hasPhoneIdx) > 0) {
                        val contactId = cursor.getString(idIdx)
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId), null
                        )?.use { phoneCursor ->
                            val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (phoneCursor.moveToNext()) {
                                contacts.add(name to phoneCursor.getString(numIdx))
                            }
                        }
                    }
                }
            }
            Log.i("ChatViewModel", "Contact search: query=\"$query\", candidates=${nameCandidates}, found=${contacts.size} contacts")
        } catch (e: SecurityException) {
            Log.w("ChatViewModel", "Contact search: READ_CONTACTS denied at runtime")
            return ""
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Contact search failed: ${e.message}", e)
            return ""
        }

        if (contacts.isEmpty()) {
            Log.i("ChatViewModel", "Contact search: no contacts on device")
            return ""
        }

        // Filter by name candidates — match whole name parts
        val filtered = if (nameCandidates.isNotEmpty()) {
            contacts.filter { (name, _) ->
                val nameParts = name.split(" ").map { it.lowercase() }
                nameCandidates.any { cand ->
                    nameParts.any { part -> part == cand.lowercase() || part.startsWith(cand.lowercase()) }
                }
            }
        } else if (isContactQuery) {
            emptyList()
        } else {
            emptyList()
        }

        if (filtered.isEmpty()) {
            Log.i("ChatViewModel", "Contact search: no match for '${nameCandidates.joinToString(" ")}' among ${contacts.size} contacts")
            if (isContactQuery) {
                return "--- Contact Search ---\nNo contact matched \"${nameCandidates.joinToString(" ")}\". Use get_contacts tool to browse all contacts.\n"
            }
            return ""
        }

        Log.i("ChatViewModel", "Contact search: returning ${filtered.size} matches for '${nameCandidates.joinToString(" ")}'")
        return buildString {
            append("--- Matching Contacts ---\n")
            filtered.take(10).forEach { (name, number) ->
                append("$name: $number\n")
            }
            append("Use call_contact or send_message tool with the exact name and number above.\n")
        }
    }

    /** Search locally-imported contacts from Room DB (fallback when READ_CONTACTS not granted) */
    private suspend fun searchLocalContacts(nameCandidates: List<String>, isContactQuery: Boolean): String {
        try {
            val allLocal = phoneContextDao.allContacts()
            if (allLocal.isEmpty()) return ""

            val filtered = if (nameCandidates.isNotEmpty()) {
                allLocal.filter { contact ->
                    val nameParts = contact.displayName.split(" ").map { it.lowercase() }
                    nameCandidates.any { cand ->
                        nameParts.any { part -> part == cand.lowercase() || part.startsWith(cand.lowercase()) }
                    }
                }
            } else if (isContactQuery) {
                allLocal
            } else {
                emptyList()
            }

            if (filtered.isEmpty()) return ""

            Log.i("ChatViewModel", "Local contacts: returning ${filtered.size} matches")
            return buildString {
                append("--- Matching Contacts (locally stored) ---\n")
                filtered.take(10).forEach { contact ->
                    append("${contact.displayName} (use get_contacts tool for phone number)\n")
                }
                append("Use call_contact or send_message tool with the exact name. The tool will look up the number.\n")
            }
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Local contact search failed: ${e.message}")
            return ""
        }
    }

    private suspend fun generateWithLlm(query: String, context: String): String = withContext(Dispatchers.IO) {
        val sysPrompt = _state.value.activeSystemPrompt.ifBlank {
            "You are a helpful assistant. Be concise."
        }
        val chatHistory = buildChatHistory()
        val systemPrompt = buildString {
            append(sysPrompt)
            append("\n\nTools: show_notification(title,content), set_alarm(time,label), call_contact(name,number), send_message(name,text), get_call_log(limit), get_contacts(). Use when relevant.")
            if (context.isNotEmpty()) {
                append("\n\nMemories:\n")
                append(context)
            }
        }
        val userMessage = buildString {
            if (chatHistory.isNotEmpty()) {
                append("Previous conversation:\n")
                append(chatHistory)
                append("\n\n")
            }
            if (context.isNotEmpty())
                append("Question: $query\n\nAnswer based on the memories above and conversation. If memories don't contain the answer, say so.")
            else
                append(query)
        }

        _state.update { it.copy(fullSystemPrompt = systemPrompt, fullUserMessage = userMessage) }

        try { llm?.chat(systemPrompt, userMessage) ?: llmEngine.summarize(query) }
        catch (e: Exception) { "[LLM error: ${e.message}]" }
    }

    private suspend fun generateWithCloudStreaming(query: String, context: String): String = withContext(Dispatchers.IO) {
        val sysPrompt = _state.value.activeSystemPrompt.ifBlank {
            "You are a helpful assistant. Be concise."
        }
        val chatHistory = buildChatHistory()
        val systemPrompt = buildString {
            append(sysPrompt)
            append("\n\nYou have access to these tools. When user asks for something a tool can do, output ONLY a JSON tool call with no other text:")
            append("\n- show_notification: {\"name\":\"show_notification\",\"parameters\":{\"title\":\"Title\",\"content\":\"Body text\"}} — Shows a notification NOW")
            append("\n- set_alarm: {\"name\":\"set_alarm\",\"parameters\":{\"time\":\"HH:MM\",\"label\":\"reminder\"}} — Sets a future alarm")
            append("\n- call_contact: {\"name\":\"call_contact\",\"parameters\":{\"contact_name\":\"Name\",\"phone_number\":\"1234\"}} — Opens dialer")
            append("\n- send_message: {\"name\":\"send_message\",\"parameters\":{\"contact_name\":\"Name\",\"message_content\":\"text\"}} — Opens SMS")
            append("\n- get_call_log: {\"name\":\"get_call_log\",\"parameters\":{\"limit\":5}} — Reads recent calls")
            append("\n- get_contacts: {\"name\":\"get_contacts\",\"parameters\":{}} — Lists contacts")
            append("\n\nIf user asks 'what can you do' or 'tools', list these 6 tools with brief descriptions.")
            if (context.isNotEmpty()) {
                append("\n\nUser's stored memories (with metadata):\n")
                append(context)
            }
        }
        val userMessage = buildString {
            if (chatHistory.isNotEmpty()) {
                append("Previous conversation:\n")
                append(chatHistory)
                append("\n\n")
            }
            if (context.isNotEmpty())
                append("Question: $query\n\nAnswer based on the memories above and conversation.")
            else
                append(query)
        }

        _state.update { it.copy(fullSystemPrompt = systemPrompt, fullUserMessage = userMessage) }

        val fullResponse = StringBuilder()
        try {
            cloudApi.chatStream(systemPrompt, userMessage).collect { token ->
                if (fullResponse.isEmpty() && token.isBlank()) return@collect
                fullResponse.append(token)
                _state.update {
                    it.copy(
                        streamingMessage = fullResponse.toString().trimStart(),
                        streamingTokens = it.streamingTokens + 1,
                        loadingText = "Generating... ${it.streamingTokens + 1}t @ %.0f t/s".format(it.tokensPerSecond)
                    )
                }
                // 30ms delay between tokens for visible streaming effect
                delay(30)
            }
        } catch (e: Exception) {
            if (fullResponse.isEmpty()) return@withContext "[Cloud API error: ${e.message}]"
        }
        fullResponse.toString().trim()
    }

    private suspend fun generateWithCloud(query: String, context: String): String = generateWithCloudStreaming(query, context)

    /** Extract tool call JSON from response, queue for user approval, return clean text */
    private fun extractAndQueueToolCall(response: String): Pair<String, Boolean> {
        val toolNames = mapOf(
            "show_notification" to "Show a notification",
            "set_alarm" to "Set an alarm",
            "call_contact" to "Call a contact",
            "send_message" to "Send a message",
            "get_call_log" to "Read call log",
            "get_contacts" to "Read contacts"
        )
        // Remove markdown code fences if present
        var cleaned = response
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")

        for ((name, desc) in toolNames) {
            val idx = cleaned.indexOf("\"$name\"")
            if (idx >= 0) {
                // Walk backwards to find the outermost '{' that encloses this tool name
                var start = -1
                for (i in idx downTo 0) {
                    if (cleaned[i] == '{') { start = i; break }
                }
                if (start < 0) continue

                // Walk forward from start, counting brace depth to find the matching '}'
                var depth = 0
                var end = start
                for (i in start until cleaned.length) {
                    when (cleaned[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) { end = i + 1; break }
                        }
                    }
                }
                if (depth != 0) end = cleaned.length // unbalanced braces fallback

                if (end > start) {
                    val json = cleaned.substring(start, end).trim()
                    // Validate it's parseable
                    try {
                        org.json.JSONObject(json)
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "Tool JSON parse failed: $json", e)
                        continue
                    }
                    val cleanText = (cleaned.substring(0, start) + cleaned.substring(end)).trim()
                        .replace(Regex("\\s+"), " ")
                    _state.update { it.copy(pendingToolCall = PendingToolCall(json, name, desc)) }
                    return Pair(cleanText.ifBlank { "I'll help with that." }, true)
                }
            }
        }
        return Pair(cleaned, false)
    }

    fun approveToolCall() {
        val pending = _state.value.pendingToolCall ?: return
        viewModelScope.launch {
            val result = toolExecutor.execute(pending.toolJson)
            val msg = when (pending.toolName) {
                "show_notification" -> "Notification shown: $result"
                "set_alarm" -> "Alarm set: $result"
                "call_contact" -> "Dialer opened: $result"
                "send_message" -> "SMS ready: $result"
                "get_call_log" -> "Call log:\n$result"
                "get_contacts" -> "Contacts:\n$result"
                else -> result
            }
            chatMessageDao.insert(ChatMessageEntity(text = msg, isUser = false))
            _state.update { it.copy(pendingToolCall = null) }
        }
    }

    fun denyToolCall() {
        val pending = _state.value.pendingToolCall ?: return
        viewModelScope.launch {
            chatMessageDao.insert(ChatMessageEntity(text = "Denied: ${pending.description}", isUser = false))
            _state.update { it.copy(pendingToolCall = null) }
        }
    }

    private fun buildChatHistory(): String {
        val allMsgs = _state.value.messages
        if (allMsgs.isEmpty()) return ""
        // Take last 10 messages as context
        return allMsgs.takeLast(10).joinToString("\n") { msg ->
            if (msg.isUser) "User: ${msg.text}" else "Assistant: ${msg.text}"
        }
    }

    fun clearChat() {
        viewModelScope.launch { chatMessageDao.deleteAll() }
        _state.update { it.copy(errorMessage = null) }
    }

    fun setAssistantNickname(name: String) {
        _state.update { it.copy(assistantNickname = name.ifBlank { "KBot" }) }
    }

    fun loadModel() {
        if (llm == null) return
        viewModelScope.launch { llm.load() }
    }

    fun updateSystemPrompt(newPrompt: String) {
        viewModelScope.launch {
            systemPromptDao.deactivateAll()
            val existing = systemPromptDao.getActiveNow()
            if (existing != null) {
                systemPromptDao.update(existing.copy(content = newPrompt))
            } else {
                systemPromptDao.insert(
                    com.offlinebot.data.database.entities.SystemPromptEntity(
                        name = "System Prompt", content = newPrompt, isActive = true
                    )
                )
            }
            _state.update { it.copy(activeSystemPrompt = newPrompt) }
        }
    }
}
