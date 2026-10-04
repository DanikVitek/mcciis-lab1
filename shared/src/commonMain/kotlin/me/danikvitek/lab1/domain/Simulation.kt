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
        field = distributedAnts().toMutableList()

    private fun distributedAnts() = graph.cities
        .indices
        .shuffled(rng)
        .asSequence()
        .take(antsAmount)
        .map(::Ant)

    private fun resetAnts() {
        ants.clear()
        ants.addAll(distributedAnts())
    }

    private fun updatePheromones() {
        val pheromoneDeltas = ants.asSequence()
            .filter {
                val accessibleCities = graph.roads.accessibleCities(it.position).keys
                it.canReturnToStart(accessibleCities)
            } // some routes may be incomplete, so we don't want to deposit pheromones for them
            .flatMap {
                val routeRoads: Set<Road> =
                    (it.visited.asSequence() + it.position)
                        .zipWithNext()
                        .map(::Road)
                        .plusElement(Road(it.position, it.startPosition))
                        .toSet()
                val routeLength: Float =
                    routeRoads.fold(0f) { acc, (from, to) -> acc + (graph.cities[from] - graph.cities[to]).getDistance() }
                routeRoads.asSequence()
                    .associateWith { weightingConfig.pheromoneDepositMultiplier / routeLength }
                    .asSequence()
            }
            .groupingBy { it.key }
            .aggregate { _, acc: Float?, entry, first -> if (first) entry.value else entry.value + acc!! }

        graph.mutableRoads.updateAllPheromones { road, oldValue ->
            Pheromone(
                amount = weightingConfig.pheromoneEvaporationRate * oldValue.amount +
                         weightingConfig.pheromoneDepositRate * pheromoneDeltas.getOrDefault(road, 0f),
            )
        }
    }

    var currentAge = 0
        private set

    private var bestLengthSoFar = Float.POSITIVE_INFINITY

    suspend fun tick(): Boolean = context(rng, weightingConfig) {
        if (currentAge >= ages) return true

        withContext(Dispatchers.Default) {
            val (bestLengthInGeneration, bestRouteInGeneration) = ants
                .mapIndexed { index, ant ->
                    async(CoroutineName("ant-$index")) {
                        var stuck = false
                        do {
                            val accessibleCities = graph.accessibleCities(ant.position)
                            val stuckOrFinished = ant.chooseNext(accessibleCities)
                            if (stuckOrFinished && (ant.cantReturnToStart(accessibleCities.keys) || ant.visited.size + 1 < graph.cities.size)) {
                                stuck = true
//                                yield()
                                break
                            }
//                            yield()
                        } while (!stuckOrFinished)

                        if (stuck) return@async null

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