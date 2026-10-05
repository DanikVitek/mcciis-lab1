package me.danikvitek.lab1.domain.model

data class WeightingConfig(
    val distanceImportance: Float = 1f,
    val pheromoneImportance: Float = 3f,
    val pheromoneDepositMultiplier: Float = 1F,
    val pheromoneDepositRate: Float = 0.5F,
) {
    init {
        require(pheromoneImportance >= 0) { "Pheromone importance cannot be negative" }
        require(distanceImportance >= 0) { "Distance importance cannot be negative" }
        require(pheromoneDepositMultiplier > 0) { "Pheromone deposit multiplier must be greater than 0" }
        require(pheromoneDepositRate in 0.0..1.0) { "Evaporation rate must be between 0 and 1" }
    }

    val alpha by this::distanceImportance
    val beta by this::pheromoneImportance
    val rho by this::pheromoneDepositRate
    val qValue by this::pheromoneDepositMultiplier

    val pheromoneEvaporationRate: Float
        get() = 1 - pheromoneDepositRate
}
