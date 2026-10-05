package me.danikvitek.lab1.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import me.danikvitek.lab1.domain.model.*
import me.danikvitek.lab1.util.treeFold
import kotlin.random.Random

class Simulation(
    private val bestRouteChannel: SendChannel<CyclicRoute>,
    val graph: CityGraph,
    val antsAmount: Int = graph.cities.size,
    private val ages: Int = 50,
    private val weightingConfig: WeightingConfig = weightingConfig {},
    private val rng: Random = Random.Default,
) {
    init {
        require(ages > 0) { "Ages must be greater than 0" }
        require(antsAmount > 0) { "Ants amount must be greater than 0" }
    }

    val ants: Collection<Ant>
        field = distributedAnts().toCollection(ArrayList(antsAmount))

    private fun distributedAnts() = sequence {
        repeat(antsAmount / graph.cities.size) {
            graph.cities
                .indices
                .shuffled(rng)
                .asSequence()
                .map(::Ant)
                .let { yieldAll(it) }
        }
        graph.cities
            .indices
            .shuffled(rng)
            .asSequence()
            .take(antsAmount % graph.cities.size)
            .map(::Ant)
            .let { yieldAll(it) }
    }

    private fun resetAnts() {
        val distributedAnts = distributedAnts().iterator()
        ants.replaceAll { distributedAnts.next() }
    }

    private suspend fun updatePheromones() {
        val pheromoneDeltas = ants.asSequence()
            .filter {
                val accessibleCities = graph.roads.accessibleCities(it.position).keys
                it.canReturnToStart(accessibleCities)
            } // some routes may be incomplete, so we don't want to deposit pheromones for them
            .flatMap {
                val routeRoads: Set<Road> = (it.visited.asSequence() + it.position + it.startPosition)
                    .zipWithNext()
                    .map(::Road)
                    .toSet()
                val routeLength: Float = routeRoads
                    .map { (from, to) -> (graph.cities[from] - graph.cities[to]).getDistance() }
                    .reduce { acc, distance -> acc + distance }
                routeRoads
                    .associateWith { weightingConfig.pheromoneDepositMultiplier / routeLength }
                    .asSequence()
            }
            .groupingBy { it.key }
            .aggregate { _, acc: Float?, entry, first ->
                yield()
                if (first) entry.value else entry.value + acc!!
            }

        graph.mutableRoads.replaceAll { road: Road, oldValue: Pheromone ->
            Pheromone(
                amount = weightingConfig.pheromoneEvaporationRate * oldValue.amount +
                         weightingConfig.pheromoneDepositRate * pheromoneDeltas.getOrDefault(road, 0f),
            )
        }
    }

    var currentAge = 0
        private set

    private var bestLengthSoFar = Float.POSITIVE_INFINITY

    private var coroutine: CoroutineScope? = null
    suspend fun cancel() {
        coroutine?.coroutineContext[Job]?.cancelAndJoin()
        coroutine = null
    }

    suspend fun tick(): Boolean = context(rng, weightingConfig) {
        if (currentAge >= ages) return true

        withContext(Dispatchers.Default) {
            coroutine = this
            val (bestLengthInGeneration, bestRouteInGeneration) = ants
                .mapIndexed { index, ant ->
                    async(CoroutineName("ant-$index")) {
                        do {
                            val accessibleCities = graph.accessibleCities(ant.position)

                            val stuckOrFinished = ant.chooseNext(accessibleCities) == Ant.ChoiceResult.STAYED
                            val stuck = stuckOrFinished &&
                                        (ant.cannotReturnToStart(accessibleCities.keys) ||
                                         ant.visited.size + 1 < graph.cities.size)
                            if (stuck) {
                                return@async null
                            }
                            yield()
                        } while (!stuckOrFinished)

                        val route = (ant.visited.asSequence() + ant.position).toList()
                        var sum = 0f
                        route.forEachIndexed { index, city ->
                            val nextCity = route[(index + 1) % route.size]
                            sum += (graph.cities[city] - graph.cities[nextCity]).getDistance()
                        }
                        sum to route
                    }
                }
                .treeFold(
                    initial = { Float.POSITIVE_INFINITY to null },
                    aggregateItem = { acc: Pair<Float, List<Int>?>, item: Pair<Float, List<Int>>? ->
                        if (item == null) {
                            acc
                        } else {
                            val (bestLength) = acc
                            val (length) = item
                            if (length < bestLength) {
                                item
                            } else {
                                acc
                            }
                        }
                    },
                    aggregateAccumulators = { left: Pair<Float, List<Int>?>, right: Pair<Float, List<Int>?> ->
                        if (left.first < right.first) {
                            left
                        } else {
                            right
                        }
                    },
                )

            val updateJob = launch(CoroutineName("updatePheromones")) {
                updatePheromones()
            }

            if (bestLengthInGeneration < bestLengthSoFar) {
                bestLengthSoFar = bestLengthInGeneration
                bestRouteChannel.send(CyclicRoute(bestRouteInGeneration!!))
            }

            updateJob.join()
        }

        resetAnts()

        currentAge++
        return currentAge >= ages
    }
}