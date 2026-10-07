package me.danikvitek.lab1.domain.model

import androidx.compose.ui.geometry.Offset

class CityGraphBuilder(val initialPheromoneLevel: Float) {
    val cities: List<Offset>
        field = mutableListOf<Offset>()
    val roads: Roads
        field = MutableRoads()

    fun city(x: Float, y: Float): CityIndex {
        cities.add(Offset(x, y))
        return CityIndex(cities.lastIndex)
    }

    fun road(city1: CityIndex, city2: CityIndex, pheromone: Float = initialPheromoneLevel) =
        roads.put(city1.value, city2.value, pheromone)

    internal fun build() = CityGraph(cities, roads)
}

@JvmInline
value class CityIndex(val value: Int) {
    init {
        require(value >= 0) { "City index must be non-negative" }
    }
}

fun cityGraph(initialPheromoneLevel: Float, init: CityGraphBuilder.() -> Unit) =
    CityGraphBuilder(initialPheromoneLevel).apply(init).build()
