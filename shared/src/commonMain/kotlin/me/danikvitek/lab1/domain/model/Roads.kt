package me.danikvitek.lab1.domain.model

/**
 * A set of graph edges, associated with some property. Used to obtain the set of vertices, accessible from the provided vertex.
 */

sealed interface Roads : Map<Road, Pheromone> {
    fun accessibleCities(from: Int): Map<Int, Pheromone> =
        this.asSequence()
            .filter { it.key.city1 == from }
            .associate { it.key.city2 to it.value } +
        this.asSequence()
            .filter { it.key.city2 == from }
            .associate { it.key.city1 to it.value }
}

class MutableRoads(private val roads: MutableMap<Road, Pheromone> = mutableMapOf()) : Roads,
                                                                                       MutableMap<Road, Pheromone> by roads {
    fun add(city1: Int, city2: Int, pheromone: Pheromone) {
        put(Road(city1, city2), pheromone)
    }

    fun remove(city1: Int, city2: Int) = remove(Road(city1, city2))

    fun setAll(initialPheromoneLevel: Float) {
        entries.forEach {
            it.setValue(Pheromone(initialPheromoneLevel))
        }
    }

    fun updatePheromone(city1: Int, city2: Int, transform: (Pheromone) -> Pheromone) {
        computeIfPresent(Road(city1, city2)) { _, oldValue -> transform(oldValue) }
    }

    fun updateAllPheromones(transform: (road: Road, oldValue: Pheromone) -> Pheromone) = replaceAll(transform)

    override val entries by roads::entries
    override val keys by roads::keys
    override val values by roads::values

}