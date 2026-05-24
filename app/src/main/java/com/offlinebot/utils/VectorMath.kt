package com.offlinebot.utils

import kotlin.math.sqrt

fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
    require(left.size == right.size) { "Vectors must have the same dimensions." }
    var dot = 0f
    var leftNorm = 0f
    var rightNorm = 0f
    for (index in left.indices) {
        dot += left[index] * right[index]
        leftNorm += left[index] * left[index]
        rightNorm += right[index] * right[index]
    }
    if (leftNorm == 0f || rightNorm == 0f) return 0f
    val denominator = sqrt(leftNorm.toDouble()) * sqrt(rightNorm.toDouble())
    return (dot / denominator).toFloat()
}
