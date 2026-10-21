package me.danikvitek.lab1.domain.model

import androidx.annotation.FloatRange

@DslMarker
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
private annotation class WeightingConfigDsl

@WeightingConfigDsl
class WeightingConfigBuilder {
    @FloatRange(from = 0.0)
    var distanceImportance: Float = 1f
        /** @throws IllegalArgumentException if the value is negative */
        @FloatRange(from = 0.0)
        set(value) {
            require(value >= 0) { "Distance importance cannot be negative" }
            field = value
        }

    fun distanceImportance(@FloatRange(from = 0.0) value: Float) {
        distanceImportance = value
    }

    @FloatRange(from = 0.0)
    var pheromoneImportance: Float = 3f
        /** @throws IllegalArgumentException if the value is negative */
        @FloatRange(from = 0.0)
        set(value) {
            require(value >= 0) { "Pheromone importance cannot be negative" }
            field = value
        }

    fun pheromoneImportance(@FloatRange(from = 0.0) value: Float) {
        pheromoneImportance = value
    }

    /** Should be selected with a magnitude of an optimal route's length */
    @FloatRange(from = 0.0, fromInclusive = false)
    var pheromoneDepositMultiplier: Float = 1F
        /** @throws IllegalArgumentException if the value is not between 0 and 1 */
        @FloatRange(from = 0.0, fromInclusive = false)
        set(value) {
            require(value > 0) { "Pheromone deposit multiplier must be greater than 0" }
            field = value
        }

    fun pheromoneDepositMultiplier(
        @FloatRange(from = 0.0, fromInclusive = false)
        value: Float,
    ) {
        pheromoneDepositMultiplier = value
    }

    @FloatRange(from = 0.0, to = 1.0)
    var pheromoneDepositRate: Float = 0.5F
        /** @throws IllegalArgumentException if the value is not between 0 and 1 */
        @FloatRange(from = 0.0, to = 1.0)
        set(value) {
            require(value in 0.0..1.0) { "Deposit rate must be between 0 and 1" }
            field = value
        }

    fun pheromoneDepositRate(@FloatRange(from = 0.0, to = 1.0) value: Float) {
        pheromoneDepositRate = value
    }

    var alpha
        @FloatRange(from = 0.0)
        get() = distanceImportance
        @FloatRange(from = 0.0)
        set(value) = distanceImportance(value)

    var beta
        get() = pheromoneImportance
        set(value) = pheromoneImportance(value)

    var rho
        @FloatRange(from = 0.0, to = 1.0)
        get() = pheromoneDepositRate
        @FloatRange(from = 0.0, to = 1.0)
        set(value) = pheromoneDepositRate(value)

    var qValue
        @FloatRange(from = 0.0, fromInclusive = false)
        get() = pheromoneDepositMultiplier
        @FloatRange(from = 0.0, fromInclusive = false)
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
