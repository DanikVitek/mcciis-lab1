package me.danikvitek.lab1.domain

import kotlinx.coroutines.*
import me.danikvitek.lab1.domain.model.*
import me.danikvitek.lab1.util.treeFold
import kotlin.math.ceil
import kotlin.random.Random

class Simulation(
    val graph: CityGraph,
    private val ages: Int,
    val antsAmount: Int = graph.cities.size,
    eliteAntsFraction: Float = 0f,
    private val weightingConfig: WeightingConfig = weightingConfig {},
    private val rng: Random = Random.Default,
) {
    init {
        require(ages > 0) { "Ages must be greater than 0" }
        require(antsAmount > 0) { "Ants amount must be greater than 0" }
    }

    val eliteAntsAmount = ceil(antsAmount * eliteAntsFraction).toInt()
    val ants: Collection<Ant>
        field = distributedAnts().toCollection(ArrayList(antsAmount))

    private fun distributedAnts() = sequence {
        val citiesAmount by graph.cities::size
        var eliteAntsLeft = eliteAntsAmount
        repeat(antsAmount / citiesAmount) {
            graph.cities
                .indices
                .shuffled(rng)
                .asSequence()
                .map {
                    Ant(
                        startPosition = it,
                        isElite = eliteAntsLeft-- > 0,
                    )
                }
                .let { yieldAll(it) }
        }
        graph.cities
            .indices
            .shuffled(rng)
            .subList(0, antsAmount % citiesAmount)
            .asSequence()
            .map {
                Ant(
                    startPosition = it,
                    isElite = eliteAntsLeft-- > 0,
                )
            }
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
            .flatMap { ant ->
                val routeRoads: Set<Road> = (ant.visited.asSequence() + ant.position + ant.startPosition)
                    .zipWithNext()
                    .map(::Road)
                    .toSet()
                val routeLength: Float = routeRoads
                    .map { (from, to) -> (graph.cities[from] - graph.cities[to]).getDistance() }
                    .reduce { acc, distance -> acc + distance }
                routeRoads
                    .associateWith { road ->
                        if (ant.isElite)
                            if (road in bestRouteSoFar)
                                weightingConfig.pheromoneDepositMultiplier / bestRouteSoFar.length
                            else 0f
                        else
                            weightingConfig.pheromoneDepositMultiplier / routeLength
                    }
                    .asSequence()
            }
            .groupingBy { it.key }
            .aggregate { _, acc: Float?, entry, first ->
                yield()
                if (first) entry.value else entry.value + acc!!
            }

        graph.mutableRoads.replaceAll { road, oldValue ->
            weightingConfig.pheromoneEvaporationRate * oldValue +
            pheromoneDeltas.getOrDefault(road, 0f)
        }
    }

    var currentAge = 0
        private set

    var bestRouteSoFar = CyclicRoute()
        private set

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

                        ant.visited
                            .toCollection(ArrayList(ant.visited.size + 1))
                            .apply { add(ant.position) }
                    }
                }
                .treeFold(
                    initial = { Float.POSITIVE_INFINITY to null },
                    aggregateItem = { acc: Pair<Float, List<Int>?>, route: List<Int>? ->
                        if (route == null) {
                            return@treeFold acc
                        }

                        var length = 0f
                        route.forEachIndexed { index, city ->
                            val nextCity = route[(index + 1) % route.size]
                            length += (graph.cities[city] - graph.cities[nextCity]).getDistance()
                        }
                        length to route
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

            if (bestLengthInGeneration < bestRouteSoFar.length) {
                bestRouteSoFar = CyclicRoute(bestRouteInGeneration!!, bestLengthInGeneration)
            }

            updateJob.join()
        }

        resetAnts()

        currentAge++
        return currentAge >= ages
    }
}