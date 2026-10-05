package me.danikvitek.lab1.domain.model

import kotlin.random.Random

class Ant(
    /**
     * The current position of the ant in the list of cities.
     */
    val startPosition: Int,
) {
    /**
     * The current position of the ant in the list of cities.
     */
    var position = startPosition
        private set(value) {
            require(value >= 0) { "Position must be non-negative" }
            visited.add(field)
            field = value
        }

    /**
     * The set of visited cities.
     */
    val visited: Set<Int>
        field = LinkedHashSet()

    /**
     * Chooses the next city to visit from the given set of cities. The chosen city is removed from the set of cities
     * and added to the set of visited cities. If there are no unvisited cities left, the ant stays in its current
     * position.
     *
     * @return `true` if the ant moved to a new city, `false` if it stayed in its current position.
     * @param rng The random number generator to use for choosing the next city.
     */
    context(rng: Random)
    fun chooseNext(nextCities: Map<Int, Priority>): ChoiceResult {
        if (nextCities.isEmpty()) return ChoiceResult.STAYED

        require(
            nextCities.values.all { it.value >= 0f },
        ) { "Weights must be non-negative" }

        val unvisitedCities = sequence {
            for ((city, weight) in nextCities) {
                if (city !in visited) {
                    yield(city to weight)
                }
            }
        }.toMap()

        val totalWeight = unvisitedCities.values.fold(0f) { acc, it -> acc + it.value }
        if (totalWeight == 0f) return ChoiceResult.STAYED

        unvisitedCities.asSequence()
            .map(Map.Entry<Int, Priority>::toPair)
            .runningReduce { (_, accProb), (city, weight) -> city to (accProb + weight / totalWeight) }
            .let { seq ->
                val random = rng.nextFloat()
                seq.firstOrNull() { (_, cProb) -> random <= cProb.value }?.first
            }?.also { nextPosition ->
                position = nextPosition
                return ChoiceResult.MOVED
            }

        return ChoiceResult.STAYED
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