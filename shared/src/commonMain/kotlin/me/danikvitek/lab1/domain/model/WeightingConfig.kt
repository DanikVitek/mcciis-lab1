package me.danikvitek.lab1.domain.model

import androidx.annotation.FloatRange

data class WeightingConfig(
    @FloatRange(from = 0.0)
    val distanceImportance: Float = 1f,
    @FloatRange(from = 0.0)
    val pheromoneImportance: Float = 3f,
    @FloatRange(from = 0.0, fromInclusive = false)
    val pheromoneDepositMultiplier: Float = 1F,
    @FloatRange(from = 0.0, to = 1.0)
    val pheromoneDepositRate: Float = 0.5F,
) {
    init {
        require(pheromoneImportance >= 0) { "Pheromone importance cannot be negative" }
        require(distanceImportance >= 0) { "Distance importance cannot be negative" }
        require(pheromoneDepositMultiplier > 0) { "Pheromone deposit multiplier must be greater than 0" }
        require(pheromoneDepositRate in 0.0..1.0) { "Evaporation rate must be between 0 and 1" }
    }

    @get:FloatRange(from = 0.0)
    val alpha by this::distanceImportance

    @get:FloatRange(from = 0.0)
    val beta by this::pheromoneImportance

    @get:FloatRange(from = 0.0, to = 1.0)
    val rho by this::pheromoneDepositRate

    @get:FloatRange(from = 0.0, fromInclusive = false)
    val qValue by this::pheromoneDepositMultiplier

    val pheromoneEvaporationRate: Float
        @FloatRange(from = 0.0, to = 1.0)
        get() = 1 - pheromoneDepositRate
}
