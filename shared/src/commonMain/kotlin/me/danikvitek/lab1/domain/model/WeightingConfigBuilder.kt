package me.danikvitek.lab1.domain.model

@DslMarker
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
private annotation class WeightingConfigDsl

@WeightingConfigDsl
class WeightingConfigBuilder {
    var distanceImportance: Float = 1f
        /** @throws IllegalArgumentException if the value is negative */
        set(value) {
            require(value >= 0) { "Distance importance cannot be negative" }
            field = value
        }

    fun distanceImportance(value: Float) {
        distanceImportance = value
    }

    var pheromoneImportance: Float = 3f
        /** @throws IllegalArgumentException if the value is negative */
        set(value) {
            require(value >= 0) { "Pheromone importance cannot be negative" }
            field = value
        }

    fun pheromoneImportance(value: Float) {
        pheromoneImportance = value
    }

    /** Should be selected with a magnitude of an optimal route's length */
    var pheromoneDepositMultiplier: Float = 1F
        /** @throws IllegalArgumentException if the value is not between 0 and 1 */
        set(value) {
            require(value > 0) { "Pheromone deposit multiplier must be greater than 0" }
            field = value
        }

    fun pheromoneDepositMultiplier(value: Float) {
        pheromoneDepositMultiplier = value
    }

    var pheromoneDepositRate: Float = 0.5F
        /** @throws IllegalArgumentException if the value is not between 0 and 1 */
        set(value) {
            require(value in 0.0..1.0) { "Deposit rate must be between 0 and 1" }
            field = value
        }

    fun pheromoneDepositRate(value: Float) {
        pheromoneDepositRate = value
    }

    var alpha
        get() = pheromoneImportance
        set(value) = pheromoneImportance(value)

    var beta
        get() = distanceImportance
        set(value) = distanceImportance(value)

    var rho
        get() = pheromoneDepositRate
        set(value) = pheromoneDepositRate(value)

    var qValue
        get() = pheromoneDepositMultiplier
        set(value) = pheromoneDepositMultiplier(value)

    fun build(): WeightingConfig = WeightingConfig(
        distanceImportance = distanceImportance,
        pheromoneImportance = pheromoneImportance,
        pheromoneDepositMultiplier = pheromoneDepositMultiplier,
        pheromoneDepositRate = pheromoneDepositRate,
    )
}

fun weightingConfig(
    init: WeightingConfigBuilder.() -> Unit = {},
): WeightingConfig = WeightingConfigBuilder().apply(init).build()
