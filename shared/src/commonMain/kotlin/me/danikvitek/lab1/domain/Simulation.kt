package me.danikvitek.lab1.domain

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.random.Random

class Simulation(
    private val bestRouteChannel: Channel<List<Offset>>,
    private val ages: Int = 50,
    private val antsAmount: Int = 60,
    val citiesAmount: Int = 60,
    private val pheromoneImportance: Float = 1F,
    private val distanceImportance: Float = 3F,
    private val pheromoneEvaporationRate: Float = 0.5F,
    private val pheromoneDepositMultiplier: Float = 1F,
    private val initialPheromoneLevel: Float = 0.01F,
) {
    init {
        require(ages > 0) { "Ages must be greater than 0" }
        require(antsAmount > 0) { "Ants amount must be greater than 0" }
        require(citiesAmount > 0) { "Cities amount must be greater than 0" }
        require(pheromoneImportance >= 0) { "Pheromone importance cannot be negative" }
        require(distanceImportance >= 0) { "Distance importance cannot be negative" }
        require(pheromoneEvaporationRate in 0.0..1.0) { "Evaporation rate must be between 0.0 and 1.0" }
        require(pheromoneDepositMultiplier > 0) { "Pheromone deposit multiplier must be greater than 0" }
        require(initialPheromoneLevel > 0) { "Initial pheromone level must be greater than 0" }
    }

    val cities = LongArray(citiesAmount) {
        Offset(Random.nextFloat(), Random.nextFloat()).packedValue
    }

    val distances = Array(citiesAmount) { i ->
        FloatArray(citiesAmount) { j ->
            if (i == j) 0f else (Offset(cities[i]) - Offset(cities[j])).getDistance()
        }
    }

    private val reverseDistances = Array(citiesAmount) { i ->
        FloatArray(citiesAmount) { j ->
            if (i == j) 0f else 1f / distances[i][j]
        }
    }

    val routes = Array(antsAmount) { IntArray(citiesAmount) }
    private val routeDistances = FloatArray(antsAmount)

    var bestDistance = Float.POSITIVE_INFINITY
        private set

    private val pheromones = Array(citiesAmount) {
        FloatArray(citiesAmount) { initialPheromoneLevel }
    }
    val currentPheromones
        get() = Array(pheromones.size) { pheromones[it].clone() }

    var currentAge = 0
        private set

    var currentStep = 0
        private set

    suspend fun tick(): Boolean = withContext(Dispatchers.Default) {
        if (currentAge >= ages) return@withContext true

        // Фаза 1: Початок епохи, розставляємо мурах у випадкові міста
        if (currentStep == 0) {
            val startingCities = IntArray(citiesAmount) { it }.apply { shuffle() }
            for (k in 0..<antsAmount) {
                routes[k][0] = startingCities[k % citiesAmount]
            }
            currentStep = 1
            return@withContext false
        }

        // Фаза 2: Рух усіх мурах на одне місто вперед
        if (currentStep < citiesAmount) {
            val s = currentStep
            for (k in 0..<antsAmount) {
                val currentCity = routes[k][s - 1]
                val probabilities = FloatArray(citiesAmount)
                var probSum = 0f

                for (nextCity in 0..<citiesAmount) {
                    // Перевіряємо, чи мураха вже відвідувала це місто
                    var visited = false
                    for (v in 0..<s) {
                        if (routes[k][v] == nextCity) {
                            visited = true; break
                        }
                    }

                    if (!visited) {
                        val p = pheromones[currentCity][nextCity].pow(pheromoneImportance) *
                                reverseDistances[currentCity][nextCity].pow(distanceImportance)
                        probabilities[nextCity] = p
                        probSum += p
                    }
                }

                // Вибір наступного міста на основі ймовірностей (Roulette wheel selection)
                var chosenCity = -1
                if (probSum > 0f) {
                    val randomThreshold = Random.nextFloat() * probSum
                    var cumulativeProb = 0f
                    for (i in 0..<citiesAmount) {
                        if (probabilities[i] > 0f) {
                            cumulativeProb += probabilities[i]
                            if (cumulativeProb >= randomThreshold) {
                                chosenCity = i; break
                            }
                        }
                    }
                }
                if (chosenCity == -1) {
                    for (i in 0..<citiesAmount) {
                        if (probabilities[i] > 0f) {
                            chosenCity = i; break
                        }
                    }
                }
                routes[k][s] = chosenCity
            }
            currentStep++
            return@withContext false
        }

        // Фаза 3: Епоха завершена (currentStep == citiesAmount), оновлюємо феромони
        for (k in 0..<antsAmount) {
            var currentDistance = 0f
            for (i in 0..<citiesAmount - 1) {
                currentDistance += distances[routes[k][i]][routes[k][i + 1]]
            }
            currentDistance += distances[routes[k][citiesAmount - 1]][routes[k][0]]
            routeDistances[k] = currentDistance

            if (currentDistance < bestDistance) {
                bestDistance = currentDistance
                bestRouteChannel.send(routes[k].map { Offset(cities[it]) })
            }
        }

        for (i in 0..<citiesAmount) {
            for (j in 0..<citiesAmount) {
                pheromones[i][j] *= (1f - pheromoneEvaporationRate)
            }
        }

        for (k in 0..<antsAmount) {
            val pheromoneContribution = pheromoneDepositMultiplier / routeDistances[k]
            for (i in 0..<citiesAmount - 1) {
                pheromones[routes[k][i]][routes[k][i + 1]] += pheromoneContribution
            }
            pheromones[routes[k][citiesAmount - 1]][routes[k][0]] += pheromoneContribution
        }

        currentStep = 0
        currentAge++
        return@withContext currentAge >= ages
    }
}