package me.danikvitek.lab1.util

import me.danikvitek.lab1.domain.model.MutableRoads

fun <T> List<T>.equalsElementWise(other: List<T>, itemsEqual: (T, T) -> Boolean = { a, b -> a == b }): Boolean {
    if (size != other.size) return false
    for (i in indices) {
        if (!itemsEqual(this[i], other[i])) return false
    }
    return true
}

fun <K, V> Map<K, V>.equalsElementWise(other: Map<K, V>, valuesEqual: (V, V) -> Boolean = { a, b -> a == b }): Boolean {
    if (size != other.size) return false
    if (this is MutableRoads && other is MutableRoads) {
        for (i in 0..<size) {
            val packed = packedRoads[i]
            val otherIdx = other.indexOfPacked(packed)
            if (otherIdx < 0) return false
            @Suppress("UNCHECKED_CAST")
            if (!valuesEqual(pheromones[i] as V, other.pheromones[otherIdx] as V)) return false
        }
        return true
    }
    for ((key, value) in this) {
        val otherValue = other[key] ?: if (!other.containsKey(key)) return false else null
        @Suppress("UNCHECKED_CAST")
        if (!valuesEqual(value, otherValue as V)) return false
    }
    return true
}

fun <T> Iterable<T>.hashCodeElementWise(): Int {
    var result = 1
    for (element in this) {
        result = 31 * result + element.hashCode()
    }
    return result
}

fun <K, V> Map<K, V>.hashCodeElementWise(): Int {
    var result = 1
    if (this is MutableRoads) {
        for (i in 0..<size) {
            result = 31 * result + packedRoads[i].hashCode()
            result = 31 * result + pheromones[i].hashCode()
        }
        return result
    }
    for ((key, value) in this) {
        result = 31 * result + key.hashCode()
        result = 31 * result + value.hashCode()
    }
    return result
}
