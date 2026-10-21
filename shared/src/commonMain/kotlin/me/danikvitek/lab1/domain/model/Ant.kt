package me.danikvitek.lab1.domain.model

import kotlinx.collections.immutable.PersistentMap
import kotlin.random.Random

class Ant(
    startPosition: Int,
    isElite: Boolean = false,
    initialCapacity: Int = 16,
) {
    /**
     * The starting position of the ant in the list of cities.
     */
    var startPosition: Int = startPosition
        private set

    var isElite: Boolean = isElite
        private set

    /**
     * The current position of the ant in the list of cities.
     */
    var position: Int = startPosition
        private set

    internal var completedRouteLength: Float = Float.POSITIVE_INFINITY

    internal var visitedOrder: IntArray = IntArray(initialCapacity.coerceAtLeast(1))
        private set

    internal var visitedCount: Int = 0
        private set

    private var visitedBits: LongArray = LongArray(maxOf(1, (maxOf(startPosition, initialCapacity - 1) ushr 6) + 1))

    private fun moveTo(nextCity: Int) {
        require(nextCity >= 0) { "Position must be non-negative" }
        val prevCity = position
        val count = visitedCount
        if (count == visitedOrder.size) {
            visitedOrder = visitedOrder.copyOf(visitedOrder.size * 2)
        }
        visitedOrder[count] = prevCity
        visitedCount = count + 1

        val wordIndex = prevCity ushr 6
        if (wordIndex >= visitedBits.size) {
            visitedBits = visitedBits.copyOf(maxOf(visitedBits.size * 2, wordIndex + 1))
        }
        visitedBits[wordIndex] = visitedBits[wordIndex] or (1L shl prevCity)

        position = nextCity
    }

    internal fun isVisited(city: Int): Boolean {
        val wordIndex = city ushr 6
        return wordIndex < visitedBits.size && (visitedBits[wordIndex] and (1L shl city)) != 0L
    }

    internal fun reset(newStartPosition: Int, newIsElite: Boolean) {
        require(newStartPosition >= 0) { "Position must be non-negative" }
        visitedBits.fill(0L)
        visitedCount = 0
        completedRouteLength = Float.POSITIVE_INFINITY
        startPosition = newStartPosition
        isElite = newIsElite
        position = newStartPosition
    }

    /**
     * The set of visited cities.
     */
    val visited: Set<Int> = object : AbstractSet<Int>() {
        override val size: Int get() = visitedCount

        override fun isEmpty(): Boolean = visitedCount == 0

        override fun contains(element: Int): Boolean = element >= 0 && isVisited(element)

        override fun iterator(): IntIterator = object : IntIterator() {
            private var index = 0
            override fun hasNext(): Boolean = index < visitedCount
            override fun nextInt(): Int {
                if (index >= visitedCount) throw NoSuchElementException()
                return visitedOrder[index++]
            }
        }
    }

    /**
     * Allocation-free next city selection over precomputed primitive neighbor and weight arrays.
     */
    context(rng: Random)
    internal fun chooseNext(neighbors: IntArray, weights: FloatArray): ChoiceResult {
        if (neighbors.isEmpty()) return ChoiceResult.STAYED

        val currentPos = position
        var totalWeight = 0f
        for (i in neighbors.indices) {
            val city = neighbors[i]
            if (city != currentPos && !isVisited(city)) {
                totalWeight += weights[i]
            }
        }
        if (totalWeight <= 0f) return ChoiceResult.STAYED

        val target = rng.nextFloat() * totalWeight
        var cumulativeWeight = 0f
        for (i in neighbors.indices) {
            val city = neighbors[i]
            if (city != currentPos && !isVisited(city)) {
                cumulativeWeight += weights[i]
                if (target <= cumulativeWeight) {
                    moveTo(city)
                    return ChoiceResult.MOVED
                }
            }
        }

        return ChoiceResult.STAYED
    }

    /**
     * Chooses the next city to visit from the given set of cities. The chosen city is removed from the set of cities
     * and added to the set of visited cities. If there are no unvisited cities left, the ant stays in its current
     * position.
     *
     * @return `true` if the ant moved to a new city, `false` if it stayed in its current position.
     * @param rng The random number generator to use for choosing the next city.
     */
    context(rng: Random)
    fun chooseNext(nextCities: PersistentMap<Int, Float>): ChoiceResult {
        if (nextCities.isEmpty()) return ChoiceResult.STAYED

        val currentPos = position
        var totalWeight = 0f
        for ((city, weight) in nextCities) {
            if (city != currentPos && !isVisited(city)) {
                totalWeight += weight
            }
        }
        if (totalWeight <= 0f) return ChoiceResult.STAYED

        val target = rng.nextFloat() * totalWeight
        var cumulativeWeight = 0f
        for ((city, weight) in nextCities) {
            if (city != currentPos && !isVisited(city)) {
                cumulativeWeight += weight
                if (target <= cumulativeWeight) {
                    moveTo(city)
                    return ChoiceResult.MOVED
                }
            }
        }

        return ChoiceResult.STAYED
    }

    internal fun buildRouteList(): List<Int> {
        val size = visitedCount + 1
        val result = ArrayList<Int>(size)
        val order = visitedOrder
        for (i in 0..<visitedCount) {
            result.add(order[i])
        }
        result.add(position)
        return result
    }

    fun canReturnToStart(nextCities: Set<Int>): Boolean = startPosition in nextCities

    fun cannotReturnToStart(nextCities: Set<Int>): Boolean = !canReturnToStart(nextCities)

    @JvmInline
    value class ChoiceResult(private val value: Boolean) {
        companion object {
            val MOVED = ChoiceResult(true)
            val STAYED = ChoiceResult(false)
        }
    }
}
