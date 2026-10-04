package me.danikvitek.lab1.util

fun <T> List<T>.equalsElementWise(other: List<T>, itemsEqual: (T, T) -> Boolean = { a, b -> a == b }): Boolean {
    if (size != other.size) return false
    if (asSequence().withIndex().any { it.value != other[it.index] }) return false
    return true
}

fun <K, V> Map<K, V>.equalsElementWise(other: Map<K, V>, valuesEqual: (V, V) -> Boolean = { a, b -> a == b }): Boolean {
    if (size != other.size) return false
    if (asSequence().any { (key, value) -> other[key] != value }) return false
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
    for ((key, value) in this) {
        result = 31 * result + key.hashCode()
        result = 31 * result + value.hashCode()
    }
    return result
}