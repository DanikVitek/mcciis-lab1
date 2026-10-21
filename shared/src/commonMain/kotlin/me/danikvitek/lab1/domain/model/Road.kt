package me.danikvitek.lab1.domain.model


@JvmInline
value class Road internal constructor(val packed: Long) : Set<Int> {
    constructor(city1: Int, city2: Int) : this(pack(city1, city2))

    constructor(cities: Pair<Int, Int>) : this(cities.first, cities.second)

    companion object {
        internal fun pack(city1: Int, city2: Int): Long {
            require(city1 >= 0) { "City index must be non-negative" }
            require(city2 >= 0) { "City index must be non-negative" }
            require(city1 != city2) { "A road cannot connect a city to itself" }
            val min = minOf(city1, city2).toLong() and 0xFFFFFFFFL
            val max = maxOf(city1, city2).toLong()
            return (max shl 32) or min
        }
    }

    val city1
        get() = packed.toInt()
    val city2
        get() = (packed ushr 32).toInt()

    operator fun component1() = city1
    operator fun component2() = city2

    override val size: Int get() = 2

    override fun isEmpty() = false

    override operator fun contains(element: Int) = element == city1 || element == city2

    override fun containsAll(elements: Collection<Int>) = elements.all { it == city1 || it == city2 }

    override fun iterator() = object : IntIterator() {
        private var step = 0

        override fun nextInt(): Int {
            when (step) {
                0 -> {
                    step++
                    return city1
                }

                1 -> {
                    step++
                    return city2
                }

                else -> throw NoSuchElementException()
            }
        }

        override fun hasNext() = when (step) {
            0, 1 -> true
            else -> false
        }
    }

    override fun toString(): String {
        return "Road(city1=$city1, city2=$city2)"
    }
}
