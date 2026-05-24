package com.offlinebot.ai.embeddings

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.offlinebot.ai.ModelPaths
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class MiniLmOnnxEmbeddingEngine @Inject constructor(
    private val modelPaths: ModelPaths
) : EmbeddingEngine {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: MiniLmTokenizer? = null

    override suspend fun embed(text: String): FloatArray {
        val activeEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val activeSession = session ?: activeEnv.createSession(modelPaths.miniLm.absolutePath).also { session = it }
        val activeTokenizer = tokenizer ?: MiniLmTokenizer(modelPaths.miniLmVocab).also { tokenizer = it }
        val tokenized = activeTokenizer.encode(text)
        val shape = longArrayOf(1, tokenized.inputIds.size.toLong())

        OnnxTensor.createTensor(activeEnv, LongBuffer.wrap(tokenized.inputIds), shape).use { inputIds ->
            OnnxTensor.createTensor(activeEnv, LongBuffer.wrap(tokenized.attentionMask), shape).use { attentionMask ->
                OnnxTensor.createTensor(activeEnv, LongBuffer.wrap(tokenized.tokenTypeIds), shape).use { tokenTypeIds ->
                    activeSession.run(
                        mapOf(
                            "input_ids" to inputIds,
                            "attention_mask" to attentionMask,
                            "token_type_ids" to tokenTypeIds
                        )
                    ).use { result ->
                        val tokenEmbeddings = result[0].value as Array<Array<FloatArray>>
                        return meanPoolAndNormalize(tokenEmbeddings[0], tokenized.attentionMask)
                    }
                }
            }
        }
    }

    override fun unload() {
        session?.close()
        session = null
        env?.close()
        env = null
        tokenizer = null
    }

    private fun meanPoolAndNormalize(tokens: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val dimensions = tokens.firstOrNull()?.size ?: return FloatArray(0)
        val pooled = FloatArray(dimensions)
        var count = 0f
        tokens.forEachIndexed { index, vector ->
            if (attentionMask.getOrElse(index) { 0L } == 1L) {
                for (dimension in 0 until dimensions) {
                    pooled[dimension] += vector[dimension]
                }
                count += 1f
            }
        }
        if (count == 0f) return pooled
        for (dimension in pooled.indices) {
            pooled[dimension] /= count
        }
        val norm = sqrt(pooled.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (dimension in pooled.indices) {
                pooled[dimension] /= norm
            }
        }
        return pooled
    }
}
