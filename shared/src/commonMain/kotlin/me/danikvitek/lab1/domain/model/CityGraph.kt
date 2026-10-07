package me.danikvitek.lab1.domain.model

import androidx.compose.ui.geometry.Offset
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import java.util.concurrent.ConcurrentHashMap
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
            roads.keys.indexOfFirst { (city1, city2) -> city1 !in cities.indices || city2 !in cities.indices }.let {
                require(it == -1) { "Roads cannot connect to non-existent cities (road #$it)" }
            }
            cities.indices.find {
                !(roads.keys.any { road -> it in road })
            }.let {
                require(it == null) {
                    "No city can be isolated, or be a sink/source of roads (i.e., all cities must have at least one incoming and one outgoing road) (city #$it)"
                }
            }
            // No way to quickly check if the graph is Hamiltonian
        }
    }

    companion object {
        fun fullyConnected(initialPheromoneLevel: Float, cities: List<Offset>): CityGraph {
            val roads = MutableRoads().apply {
                for (i in cities.indices) {
                    for (j in i + 1..cities.lastIndex) {
                        put(i, j, initialPheromoneLevel)
                    }
                }
            }
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

    private val invDistancesCache = ConcurrentHashMap<Road, Float>(roads.size)

    context(weighting: WeightingConfig)
    fun accessibleCities(from: Int): PersistentMap<Int, Float> {
        val nextCities: PersistentMap<Int, Float> = roads.accessibleCities(from)
        val fromPosition: Offset = cities[from]
        return nextCities.mutate {
            it.replaceAll { to, pheromone ->
                val distanceWeight = invDistancesCache.getOrPut(Road(from, to)) {
                    1f / (fromPosition - cities[to]).getDistance()
                }

                pheromone.pow(weighting.alpha) *
                distanceWeight.pow(weighting.beta)
            }
        }
    }
}
