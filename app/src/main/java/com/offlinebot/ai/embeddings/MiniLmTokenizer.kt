package com.offlinebot.ai.embeddings

import java.io.File
import java.util.Locale

class MiniLmTokenizer(private val vocabFile: File) {
    private val vocab: Map<String, Int> by lazy {
        vocabFile.readLines()
            .mapIndexed { index, token -> token to index }
            .toMap()
    }

    fun encode(text: String, maxLength: Int = MAX_LENGTH): TokenizedText {
        val tokens = mutableListOf(CLS)
        basicTokenize(text).forEach { token ->
            tokens += wordPiece(token)
        }
        tokens += SEP

        val trimmed = tokens.take(maxLength).toMutableList()
        if (trimmed.lastOrNull() != SEP) {
            trimmed[trimmed.lastIndex] = SEP
        }

        val inputIds = LongArray(maxLength)
        val attentionMask = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)
        trimmed.forEachIndexed { index, token ->
            inputIds[index] = vocab[token]?.toLong() ?: unknownId
            attentionMask[index] = 1L
            tokenTypeIds[index] = 0L
        }

        return TokenizedText(inputIds, attentionMask, tokenTypeIds)
    }

    private fun basicTokenize(text: String): List<String> =
        text.lowercase(Locale.US)
            .replace(Regex("""([!?,.;:()\[\]"])"""), " $1 ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

    private fun wordPiece(token: String): List<String> {
        if (token.length > MAX_TOKEN_CHARS) return listOf(UNK)
        val pieces = mutableListOf<String>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var current: String? = null
            while (start < end) {
                val piece = token.substring(start, end).let {
                    if (start == 0) it else "##$it"
                }
                if (vocab.containsKey(piece)) {
                    current = piece
                    break
                }
                end -= 1
            }
            if (current == null) return listOf(UNK)
            pieces += current
            start = end
        }
        return pieces
    }

    private val unknownId: Long
        get() = vocab[UNK]?.toLong() ?: 100L

    companion object {
        private const val MAX_LENGTH = 256
        private const val MAX_TOKEN_CHARS = 100
        private const val CLS = "[CLS]"
        private const val SEP = "[SEP]"
        private const val UNK = "[UNK]"
    }
}

data class TokenizedText(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
)
