package com.offlinebot.ai.cloud

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NvidiaApiClient(
    private val apiKey: String = "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",
    private val model: String = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning"
) {
    fun chatStream(systemPrompt: String, userMessage: String): Flow<String> = flow {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.6)
            put("top_p", 0.95)
            put("max_tokens", 16384)
            put("chat_template_kwargs", JSONObject().apply {
                put("enable_thinking", false)
            })
            put("stream", true)
        }

        val url = URL("https://integrate.api.nvidia.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.doInput = true
        connection.useCaches = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 20_000

        try {
            val body = payload.toString().toByteArray()
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { os -> os.write(body); os.flush() }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                emit("[Cloud API error $responseCode: $errorBody]")
                return@flow
            }

            connection.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                            val content = delta?.optString("content") ?: ""
                            if (content.isNotEmpty()) emit(content)
                        } catch (_: Exception) {}
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud API call failed", e)
            emit("[Cloud API error: ${e.message}]")
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun chatBlocking(systemPrompt: String, userMessage: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.6)
            put("top_p", 0.95)
            put("max_tokens", 16384)
            put("chat_template_kwargs", JSONObject().apply {
                put("enable_thinking", false)
            })
            put("stream", false)
        }

        val url = URL("https://integrate.api.nvidia.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.doInput = true
        connection.useCaches = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 20_000

        return try {
            val body = payload.toString().toByteArray()
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { os -> os.write(body); os.flush() }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                return "[Cloud API error $responseCode: $errorBody]"
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: "[Cloud API returned empty response]"
        } catch (e: Exception) {
            Log.e(TAG, "Cloud API call failed", e)
            "[Cloud API error: ${e.message}]"
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "NvidiaApiClient"
    }
}
