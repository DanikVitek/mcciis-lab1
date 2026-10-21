package me.danikvitek.lab1.domain

import kotlinx.coroutines.*
import me.danikvitek.lab1.domain.model.*
import me.danikvitek.lab1.util.treeReduceOrNull
import kotlin.math.ceil
import kotlin.random.Random

class Simulation(
    val graph: CityGraph,
    private val ages: Int,
    val antsAmount: Int = graph.cities.size,
    eliteAntsFraction: Float = 0f,
    private val weightingConfig: WeightingConfig = weightingConfig {},
    private val rng: Random = Random.Default,
    initialBestRoute: CyclicRoute = CyclicRoute(),
) {
    init {
        require(ages > 0) { "Ages must be greater than 0" }
        require(antsAmount > 0) { "Ants amount must be greater than 0" }
    }

    val eliteAntsAmount = ceil(antsAmount * eliteAntsFraction).toInt()

    private val shuffledCityIndices = IntArray(graph.cities.size) { it }
    private val pheromoneDeltas = FloatArray(graph.edgesCount)
    private val antCoroutineNames = Array(antsAmount) { CoroutineName("ant-$it") }
    private val antJobs = ArrayList<Deferred<Ant?>>(antsAmount)

    val ants: Collection<Ant>
        field = ArrayList<Ant>(antsAmount).apply {
            val citiesAmount = graph.cities.size
            var cityCursor = citiesAmount
            for (i in 0..<antsAmount) {
                if (cityCursor >= citiesAmount) {
                    shuffledCityIndices.shuffle(rng)
                    cityCursor = 0
                }
                add(
                    Ant(
                        startPosition = shuffledCityIndices[cityCursor++],
                        isElite = i < eliteAntsAmount,
                        initialCapacity = citiesAmount,
                    )
                )
            }
        }

    private fun resetAnts() {
        val citiesAmount = graph.cities.size
        var cityCursor = citiesAmount
        for (i in 0..<antsAmount) {
            if (cityCursor >= citiesAmount) {
                shuffledCityIndices.shuffle(rng)
                cityCursor = 0
            }
            ants[i].reset(
                newStartPosition = shuffledCityIndices[cityCursor++],
                newIsElite = i < eliteAntsAmount,
            )
        }
    }

    private suspend fun updatePheromones() {
        val deltas = pheromoneDeltas
        deltas.fill(0f)

        val qValue = weightingConfig.pheromoneDepositMultiplier
        val bestRoute = bestRouteSoFar
        val edgeDistances = graph.edgeDistances

        for (antIndex in ants.indices) {
            val ant = ants[antIndex]
            val pos = ant.position
            val start = ant.startPosition
            // Some routes may be incomplete, so we don't want to deposit pheromones for them
            if (!graph.hasRoad(pos, start)) continue

            val visitedCount = ant.visitedCount
            if (visitedCount == 0) continue
            val visitedOrder = ant.visitedOrder

            if (ant.isElite) {
                if (bestRoute.isEmpty()) continue
                val eliteDeposit = qValue / bestRoute.length
                if (visitedCount == 1) {
                    if (bestRoute.containsRoad(start, pos)) {
                        deltas[graph.edgeIndex(start, pos)] += eliteDeposit
                    }
                } else {
                    for (i in 0..<visitedCount - 1) {
                        val u = visitedOrder[i]
                        val v = visitedOrder[i + 1]
                        if (bestRoute.containsRoad(u, v)) {
                            deltas[graph.edgeIndex(u, v)] += eliteDeposit
                        }
                    }
                    val lastVisited = visitedOrder[visitedCount - 1]
                    if (bestRoute.containsRoad(lastVisited, pos)) {
                        deltas[graph.edgeIndex(lastVisited, pos)] += eliteDeposit
                    }
                    if (bestRoute.containsRoad(pos, start)) {
                        deltas[graph.edgeIndex(pos, start)] += eliteDeposit
                    }
                }
            } else {
                if (visitedCount == 1) {
                    val edgeIdx = graph.edgeIndex(start, pos)
                    val routeLength = edgeDistances[edgeIdx]
                    deltas[edgeIdx] += qValue / routeLength
                } else {
                    var routeLength = ant.completedRouteLength
                    if (routeLength == Float.POSITIVE_INFINITY) {
                        var sum = 0f
                        for (i in 0..<visitedCount - 1) {
                            sum += edgeDistances[graph.edgeIndex(visitedOrder[i], visitedOrder[i + 1])]
                        }
                        sum += edgeDistances[graph.edgeIndex(visitedOrder[visitedCount - 1], pos)]
                        sum += edgeDistances[graph.edgeIndex(pos, start)]
                        routeLength = sum
                    }
                    val deposit = qValue / routeLength
                    for (i in 0..<visitedCount - 1) {
                        deltas[graph.edgeIndex(visitedOrder[i], visitedOrder[i + 1])] += deposit
                    }
                    deltas[graph.edgeIndex(visitedOrder[visitedCount - 1], pos)] += deposit
                    deltas[graph.edgeIndex(pos, start)] += deposit
                }
            }
            yield()
        }

        graph.mutableRoads.updateAllPheromones(
            evaporationRate = weightingConfig.pheromoneEvaporationRate,
            deltas = deltas,
        )
    }

    var currentAge = 0
        private set

    var bestRouteSoFar = graph.validateRoute(initialBestRoute.cities) ?: CyclicRoute()
        private set

    init {
        // Warm-start: if we have a valid previous best route, seed initial pheromone boost along its edges
        if (bestRouteSoFar.isNotEmpty() && bestRouteSoFar.length.isFinite() && bestRouteSoFar.length > 0f) {
            val initialBoost = weightingConfig.pheromoneDepositMultiplier / bestRouteSoFar.length
            for (road in bestRouteSoFar.roads) {
                graph.mutableRoads.computeIfPresent(road.city1, road.city2) { it + initialBoost }
            }
        }
    }

    private var coroutine: CoroutineScope? = null
    suspend fun cancel() {
        coroutine?.coroutineContext[Job]?.cancelAndJoin()
        coroutine = null
    }

    suspend fun tick(): Boolean = context(rng, weightingConfig) {
        if (currentAge >= ages) return true

        val citiesAmount = graph.cities.size
        val neighborCities = graph.neighborCities
        val neighborWeights = graph.neighborWeights

        graph.refreshWeights(weightingConfig)

        withContext(Dispatchers.Default) {
            coroutine = this
            antJobs.clear()
            for ((index, ant) in ants.withIndex()) {
                antJobs.add(
                    async(antCoroutineNames[index]) {
                        do {
                            val currentPos = ant.position
                            val stuckOrFinished = ant.chooseNext(
                                neighborCities[currentPos],
                                neighborWeights[currentPos],
                            ) == Ant.ChoiceResult.STAYED
                            val stuck = stuckOrFinished &&
                                        (!graph.hasRoad(ant.position, ant.startPosition) ||
                                         ant.visitedCount + 1 < citiesAmount)
                            if (stuck) {
                                return@async null
                            }
                            yield()
                        } while (!stuckOrFinished)

                        val visitedOrder = ant.visitedOrder
                        val visitedCount = ant.visitedCount
                        var length = 0f
                        for (i in 0..<visitedCount - 1) {
                            length += graph.distanceBetween(visitedOrder[i], visitedOrder[i + 1])
                        }
                        if (visitedCount > 0) {
                            length += graph.distanceBetween(visitedOrder[visitedCount - 1], ant.position)
                        }
                        length += graph.distanceBetween(ant.position, ant.startPosition)
                        ant.completedRouteLength = length
                        ant
                    }
                )
            }

            val bestAntInGeneration = antJobs.treeReduceOrNull { left, right ->
                when {
                    left == null -> right
                    right == null -> left
                    left.completedRouteLength <= right.completedRouteLength -> left
                    else -> right
                }
            }

            if (bestAntInGeneration != null && bestAntInGeneration.completedRouteLength < bestRouteSoFar.length) {
                bestRouteSoFar = CyclicRoute(
                    bestAntInGeneration.buildRouteList(),
                    bestAntInGeneration.completedRouteLength,
                )
            }

            updatePheromones()
        }

        resetAnts()

        currentAge++
        return currentAge >= ages
    }
}
