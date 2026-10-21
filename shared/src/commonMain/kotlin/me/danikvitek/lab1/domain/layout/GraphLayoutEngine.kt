package me.danikvitek.lab1.domain.layout

import androidx.compose.ui.geometry.Offset
import me.danikvitek.lab1.domain.model.NamedCity
import me.danikvitek.lab1.domain.model.Road
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object GraphLayoutEngine {
    /**
     * Adjusts city positions on the canvas so that Euclidean distances between connected cities
     * reflect the provided road lengths in km as closely as possible.
     */
    fun adjustPositions(
        cities: List<NamedCity>,
        roadLengthsKm: Map<Road, Float>,
        iterations: Int = 200,
    ): List<NamedCity> {
        val n = cities.size
        if (n <= 1 || roadLengthsKm.isEmpty()) return cities

        val maxRoadKm = roadLengthsKm.values.maxOrNull()?.takeIf { it > 0f } ?: return cities

        // All-pairs shortest path (Floyd-Warshall) for target distances
        val inf = Float.POSITIVE_INFINITY
        val distMatrix = Array(n) { i -> FloatArray(n) { j -> if (i == j) 0f else inf } }
        val isDirectEdge = Array(n) { BooleanArray(n) }

        for ((road, km) in roadLengthsKm) {
            val u = road.city1
            val v = road.city2
            if (u in 0..<n && v in 0..<n && km > 0f) {
                val normalized = (km / maxRoadKm).coerceAtLeast(0.05f)
                distMatrix[u][v] = normalized
                distMatrix[v][u] = normalized
                isDirectEdge[u][v] = true
                isDirectEdge[v][u] = true
            }
        }

        for (k in 0..<n) {
            for (i in 0..<n) {
                val dIK = distMatrix[i][k]
                if (dIK == inf) continue
                for (j in 0..<n) {
                    val dKJ = distMatrix[k][j]
                    if (dKJ == inf) continue
                    val via = dIK + dKJ
                    if (via < distMatrix[i][j]) {
                        distMatrix[i][j] = via
                    }
                }
            }
        }

        val posX = FloatArray(n) { cities[it].position.x }
        val posY = FloatArray(n) { cities[it].position.y }

        // Ensure no two cities start at the exact same point
        for (i in 0..<n) {
            for (j in i + 1..<n) {
                val dx = posX[i] - posX[j]
                val dy = posY[i] - posY[j]
                if (dx * dx + dy * dy < 1e-5f) {
                    val angle = (2.0 * Math.PI * j) / n
                    posX[j] = (posX[j] + 0.05f * cos(angle).toFloat()).coerceIn(0.05f, 0.95f)
                    posY[j] = (posY[j] + 0.05f * sin(angle).toFloat()).coerceIn(0.05f, 0.95f)
                }
            }
        }

        // Iterative stress majorization / spring relaxation
        val nextX = FloatArray(n)
        val nextY = FloatArray(n)

        repeat(iterations) { iter ->
            val step = 0.6f * (1f - (iter.toFloat() / iterations) * 0.5f)
            for (i in 0..<n) {
                var sumX = 0f
                var sumY = 0f
                var weightSum = 0f

                for (j in 0..<n) {
                    if (i == j) continue
                    val dx = posX[i] - posX[j]
                    val dy = posY[i] - posY[j]
                    val currentDist = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

                    val targetDist = distMatrix[i][j]
                    if (targetDist.isFinite()) {
                        // Direct edges have much stronger stiffness than indirect shortest-path pairs
                        val weight = if (isDirectEdge[i][j]) {
                            1.0f / (targetDist * targetDist).coerceAtLeast(0.01f)
                        } else {
                            0.15f / (targetDist * targetDist).coerceAtLeast(0.04f)
                        }
                        val idealX = posX[j] + dx * (targetDist / currentDist)
                        val idealY = posY[j] + dy * (targetDist / currentDist)
                        sumX += weight * idealX
                        sumY += weight * idealY
                        weightSum += weight
                    } else if (currentDist < 0.2f) {
                        // Gentle repulsion for disconnected components
                        val weight = 0.05f
                        val idealX = posX[j] + dx * (0.25f / currentDist)
                        val idealY = posY[j] + dy * (0.25f / currentDist)
                        sumX += weight * idealX
                        sumY += weight * idealY
                        weightSum += weight
                    }
                }

                if (weightSum > 0f) {
                    val targetX = sumX / weightSum
                    val targetY = sumY / weightSum
                    nextX[i] = posX[i] + step * (targetX - posX[i])
                    nextY[i] = posY[i] + step * (targetY - posY[i])
                } else {
                    nextX[i] = posX[i]
                    nextY[i] = posY[i]
                }
            }
            for (i in 0..<n) {
                posX[i] = nextX[i]
                posY[i] = nextY[i]
            }
        }

        // Normalize uniformly (preserving aspect ratio so relative distances stay true) into [0.08, 0.92]
        var minX = posX[0]
        var maxX = posX[0]
        var minY = posY[0]
        var maxY = posY[0]
        for (i in 1..<n) {
            if (posX[i] < minX) minX = posX[i]
            if (posX[i] > maxX) maxX = posX[i]
            if (posY[i] < minY) minY = posY[i]
            if (posY[i] > maxY) maxY = posY[i]
        }

        val spanX = maxX - minX
        val spanY = maxY - minY
        val maxSpan = max(spanX, spanY).coerceAtLeast(1e-4f)
        val usableRange = 0.80f // leaves 0.10 margin on each side
        val scale = usableRange / maxSpan

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        return cities.mapIndexed { i, city ->
            city.copy(
                position = Offset(
                    x = (0.5f + (posX[i] - centerX) * scale).coerceIn(0.05f, 0.95f),
                    y = (0.5f + (posY[i] - centerY) * scale).coerceIn(0.05f, 0.95f),
                )
            )
        }
    }
}
