package com.offlinebot.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.TranscriptDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MemoryNode(
    val id: Long,
    val label: String,
    val type: String
)

private data class SimNode(
    val id: Long,
    val label: String,
    val type: String,
    val createdAt: Long = 0L,
    var x: Float = 0f, var y: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f
) {
    val clusterKey: String
        get() {
            val df = SimpleDateFormat("MMM d", Locale.getDefault())
            val date = if (createdAt > 0) df.format(Date(createdAt)) else "?"
            return "$date - $type"
        }
}

private data class SimEdge(val source: Long, val target: Long)

private val typeColors = mapOf(
    "audio" to Color(0xFFFF6B6B),
    "text" to Color(0xFF4ECDC4),
    "photo" to Color(0xFFFFE66D),
    "note" to Color(0xFFA78BFA)
)

@Composable
fun MemoryBrainGraph(
    recordingDao: RecordingDao,
    transcriptDao: TranscriptDao,
    modifier: Modifier = Modifier
) {
    var nodes by remember { mutableStateOf<List<SimNode>>(emptyList()) }
    var edges by remember { mutableStateOf<List<SimEdge>>(emptyList()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var hoveredNode by remember { mutableStateOf<SimNode?>(null) }
    var simActive by remember { mutableStateOf(true) }

    // Load data
    LaunchedEffect(Unit) {
        val recordings = runBlocking { recordingDao.allRecordingsSnapshot() }
        val seen = mutableSetOf<Long>()
        val simNodes = recordings.mapNotNull { rec ->
            if (seen.contains(rec.id)) return@mapNotNull null
            seen.add(rec.id)
            val label = rec.textContent?.take(25)
                ?: runBlocking {
                    transcriptDao.getByRecordingId(rec.id).firstOrNull()?.transcript?.take(25)
                } ?: "Memory #${rec.id}"
            SimNode(rec.id, label, when (rec.inputType) { "text" -> "text"; "photo" -> "photo"; else -> "audio" }, rec.createdAt)
        }
        val sorted = recordings.sortedBy { it.createdAt }
        val simEdges = mutableListOf<SimEdge>()
        for (i in 0 until sorted.size - 1) {
            if (sorted[i + 1].createdAt - sorted[i].createdAt < 3600_000) {
                simEdges.add(SimEdge(sorted[i].id, sorted[i + 1].id))
            }
        }
        nodes = simNodes
        edges = simEdges
    }

    // Physics simulation — run only for initial 3s, then freeze until user interaction
    LaunchedEffect(nodes.size) {
        if (nodes.isEmpty()) return@LaunchedEffect
        val w = 800f; val h = 1200f; val cx = w / 2; val cy = h / 2

        // Init positions
        nodes.forEach { it.x = cx + Random.nextFloat() * 200 - 100; it.y = cy + Random.nextFloat() * 200 - 100 }

        val startTime = System.currentTimeMillis()
        while (true) {
            // Freeze after 3 seconds unless user is interacting
            if (System.currentTimeMillis() - startTime > 3000) {
                simActive = false
            }
            if (!simActive) {
                delay(200) // Sleep when frozen, check for reactivation
                continue
            }
            for (n in nodes) {
                n.vx += (cx - n.x) * 0.001f
                n.vy += (cy - n.y) * 0.001f
                for (m in nodes) {
                    if (n === m) continue
                    val dx = n.x - m.x; val dy = n.y - m.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    if (dist < 300f) {
                        val f = 8000f / (dist * dist)
                        n.vx += (dx / dist) * f; n.vy += (dy / dist) * f
                    }
                }
            }
            for (e in edges) {
                val s = nodes.find { it.id == e.source } ?: continue
                val t = nodes.find { it.id == e.target } ?: continue
                val dx = t.x - s.x; val dy = t.y - s.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                if (dist > 80f) {
                    val f = (dist - 80f) * 0.0005f
                    s.vx += dx * f; s.vy += dy * f
                    t.vx -= dx * f; t.vy -= dy * f
                }
            }
            for (n in nodes) {
                n.vx *= 0.9f; n.vy *= 0.9f
                n.x += n.vx; n.y += n.vy
                n.x = n.x.coerceIn(40f, w - 40f)
                n.y = n.y.coerceIn(40f, h - 40f)
            }
            delay(16) // ~60fps when active
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D1117)), contentAlignment = Alignment.Center) {
        if (nodes.isEmpty()) {
            Text("No memories yet. Start recording!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(32.dp))
        } else {
            Canvas(modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        simActive = true // Resume simulation on interaction
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
                        offsetX += pan.x; offsetY += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        simActive = true // Resume simulation on tap
                        val sx = (tapOffset.x - offsetX) / scale
                        val sy = (tapOffset.y - offsetY) / scale
                        hoveredNode = nodes.find {
                            val dx = it.x - sx; val dy = it.y - sy
                            dx * dx + dy * dy < 900f
                        }
                    }
                }
            ) {
                val canvasW = size.width; val canvasH = size.height
                val baseOx = (canvasW / 2f - 400f * scale) + offsetX
                val baseOy = (canvasH / 2f - 600f * scale) + offsetY

                fun nodeToScreen(n: SimNode): Offset = Offset(baseOx + n.x * scale, baseOy + n.y * scale)

                val clustering = scale < 0.7f && nodes.size > 3

                if (clustering) {
                    // Clustered view: group by pre-computed clusterKey
                    val clusters = nodes.groupBy { it.clusterKey }

                    clusters.forEach { (key, clusterNodes) ->
                        val avgX = clusterNodes.map { nodeToScreen(it).x }.average().toFloat()
                        val avgY = clusterNodes.map { nodeToScreen(it).y }.average().toFloat()
                        val color = typeColors[clusterNodes.first().type] ?: Color(0xFF6B7280)
                        val r = (24f + clusterNodes.size * 3f) * scale

                        drawCircle(Color(0xFF1F2937).copy(alpha = 0.5f), radius = r + 6f, center = Offset(avgX, avgY))
                        drawCircle(color.copy(alpha = 0.2f), radius = r + 2f, center = Offset(avgX, avgY))
                        drawCircle(color.copy(alpha = 0.7f), radius = r, center = Offset(avgX, avgY))
                        drawCircle(color, radius = r, center = Offset(avgX, avgY), style = Stroke(2f * scale))

                        val label = "$key (${clusterNodes.size})"
                        val lp = android.graphics.Paint().also { p ->
                            p.color = android.graphics.Color.WHITE
                            p.textSize = (12f * scale).coerceIn(10f, 20f)
                            p.textAlign = android.graphics.Paint.Align.CENTER
                            p.isFakeBoldText = true
                            p.isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(label, avgX, avgY + r + 16f * scale, lp)
                    }

                    // Also draw edges between clusters
                    for (e in edges) {
                        val s = nodes.find { it.id == e.source } ?: continue
                        val t = nodes.find { it.id == e.target } ?: continue
                        drawLine(Color(0x30707070), nodeToScreen(s), nodeToScreen(t), strokeWidth = 1f * scale)
                    }
                } else {
                    // Individual node view
                    for (e in edges) {
                        val s = nodes.find { it.id == e.source } ?: continue
                        val t = nodes.find { it.id == e.target } ?: continue
                        drawLine(Color(0x40707070), nodeToScreen(s), nodeToScreen(t), strokeWidth = 1.5f * scale)
                    }

                    for (n in nodes) {
                        val pos = nodeToScreen(n)
                        val color = typeColors[n.type] ?: Color(0xFF6B7280)
                        val r = 18f * scale
                        val isHovered = hoveredNode == n

                        drawCircle(color.copy(alpha = 0.15f), radius = r * 2.5f, center = pos)
                        drawCircle(if (isHovered) color else color.copy(alpha = 0.85f), radius = r, center = pos)
                        drawCircle(color, radius = r, center = pos, style = Stroke(width = 2f * scale))
                        drawCircle(Color.White, radius = 4f * scale, center = pos)

                        val maxLen = 12
                        val lbl = n.label.take(maxLen) + if (n.label.length > maxLen) ".." else ""
                        val textSize = (11f * scale).coerceIn(9f, 22f)
                        val labelY = pos.y + r + 14f * scale

                        val bgPaint = android.graphics.Paint().also { p ->
                            p.color = android.graphics.Color.parseColor("#1F2937")
                            p.isAntiAlias = true
                            p.style = android.graphics.Paint.Style.FILL
                        }
                        val textWidth = 80f * scale
                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            pos.x - textWidth / 2, labelY - 8f, pos.x + textWidth / 2, labelY + 16f,
                            6f, 6f, bgPaint
                        )

                        val labelPaint = android.graphics.Paint().also { p ->
                            p.color = android.graphics.Color.parseColor("#FFFFFF")
                            p.textSize = textSize
                            p.textAlign = android.graphics.Paint.Align.CENTER
                            p.isAntiAlias = true
                            p.isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(lbl, pos.x, labelY + 4f, labelPaint)
                    }

                    hoveredNode?.let { n ->
                        val pos = nodeToScreen(n)
                        val tipPaint = android.graphics.Paint().also { p ->
                            p.color = android.graphics.Color.parseColor("#1F2937")
                            p.textSize = 11f * scale * 2f
                            p.textAlign = android.graphics.Paint.Align.CENTER
                            p.isAntiAlias = true
                            p.isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(n.label, pos.x, pos.y - 32f * scale, tipPaint)
                    }
                }
            }
        }
    }
}
