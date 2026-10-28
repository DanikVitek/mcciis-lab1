package me.danikvitek.lab1.domain.model

import androidx.compose.ui.geometry.Offset
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlin.math.*
import kotlin.random.Random

enum class GraphDistribution {
    RANDOM_UNIFORM,
    RING,
    NATURAL_GRID,
}

class CityGraph private constructor(
    val cities: List<Offset>,
    roads: MutableRoads,
    val customRoadLengths: Map<Road, Float>? = null,
    validate: Boolean,
) {
    internal constructor(
        cities: List<Offset>,
        roads: MutableRoads,
        customRoadLengths: Map<Road, Float>? = null,
    ) : this(
        cities = cities,
        roads = roads,
        customRoadLengths = customRoadLengths,
        validate = true,
    )

    init {
        if (validate) {
            require(cities.isNotEmpty()) { "Cities list cannot be empty" }
            val n = cities.size
            val hasRoadForCity = BooleanArray(n)
            val packed = roads.packedRoads
            for (i in 0..<roads.size) {
                val road = packed[i]
                val city1 = road.toInt()
                val city2 = (road ushr 32).toInt()
                require(city1 in 0..<n && city2 in 0..<n) {
                    "Roads cannot connect to non-existent cities (road #$i)"
                }
                hasRoadForCity[city1] = true
                hasRoadForCity[city2] = true
            }
            for (city in 0..<n) {
                require(hasRoadForCity[city]) {
                    "No city can be isolated, or be a sink/source of roads (i.e., all cities must have at least one incoming and one outgoing road) (city #$city)"
                }
            }
            // No way to quickly check if the graph is Hamiltonian
        }
    }

    companion object {
        fun fullyConnected(initialPheromoneLevel: Float, cities: List<Offset>): CityGraph {
            val roads = MutableRoads(cities.size, initialPheromoneLevel)
            return CityGraph(cities, roads, customRoadLengths = null, validate = false)
        }

        context(rng: Random)
        fun randomFullyConnected(initialPheromoneLevel: Float, citiesAmount: Int) = fullyConnected(
            initialPheromoneLevel,
            cities = run {
                val initialPositions = (0..<citiesAmount).map {
                    Offset(
                        x = rng.nextFloat(),
                        y = rng.nextFloat(),
                    )
                }
                val minX = initialPositions.minOf { it.x }
                val maxX = initialPositions.maxOf { it.x }
                val minY = initialPositions.minOf { it.y }
                val maxY = initialPositions.maxOf { it.y }
                val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
                val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f
                initialPositions.map { position ->
                    Offset(
                        x = (position.x - minX) / spanX,
                        y = (position.y - minY) / spanY,
                    )
                }
            },
        )

        fun ringFullyConnected(initialPheromoneLevel: Float, citiesAmount: Int) = fullyConnected(
            initialPheromoneLevel,
            cities = run {
                val angle = 2 * Math.PI / citiesAmount
                (0..<citiesAmount).map { i ->
                    Offset(
                        x = 0.5f + 0.5f * cos(i * angle).toFloat(),
                        y = 0.5f + 0.5f * sin(i * angle).toFloat(),
                    )
                }
            },
        )

        context(rng: Random)
        fun naturalGrid(initialPheromoneLevel: Float, citiesAmount: Int): CityGraph {
            require(citiesAmount >= 2) { "Cities amount must be at least 2" }
            if (citiesAmount <= 3) {
                return randomFullyConnected(initialPheromoneLevel, citiesAmount)
            }

            val rows = sqrt(citiesAmount.toDouble()).toInt().coerceAtLeast(2)
            val baseCols = citiesAmount / rows
            val extraCols = citiesAmount % rows
            val colsInRow = IntArray(rows) { r -> baseCols + if (r < extraCols) 1 else 0 }
            val rowOffsets = IntArray(rows)
            for (r in 1..<rows) {
                rowOffsets[r] = rowOffsets[r - 1] + colsInRow[r - 1]
            }

            fun cityIndex(r: Int, c: Int): Int = rowOffsets[r] + c

            val rawPositions = ArrayList<Offset>(citiesAmount)
            for (r in 0..<rows) {
                val cols = colsInRow[r]
                for (c in 0..<cols) {
                    val jitterX = (rng.nextFloat() - 0.5f) * (0.6f / cols)
                    val jitterY = (rng.nextFloat() - 0.5f) * (0.6f / rows)
                    rawPositions.add(
                        Offset(
                            x = (c + 0.5f) / cols + jitterX,
                            y = (r + 0.5f) / rows + jitterY,
                        )
                    )
                }
            }

            val minX = rawPositions.minOf { it.x }
            val maxX = rawPositions.maxOf { it.x }
            val minY = rawPositions.minOf { it.y }
            val maxY = rawPositions.maxOf { it.y }
            val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
            val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f
            val cities = rawPositions.map { pos ->
                Offset(
                    x = (pos.x - minX) / spanX,
                    y = (pos.y - minY) / spanY,
                )
            }

            val roads = MutableRoads()

            // 1. Primary grid connections (horizontal & vertical/diagonal adjacent grid cells)
            for (r in 0..<rows) {
                val cols = colsInRow[r]
                for (c in 0..<cols - 1) {
                    roads.put(cityIndex(r, c), cityIndex(r, c + 1), initialPheromoneLevel)
                }
                if (r + 1 < rows) {
                    val nextCols = colsInRow[r + 1]
                    val maxNormalizedColDiff = 1.35f / minOf(cols, nextCols)
                    for (c1 in 0..<cols) {
                        val u1 = (c1 + 0.5f) / cols
                        for (c2 in 0..<nextCols) {
                            val u2 = (c2 + 0.5f) / nextCols
                            if (abs(u1 - u2) <= maxNormalizedColDiff) {
                                roads.put(cityIndex(r, c1), cityIndex(r + 1, c2), initialPheromoneLevel)
                            }
                        }
                    }
                }
            }

            // 2. Gabriel graph proximity edges (natural planar neighborhood)
            for (i in 0..<citiesAmount) {
                val pi = cities[i]
                for (j in i + 1..<citiesAmount) {
                    if (roads.indexOfCities(i, j) >= 0) continue
                    val pj = cities[j]
                    val dijSq = (pi - pj).getDistanceSquared()
                    var isGabriel = true
                    for (k in 0..<citiesAmount) {
                        if (k == i || k == j) continue
                        val pk = cities[k]
                        if ((pi - pk).getDistanceSquared() + (pj - pk).getDistanceSquared() < dijSq) {
                            isGabriel = false
                            break
                        }
                    }
                    if (isGabriel) {
                        roads.put(i, j, initialPheromoneLevel)
                    }
                }
            }

            // 3. Rare medium/long-range regional connections with steep distance decay
            val cellSpacing = 1f / sqrt(citiesAmount.toFloat())
            for (i in 0..<citiesAmount) {
                val pi = cities[i]
                for (j in i + 1..<citiesAmount) {
                    if (roads.indexOfCities(i, j) >= 0) continue
                    val dist = (pi - cities[j]).getDistance()
                    val normalizedDist = (dist / cellSpacing) - 1f
                    if (normalizedDist > 0f) {
                        val prob = 0.25f * exp(-1.1f * normalizedDist * normalizedDist)
                        if (rng.nextFloat() < prob) {
                            roads.put(i, j, initialPheromoneLevel)
                        }
                    }
                }
            }

            return CityGraph(cities, roads, customRoadLengths = null, validate = false)
        }
    }

    internal val mutableRoads: MutableRoads = roads
    val roads: Roads by this::mutableRoads

    internal val edgesCount: Int
        get() = mutableRoads.size

    internal val edgeDistances = FloatArray(mutableRoads.size)
    internal val edgeInvDistances = FloatArray(mutableRoads.size)
    private val edgeInvDistancesPowBeta = FloatArray(mutableRoads.size)
    private var cachedBeta: Float = Float.NaN
    private val edgeWeights = FloatArray(mutableRoads.size)

    internal val neighborCities: Array<IntArray>
    internal val neighborEdgeIndices: Array<IntArray>
    internal val neighborWeights: Array<FloatArray>

    init {
        val n = cities.size
        val m = mutableRoads.size
        val packed = mutableRoads.packedRoads
        val degrees = IntArray(n)

        for (e in 0..<m) {
            val road = packed[e]
            val c1 = road.toInt()
            val c2 = (road ushr 32).toInt()
            val customDist = customRoadLengths?.get(Road(road))
            val dist = (customDist ?: (cities[c1] - cities[c2]).getDistance()).coerceAtLeast(1e-6f)
            edgeDistances[e] = dist
            edgeInvDistances[e] = 1f / dist
            degrees[c1]++
            degrees[c2]++
        }

        val adjCities = Array(n) { IntArray(degrees[it]) }
        val adjEdges = Array(n) { IntArray(degrees[it]) }
        val adjWeights = Array(n) { FloatArray(degrees[it]) }
        val cursors = IntArray(n)

        for (e in 0..<m) {
            val road = packed[e]
            val c1 = road.toInt()
            val c2 = (road ushr 32).toInt()

            val pos1 = cursors[c1]++
            adjCities[c1][pos1] = c2
            adjEdges[c1][pos1] = e

            val pos2 = cursors[c2]++
            adjCities[c2][pos2] = c1
            adjEdges[c2][pos2] = e
        }

        neighborCities = adjCities
        neighborEdgeIndices = adjEdges
        neighborWeights = adjWeights
    }

    private fun ensureInvDistancesPowBeta(beta: Float): FloatArray {
        val invDistBeta = edgeInvDistancesPowBeta
        if (cachedBeta != beta) {
            val invDist = edgeInvDistances
            when (beta) {
                0f -> invDistBeta.fill(1f)
                1f -> invDist.copyInto(invDistBeta)
                else -> for (e in invDist.indices) {
                    invDistBeta[e] = invDist[e].pow(beta)
                }
            }
            cachedBeta = beta
        }
        return invDistBeta
    }

    /**
     * Precomputes transition weights for all edges and populates per-city adjacency weight arrays once per tick.
     */
    internal fun refreshWeights(weighting: WeightingConfig) {
        val alpha = weighting.alpha
        val invDistBeta = ensureInvDistancesPowBeta(weighting.beta)
        val pheromones = mutableRoads.pheromones
        val weights = edgeWeights
        val m = edgesCount

        when (alpha) {
            0f -> invDistBeta.copyInto(weights, 0, 0, m)
            1f -> for (e in 0..<m) {
                weights[e] = pheromones[e] * invDistBeta[e]
            }
            else -> for (e in 0..<m) {
                weights[e] = pheromones[e].pow(alpha) * invDistBeta[e]
            }
        }

        for (u in cities.indices) {
            val edgeIndices = neighborEdgeIndices[u]
            val cityWeights = neighborWeights[u]
            for (i in edgeIndices.indices) {
                cityWeights[i] = weights[edgeIndices[i]]
            }
        }
    }

    fun hasRoad(city1: Int, city2: Int): Boolean {
        if (city1 == city2) return false
        val fcCount = mutableRoads.fullyConnectedCitiesCount
        if (fcCount > 0) {
            return city1 in 0..<fcCount && city2 in 0..<fcCount
        }
        return mutableRoads.indexOfCities(city1, city2) >= 0
    }

    internal fun edgeIndex(city1: Int, city2: Int): Int =
        mutableRoads.indexOfCities(city1, city2)

    fun distanceBetween(city1: Int, city2: Int): Float =
        edgeDistances[mutableRoads.indexOfCities(city1, city2)]

    /**
     * Validates that [routeCities] forms a complete Hamiltonian cycle on this graph
     * and returns a [CyclicRoute] with the calculated total length, or `null` if invalid.
     */
    fun validateRoute(routeCities: List<Int>): CyclicRoute? {
        val n = cities.size
        if (n < 2 || routeCities.size != n) return null
        val seen = BooleanArray(n)
        for (c in routeCities) {
            if (c !in 0..<n || seen[c]) return null
            seen[c] = true
        }
        var totalLength = 0f
        if (n == 2) {
            val u = routeCities[0]
            val v = routeCities[1]
            if (!hasRoad(u, v)) return null
            totalLength = distanceBetween(u, v) * 2f
        } else {
            for (i in 0..<n) {
                val u = routeCities[i]
                val v = routeCities[(i + 1) % n]
                if (!hasRoad(u, v)) return null
                totalLength += distanceBetween(u, v)
            }
        }
        return CyclicRoute(routeCities, totalLength)
    }

    context(weighting: WeightingConfig)
    fun accessibleCities(from: Int): PersistentMap<Int, Float> {
        val nextCities: PersistentMap<Int, Float> = roads.accessibleCities(from)
        val invDistBeta = ensureInvDistancesPowBeta(weighting.beta)
        val alpha = weighting.alpha
        return nextCities.mutate {
            it.replaceAll { to, pheromone ->
                val edgeIdx = mutableRoads.indexOfCities(from, to)
                val distWeightPowBeta = if (edgeIdx >= 0) {
                    invDistBeta[edgeIdx]
                } else {
                    (1f / (cities[from] - cities[to]).getDistance()).pow(weighting.beta)
                }

                pheromone.pow(alpha) * distWeightPowBeta
            }
        }
    }
}
