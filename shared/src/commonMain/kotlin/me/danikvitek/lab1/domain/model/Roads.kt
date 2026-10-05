package me.danikvitek.lab1.domain.model

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.BiFunction

/**
 * A set of graph edges, associated with some property. Used to obtain the set of vertices, accessible from the provided vertex.
 */
sealed interface Roads : Map<Road, Pheromone> {
    fun accessibleCities(from: Int): Map<Int, Pheromone>
}

class MutableRoads(private val roads: MutableMap<Road, Pheromone> = mutableMapOf()) :
        Roads,
        MutableMap<Road, Pheromone> by roads {

    private val accessibleCitiesCache: ConcurrentMap<Int, MutableMap<Int, Pheromone>> = ConcurrentHashMap()

    override fun accessibleCities(from: Int): Map<Int, Pheromone> = accessibleCitiesCache.getOrPut(from) {
        asSequence().mapNotNull { (road, value) ->
            if (road.city1 == from)
                road.city2 to value
            else if (road.city2 == from)
                road.city1 to value
            else null
        }.toMap(mutableMapOf())
    }

    fun put(city1: Int, city2: Int, value: Pheromone) = put(Road(city1, city2), value)

    fun remove(city1: Int, city2: Int) = remove(Road(city1, city2))

    fun setAll(value: Pheromone) {
//        accessibleCitiesCache.clear()
        entries.forEach {
            accessibleCitiesCache[it.key.city1]?.computeIfPresent(it.key.city2) { _, _ -> value }
            accessibleCitiesCache[it.key.city2]?.computeIfPresent(it.key.city1) { _, _ -> value }
            it.setValue(value)
        }
    }

    fun computeIfPresent(city1: Int, city2: Int, transform: (Pheromone) -> Pheromone): Pheromone? {
        return computeIfPresent(Road(city1, city2)) { _, oldValue ->
            val newValue = transform(oldValue)
            accessibleCitiesCache[city1]?.computeIfPresent(city2) { _, _ -> newValue }
            accessibleCitiesCache[city2]?.computeIfPresent(city1) { _, _ -> newValue }
            newValue
        }
    }

    override fun replaceAll(function: BiFunction<in Road, in Pheromone, out Pheromone>) {
        super.replaceAll { road, oldValue ->
            val newValue = function.apply(road, oldValue)
            accessibleCitiesCache[road.city1]?.computeIfPresent(road.city2) { _, _ -> newValue }
            accessibleCitiesCache[road.city2]?.computeIfPresent(road.city1) { _, _ -> newValue }
            newValue
        }
    }

    override val entries by roads::entries
    override val keys by roads::keys
    override val values by roads::values
}