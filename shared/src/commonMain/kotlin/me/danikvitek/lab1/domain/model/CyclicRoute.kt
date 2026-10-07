package me.danikvitek.lab1.domain.model

class CyclicRoute internal constructor(cities: List<Int> = emptyList(), val length: Float = Float.POSITIVE_INFINITY) :
        Collection<Int> {
    val cities = run {
        if (cities.isEmpty()) return@run cities

        // Find the minimum city index
        val minIndex = cities.indices.minByOrNull { cities[it] }?.takeUnless { it == 0 } ?: return@run cities

        buildList(cities.size) {
            // Create a new list with the normalized cities
            for (i in cities.indices) {
                add(cities[(minIndex + i) % cities.size])
            }
        }
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

    override fun isEmpty() = this.cities.isEmpty()

    override fun contains(element: Int) = this.cities.contains(element)

    override fun iterator() = object : IntIterator() {
        private var inner = cities.iterator()

        override fun nextInt(): Int {
            if (!hasNext()) throw NoSuchElementException("empty")
            if (!inner.hasNext()) {
                inner = cities.iterator()
            }
            return inner.next()
        }

        override fun hasNext() = !isEmpty()
    }

    /** Returns a list iterator in case one needs to iterate over the route only once. */
    fun listIterator() = cities.listIterator()

    override fun containsAll(elements: Collection<Int>) = cities.containsAll(elements)
}