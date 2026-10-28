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

    internal fun backtrack(): Boolean {
        val count = visitedCount
        if (count <= 0) return false
        val prevCount = count - 1
        val prevCity = visitedOrder[prevCount]
        visitedCount = prevCount
        val wordIndex = prevCity ushr 6
        visitedBits[wordIndex] = visitedBits[wordIndex] and (1L shl prevCity).inv()
        position = prevCity
        return true
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
     * Next city selection for sparse (non-complete) graphs that avoids 1-step dead ends,
     * preserves the last remaining return edge to [startPosition] until the final step,
     * and returns the chosen neighbor index in `graph.neighborCities[position]` (or `-1` if no move is valid).
     */
    context(rng: Random)
    internal fun chooseNextSparse(graph: CityGraph, triedMask: Long = 0L): Int {
        val citiesAmount = graph.cities.size
        if (visitedCount + 1 >= citiesAmount) return -1

        val currentPos = position
        val allNeighbors = graph.neighborCities
        val neighbors = allNeighbors[currentPos]
        if (neighbors.isEmpty()) return -1
        val weights = graph.neighborWeights[currentPos]

        // Final step: only one unvisited city remains in the graph
        if (visitedCount + 2 == citiesAmount) {
            for (i in neighbors.indices) {
                if (i < 64 && (triedMask and (1L shl i)) != 0L) continue
                val city = neighbors[i]
                if (city != currentPos && !isVisited(city) && graph.hasRoad(city, startPosition)) {
                    moveTo(city)
                    return i
                }
            }
            return -1
        }

        // Check how many unvisited neighbors startPosition still has
        val startNeighbors = allNeighbors[startPosition]
        var startUnvisitedCount = 0
        var lastStartNeighbor = -1
        for (i in startNeighbors.indices) {
            val u = startNeighbors[i]
            if (u != currentPos && !isVisited(u)) {
                startUnvisitedCount++
                lastStartNeighbor = u
                if (startUnvisitedCount > 1) break
            }
        }
        if (startUnvisitedCount == 0) return -1
        val reservedLastCity = if (startUnvisitedCount == 1) lastStartNeighbor else -1

        var forcedNeighborIndex = -1
        var validTotalWeight = 0f

        for (i in neighbors.indices) {
            val city = neighbors[i]
            if (city == currentPos || isVisited(city)) continue

            val nextNeighbors = allNeighbors[city]
            var onwardCount = 0
            for (j in nextNeighbors.indices) {
                val w = nextNeighbors[j]
                if (w != currentPos && w != city && !isVisited(w)) {
                    onwardCount++
                }
            }

            if (city == reservedLastCity) {
                if (onwardCount == 0) return -1
                continue
            }

            val connectsToStart = graph.hasRoad(city, startPosition)
            val futureDegree = onwardCount + (if (connectsToStart) 1 else 0)
            if (futureDegree == 0) return -1

            if (futureDegree == 1) {
                if (onwardCount == 0) return -1
                if (forcedNeighborIndex != -1) return -1
                forcedNeighborIndex = i
            } else if (onwardCount > 0) {
                val isTried = i < 64 && (triedMask and (1L shl i)) != 0L
                if (!isTried) {
                    val degreeFactor = 1f / (onwardCount * onwardCount)
                    validTotalWeight += weights[i] * degreeFactor
                }
            }
        }

        if (forcedNeighborIndex != -1) {
            val isTried = forcedNeighborIndex < 64 && (triedMask and (1L shl forcedNeighborIndex)) != 0L
            if (isTried) return -1
            moveTo(neighbors[forcedNeighborIndex])
            return forcedNeighborIndex
        }

        if (validTotalWeight <= 0f) return -1

        val target = rng.nextFloat() * validTotalWeight
        var cumulativeWeight = 0f

        for (i in neighbors.indices) {
            if (i < 64 && (triedMask and (1L shl i)) != 0L) continue
            val city = neighbors[i]
            if (city == currentPos || city == reservedLastCity || isVisited(city)) continue

            val nextNeighbors = allNeighbors[city]
            var onwardCount = 0
            for (j in nextNeighbors.indices) {
                val w = nextNeighbors[j]
                if (w != currentPos && w != city && !isVisited(w)) {
                    onwardCount++
                }
            }
            if (onwardCount == 0) continue

            val degreeFactor = 1f / (onwardCount * onwardCount)
            cumulativeWeight += weights[i] * degreeFactor
            if (target <= cumulativeWeight) {
                moveTo(city)
                return i
            }
        }

        return -1
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
