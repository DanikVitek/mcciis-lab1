package me.danikvitek.lab1.domain.model

class CyclicRoute internal constructor(cities: List<Int> = emptyList(), val length: Float = Float.POSITIVE_INFINITY) :
        Collection<Road> {
    init {
        require(cities.isEmpty() || cities.size >= 2)
    }

    val cities by lazy {
        if (cities.isEmpty()) return@lazy cities

        // Find the minimum city index
        val minIndex = cities.indices
                           .minByOrNull { cities[it] }
                           ?.takeUnless { it == 0 }
                       ?: return@lazy cities

        buildList(cities.size) {
            // Create a new list with the normalized cities
            for (i in cities.indices) {
                add(cities[(minIndex + i) % cities.size])
            }
        }
    }

    val roads by lazy {
        if (this.cities.isEmpty())
            emptySet()
        else
            this.cities.asSequence().plusElement(this.cities[0]).zipWithNext().map(::Road).toSet()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CyclicRoute) return false

        if (cities.size != other.cities.size) return false
        if (cities.isEmpty()) return true

        return cities.asSequence()
            .zip(other.cities.asSequence())
            .all { (a, b) -> a == b }
    }

    override fun hashCode(): Int {
        var result = 1
        for (city in cities) {
            result = 31 * result + city.hashCode()
        }
        return result
    }

    override val size by this.cities::size

    override fun isEmpty() = cities.isEmpty()

    override fun contains(element: Road) = roads.contains(element)

    override fun iterator() = roads.iterator()

    /** Returns a list iterator in case one needs to iterate over the route only once. */
    fun citiesIterator() = cities.listIterator()

    override fun containsAll(elements: Collection<Road>) = roads.containsAll(elements)
}
