package com.offlinebot.ai.embeddings

interface EmbeddingEngine {
    suspend fun embed(text: String): FloatArray
    fun unload()
}
