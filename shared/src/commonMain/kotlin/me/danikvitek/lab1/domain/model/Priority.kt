package me.danikvitek.lab1.domain.model

@JvmInline
value class Priority(val value: Float): Comparable<Priority> {
    init {
        require(value >= 0f) { "Priority must be non-negative" }
    }

    override operator fun compareTo(other: Priority): Int = value.compareTo(other.value)

    operator fun plus(other: Priority): Priority = Priority(value + other.value)
    operator fun minus(other: Priority): Priority = Priority(value - other.value)

    operator fun times(other: Float): Priority = Priority(this.value * other)
    operator fun div(other: Float): Priority = Priority(this.value / other)
}