package me.danikvitek.lab1.domain.model

class CyclicRoute internal constructor(cities: List<Int> = emptyList(), val length: Float = Float.POSITIVE_INFINITY) :
        Collection<Road> {
    init {
        require(cities.isEmpty() || cities.size >= 2)
    }

    val cities by lazy {
        if (cities.isEmpty()) return@lazy cities

        var minIndex = 0
        var minValue = cities[0]
        for (i in 1..<cities.size) {
            val v = cities[i]
            if (v < minValue) {
                minValue = v
                minIndex = i
            }
        }
        if (minIndex == 0) return@lazy cities

        val n = cities.size
        buildList(n) {
            for (i in 0..<n) {
                add(cities[(minIndex + i) % n])
            }
        }
    }

    /**
     * Sorted array of packed road values for allocation-free binary search membership checks.
     */
    private val packedRoadsSorted: LongArray by lazy {
        val normCities = this.cities
        val n = normCities.size
        if (n == 0) {
            LongArray(0)
        } else {
            LongArray(n) { i ->
                Road.pack(normCities[i], normCities[(i + 1) % n])
            }.apply { sort() }
        }
    }

    val roads by lazy {
        val normCities = this.cities
        val n = normCities.size
        if (n == 0) {
            emptySet()
        } else {
            LinkedHashSet<Road>(n).apply {
                for (i in 0..<n) {
                    add(Road(normCities[i], normCities[(i + 1) % n]))
                }
            }
        }
    }

    internal fun containsRoad(city1: Int, city2: Int): Boolean =
        packedRoadsSorted.binarySearch(Road.pack(city1, city2)) >= 0

    internal fun containsRoad(road: Road): Boolean =
        packedRoadsSorted.binarySearch(road.packed) >= 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CyclicRoute) return false

        val a = cities
        val b = other.cities
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
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

    override fun contains(element: Road) = containsRoad(element)

    override fun iterator() = roads.iterator()

    /** Returns a list iterator in case one needs to iterate over the route only once. */
    fun citiesIterator() = cities.listIterator()

    override fun containsAll(elements: Collection<Road>) = elements.all { containsRoad(it) }
}
