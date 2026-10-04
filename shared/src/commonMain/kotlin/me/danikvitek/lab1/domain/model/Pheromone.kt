package me.danikvitek.lab1.domain.model

@JvmInline
value class Pheromone(val amount: Float): Comparable<Pheromone> {
    override operator fun compareTo(other: Pheromone) = amount.compareTo(other.amount)
}
