package me.danikvitek.lab1.domain.model

import androidx.compose.ui.geometry.Offset

data class NamedCity(
    val name: String,
    val position: Offset,
)

data class TriangleViolation(
    val cityA: Int,
    val cityB: Int,
    val viaCityC: Int,
    val directLengthKm: Float,
    val indirectLengthKm: Float,
) {
    val cityC: Int
        get() = viaCityC

    val violatingRoad: Road
        get() = Road(cityA, cityB)
}

data class GraphValidationResult(
    val hasEnoughCities: Boolean,
    val isConnected: Boolean,
    val lowDegreeCities: List<Int>,
    val triangleViolations: List<TriangleViolation>,
) {
    val isValidForSimulation: Boolean
        get() = hasEnoughCities && isConnected && lowDegreeCities.isEmpty() && triangleViolations.isEmpty()
}

data class TaskMap(
    val name: String = "Custom Map",
    val cities: List<NamedCity> = emptyList(),
    val roadLengthsKm: Map<Road, Float> = emptyMap(),
    val startCityIndex: Int = 0,
    val savedBestRouteCities: List<Int> = emptyList(),
) {
    fun validate(): GraphValidationResult {
        val n = cities.size
        if (n < 2) {
            return GraphValidationResult(
                hasEnoughCities = false,
                isConnected = false,
                lowDegreeCities = emptyList(),
                triangleViolations = emptyList(),
            )
        }

        val minRequiredDegree = if (n == 2) 1 else 2
        val degrees = IntArray(n)
        val adj = Array(n) { ArrayList<Int>() }
        for ((road, length) in roadLengthsKm) {
            if (length <= 0f) continue
            val u = road.city1
            val v = road.city2
            if (u in 0..<n && v in 0..<n) {
                degrees[u]++
                degrees[v]++
                adj[u].add(v)
                adj[v].add(u)
            }
        }

        val lowDegreeCities = (0..<n).filter { degrees[it] < minRequiredDegree }

        // Check graph connectivity via BFS
        val visited = BooleanArray(n)
        val queue = IntArray(n)
        var head = 0
        var tail = 0
        queue[tail++] = 0
        visited[0] = true
        var visitedCount = 1
        while (head < tail) {
            val u = queue[head++]
            for (v in adj[u]) {
                if (!visited[v]) {
                    visited[v] = true
                    visitedCount++
                    queue[tail++] = v
                }
            }
        }
        val isConnected = visitedCount == n

        // Check triangle inequality on all triangles (i, j, k) where all 3 edges exist
        val violations = mutableListOf<TriangleViolation>()
        val eps = 1e-3f
        for (i in 0..<n) {
            for (j in i + 1..<n) {
                val dIJ = roadLengthsKm[Road(i, j)] ?: continue
                for (k in j + 1..<n) {
                    val dIK = roadLengthsKm[Road(i, k)] ?: continue
                    val dJK = roadLengthsKm[Road(j, k)] ?: continue

                    if (dIJ > dIK + dJK + eps) {
                        violations.add(
                            TriangleViolation(
                                cityA = i,
                                cityB = j,
                                viaCityC = k,
                                directLengthKm = dIJ,
                                indirectLengthKm = dIK + dJK,
                            )
                        )
                    }
                    if (dIK > dIJ + dJK + eps) {
                        violations.add(
                            TriangleViolation(
                                cityA = i,
                                cityB = k,
                                viaCityC = j,
                                directLengthKm = dIK,
                                indirectLengthKm = dIJ + dJK,
                            )
                        )
                    }
                    if (dJK > dIJ + dIK + eps) {
                        violations.add(
                            TriangleViolation(
                                cityA = j,
                                cityB = k,
                                viaCityC = i,
                                directLengthKm = dJK,
                                indirectLengthKm = dIJ + dIK,
                            )
                        )
                    }
                }
            }
        }

        return GraphValidationResult(
            hasEnoughCities = true,
            isConnected = isConnected,
            lowDegreeCities = lowDegreeCities,
            triangleViolations = violations,
        )
    }

    fun toCityGraph(initialPheromoneLevel: Float): CityGraph {
        val mutableRoads = MutableRoads()
        for ((road, _) in roadLengthsKm) {
            mutableRoads.put(road, initialPheromoneLevel)
        }
        return CityGraph(
            cities = cities.map { it.position },
            roads = mutableRoads,
            customRoadLengths = roadLengthsKm,
        )
    }

    fun validateSavedRoute(): CyclicRoute {
        val n = cities.size
        if (n < 2 || savedBestRouteCities.size != n) return CyclicRoute()
        val seen = BooleanArray(n)
        for (c in savedBestRouteCities) {
            if (c !in 0..<n || seen[c]) return CyclicRoute()
            seen[c] = true
        }
        var total = 0f
        if (n == 2) {
            val len = roadLengthsKm[Road(savedBestRouteCities[0], savedBestRouteCities[1])] ?: return CyclicRoute()
            total = len * 2f
        } else {
            for (i in 0..<n) {
                val u = savedBestRouteCities[i]
                val v = savedBestRouteCities[(i + 1) % n]
                val len = roadLengthsKm[Road(u, v)] ?: return CyclicRoute()
                total += len
            }
        }
        return CyclicRoute(savedBestRouteCities, total)
    }
}
