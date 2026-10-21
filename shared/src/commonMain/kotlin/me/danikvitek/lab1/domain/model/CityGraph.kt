package me.danikvitek.lab1.domain.model

import androidx.compose.ui.geometry.Offset
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random


class CityGraph private constructor(
    val cities: List<Offset>,
    roads: MutableRoads,
    validate: Boolean,
) {
    internal constructor(cities: List<Offset>, roads: MutableRoads) : this(
        cities,
        roads,
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
            return CityGraph(cities, roads, validate = false)
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
                initialPositions.map { position ->
                    Offset(
                        x = (position.x - minX) / (maxX - minX),
                        y = (position.y - minY) / (maxY - minY),
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
            val dist = (cities[c1] - cities[c2]).getDistance()
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

    internal fun hasRoad(city1: Int, city2: Int): Boolean {
        if (city1 == city2) return false
        val fcCount = mutableRoads.fullyConnectedCitiesCount
        if (fcCount > 0) {
            return city1 in 0..<fcCount && city2 in 0..<fcCount
        }
        return mutableRoads.indexOfCities(city1, city2) >= 0
    }

    internal fun edgeIndex(city1: Int, city2: Int): Int =
        mutableRoads.indexOfCities(city1, city2)

    internal fun distanceBetween(city1: Int, city2: Int): Float =
        edgeDistances[mutableRoads.indexOfCities(city1, city2)]

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
