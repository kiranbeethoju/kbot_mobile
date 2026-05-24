package com.offlinebot.ai.llm

interface LocalLlmEngine {
    suspend fun summarize(prompt: String): String
    suspend fun tag(text: String): List<String>
    fun unload()
}
