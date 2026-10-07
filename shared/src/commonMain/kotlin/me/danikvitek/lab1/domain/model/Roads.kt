package me.danikvitek.lab1.domain.model

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentMapOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.BiFunction

/**
 * A set of graph edges, associated with some property. Used to obtain the set of vertices, accessible from the provided vertex.
 */
sealed interface Roads : Map<Road, Float> {
    fun accessibleCities(from: Int): PersistentMap<Int, Float>
}

class MutableRoads(private val roads: MutableMap<Road, Float> = mutableMapOf()) :
        Roads,
        MutableMap<Road, Float> by roads {

    private val accessibleCitiesCache: ConcurrentMap<Int, PersistentMap<Int, Float>> = ConcurrentHashMap()

    override fun accessibleCities(from: Int): PersistentMap<Int, Float> = accessibleCitiesCache.getOrPut(from) {
        persistentHashMapOf<Int, Float>().mutate {
            for ((road, value) in roads) {
                if (road.city1 == from)
                    it[road.city2] = value
                else if (road.city2 == from)
                    it[road.city1] = value
            }
        }
    }

    fun put(city1: Int, city2: Int, value: Float): Float? {
        accessibleCitiesCache.compute(city1) { _, it ->
            it?.mutate { it[city2] = value } ?: persistentHashMapOf(city2 to value)
        }
        accessibleCitiesCache.compute(city2) { _, it ->
            it?.mutate { it[city1] = value } ?: persistentHashMapOf(city1 to value)
        }
        return put(Road(city1, city2), value)
    }

    fun remove(city1: Int, city2: Int) = remove(Road(city1, city2))

    fun computeIfPresent(city1: Int, city2: Int, transform: (Float) -> Float) {
        computeIfPresent(Road(city1, city2)) { _, oldValue ->
            val newValue = transform(oldValue)
            accessibleCitiesCache.compute(city1) { _, it ->
                it?.mutate { it[city2] = newValue } ?: persistentHashMapOf(city2 to newValue)
            }
            accessibleCitiesCache.compute(city2) { _, it ->
                it?.mutate { it[city1] = newValue } ?: persistentHashMapOf(city1 to newValue)
            }
            newValue
        }
    }

    override fun replaceAll(function: BiFunction<in Road, in Float, out Float>) {
        super.replaceAll { road, oldValue ->
            val newValue = function.apply(road, oldValue)
            accessibleCitiesCache.compute(road.city1) { _, it ->
                it?.mutate { it[road.city2] = newValue } ?: persistentHashMapOf(road.city2 to newValue)
            }
            accessibleCitiesCache.compute(road.city2) { _, it ->
                it?.mutate { it[road.city1] = newValue } ?: persistentHashMapOf(road.city1 to newValue)
            }
            newValue
        }
    }

    override val entries by roads::entries
    override val keys by roads::keys
    override val values by roads::values
}