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
    private val citiesAmount: Int = 60,
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

    var currentAge = 0
        private set

    suspend fun iterate(): Boolean = withContext(Dispatchers.Default) {
        if (currentAge >= ages) return@withContext true

        // Випадковий порядок стартових міст
        val startingCities = IntArray(citiesAmount) { it }.apply { shuffle() }

        for (k in 0 until antsAmount) {
            routes[k][0] = startingCities[k % citiesAmount]

            for (s in 1 until citiesAmount) {
                val currentCity = routes[k][s - 1]
                val probabilities = FloatArray(citiesAmount)
                var probSum = 0f

                for (nextCity in 0 until citiesAmount) {
                    // Перевіряємо, чи мураха вже відвідувала це місто
                    var visited = false
                    for (v in 0 until s) {
                        if (routes[k][v] == nextCity) {
                            visited = true
                            break
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
                    for (i in 0 until citiesAmount) {
                        if (probabilities[i] > 0f) {
                            cumulativeProb += probabilities[i]
                            if (cumulativeProb >= randomThreshold) {
                                chosenCity = i
                                break
                            }
                        }
                    }
                }

                // Fallback, якщо через float-округлення місто не обралось
                if (chosenCity == -1) {
                    for (i in 0 until citiesAmount) {
                        if (probabilities[i] > 0f) {
                            chosenCity = i
                            break
                        }
                    }
                }

                routes[k][s] = chosenCity
            }

            // Підрахунок загальної довжини побудованого маршруту
            var currentDistance = 0f
            for (i in 0 until citiesAmount - 1) {
                currentDistance += distances[routes[k][i]][routes[k][i + 1]]
            }
            currentDistance += distances[routes[k][citiesAmount - 1]][routes[k][0]]
            routeDistances[k] = currentDistance

            // Оновлення найкращого знайденого маршруту
            if (currentDistance < bestDistance) {
                bestDistance = currentDistance
                bestRouteChannel.send(routes[k].map { Offset(cities[it]) })
            }
        }

        // 1. Випаровування старих феромонів
        for (i in 0 until citiesAmount) {
            for (j in 0 until citiesAmount) {
                pheromones[i][j] *= (1f - pheromoneEvaporationRate)
            }
        }

        // 2. Відкладання нових феромонів (чим коротший шлях, тим більше феромону)
        for (k in 0 until antsAmount) {
            val pheromoneContribution = pheromoneDepositMultiplier / routeDistances[k]
            for (i in 0 until citiesAmount - 1) {
                val from = routes[k][i]
                val to = routes[k][i + 1]
                pheromones[from][to] += pheromoneContribution
            }
            // Враховуємо повернення в початкове місто
            val from = routes[k][citiesAmount - 1]
            val to = routes[k][0]
            pheromones[from][to] += pheromoneContribution
        }

        currentAge++
        return@withContext currentAge >= ages
    }
}