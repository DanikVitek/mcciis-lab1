package me.danikvitek.lab1.domain.model

import androidx.compose.ui.geometry.Offset
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
            roads.keys.indexOfFirst { (from, to) -> from == to }.let {
                require(it == -1) { "Roads cannot connect a city to itself (road #$it)" }
            }
            roads.keys.indexOfFirst { (from, to) -> from !in cities.indices || to !in cities.indices }.let {
                require(it == -1) { "Roads cannot connect to non-existent cities (road #$it)" }
            }
            cities.indices.find {
                !(roads.keys.any { (from, to) -> from == it } &&
                  roads.keys.any { (from, to) -> to == it })
            }.let {
                require(it == null) {
                    "No city can be isolated, or be a sink/source of roads (i.e., all cities must have at least one incoming and one outgoing road) (city #$it)"
                }
            }
            // No way to quickly check if the graph is Hamiltonian
        }
    }

    companion object {
        fun fullyConnected(initialPheromoneLevel: Pheromone, cities: List<Offset>): CityGraph {
            val roads = MutableRoads().apply {
                for (i in cities.indices) {
                    for (j in i + 1..cities.lastIndex) {
                        add(i, j, initialPheromoneLevel)
                    }
                }
            }
            return CityGraph(cities, roads, validate = false)
        }

        context(rng: Random)
        fun randomFullyConnected(initialPheromoneLevel: Pheromone, citiesAmount: Int) = fullyConnected(
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

        fun ringFullyConnected(initialPheromoneLevel: Pheromone, citiesAmount: Int) = fullyConnected(
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

    context(weighting: WeightingConfig)
    fun accessibleCities(from: Int): Map<Int, Float> {
        val nextCities: Map<Int, Pheromone> = roads.accessibleCities(from)
        val fromPosition: Offset = cities[from]
        return nextCities.asSequence().associateBy(
            keySelector = { (cityIdx, _) -> cityIdx },
            valueTransform = { (cityIdx, pheromone) ->
                val distanceWeight = 1f / (fromPosition - cities[cityIdx]).getDistance()
                val pheromoneWeight = pheromone.amount

                distanceWeight.pow(weighting.distanceImportance) *
                pheromoneWeight.pow(weighting.pheromoneImportance)
            },
        )
    }
}
