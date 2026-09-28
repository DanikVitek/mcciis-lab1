package me.danikvitek.lab1.domain

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random

class Simulation(
    ages: Int = 50,
    antsAmount: Int = 60,
    citiesAmount: Int = 60,
    pheromonePerRoute: Float = 1F,
    distanceOverPheromoneImportance: Float = 3F,
    pheromoneByArent: Float = .5F,
    pheromoneOnRoute: Float = 1F,
    pheromone0: Float = 1F,
) {
    init {
        require(ages > 0) { "Ages must be greater than 0" }
        require(antsAmount > 0) { "Ants amount must be greater than 0" }
        require(citiesAmount > 0) { "Cities amount must be greater than 0" }
        require(pheromonePerRoute > 0) { "Pheromone per route must be greater than 0" }
        require(distanceOverPheromoneImportance > 0) { "Distance over pheromone importance must be greater than 0" }
        require(pheromoneByArent > 0) { "Pheromone by arent must be greater than 0" }
        require(pheromoneOnRoute > 0) { "Pheromone on route must be greater than 0" }
        require(pheromone0 > 0) { "Pheromone initial value must be greater than 0" }
    }

    /** Matrix of distances between cities */
    val distances = Array(citiesAmount) { FloatArray(citiesAmount) }

    /** Matrix of reverse distances between cities */
    val reverseDistances = Array(citiesAmount) { FloatArray(citiesAmount) }

    /** Matrix of routes in one generation */
    val routes1 = Array(antsAmount) { IntArray(citiesAmount) }

    /** Matrix of distances in one generation */
    val distances1 = Array(antsAmount) { FloatArray(citiesAmount) }

    /** Starting value of the best distance */
    var bestDistance = Float.POSITIVE_INFINITY

    /** Optimal routes */
    val optimalRoutes = IntArray(citiesAmount)

    val citiesOrder = run {
        val cities = IntArray(citiesAmount) { it }
        cities.shuffle()
        cities
    }

    /** Probabilities of going from city to city */
    val priority = FloatArray(citiesAmount)

    /** Positions of the cities */
    val cities = LongArray(citiesAmount) {
        Offset(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
        ).packedValue
    }

    /** Pheromone on the routes */
    val pheromoneOnRoute = Array(citiesAmount) { FloatArray(citiesAmount) { pheromone0 } }

    init {
        for (i in 0..<citiesAmount) {
            for (j in 0..<citiesAmount) {
                distances[i][j] = (Offset(cities[i]) - Offset(cities[j])).getDistance()
                if (i != j) {
                    reverseDistances[i][j] = 1F / distances[i][j]
                }
            }
        }
    }

    var currentAge = 0
        private set

    fun iterate(): Boolean {
        return false
    }
}