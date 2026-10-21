package me.danikvitek.lab1.util

import kotlinx.coroutines.Deferred

/**
 * Folds a list of deferred values into a single value using a tree-like structure.
 * This allows for parallel processing of the deferred values, potentially improving performance.
 *
 * @param initial A function that returns the initial value of the accumulator. It may be called multiple times.
 * @param aggregateItem A function that aggregates an item into the accumulator.
 * @param aggregateAccumulators A function that aggregates two accumulators together.
 * @return The result of folding the list of deferred values into a single value.
 */
suspend fun <T, R> List<Deferred<T>>.treeFold(
    initial: () -> R,
    aggregateItem: suspend (acc: R, item: T) -> R,
    aggregateAccumulators: suspend (left: R, right: R) -> R,
): R = treeFoldRange(0, size, initial, aggregateItem, aggregateAccumulators)

private suspend fun <T, R> List<Deferred<T>>.treeFoldRange(
    fromIndex: Int,
    toIndex: Int,
    initial: () -> R,
    aggregateItem: suspend (acc: R, item: T) -> R,
    aggregateAccumulators: suspend (left: R, right: R) -> R,
): R {
    return when (val length = toIndex - fromIndex) {
        0 -> initial()
        1 -> aggregateItem(
            initial(),
            this[fromIndex].await(),
        )

        else -> {
            val mid = fromIndex + (length ushr 1)
            aggregateAccumulators(
                treeFoldRange(fromIndex, mid, initial, aggregateItem, aggregateAccumulators),
                treeFoldRange(mid, toIndex, initial, aggregateItem, aggregateAccumulators),
            )
        }
    }
}

suspend fun <T, R : T> List<Deferred<T>>.treeFold(
    initial: () -> R,
    convolve: suspend (acc: R, item: T) -> R,
): R = treeFoldRange(0, size, initial, convolve, convolve)

/**
 * Reduces a list of deferred values into a single value using a tree-like structure.
 * This allows for parallel processing of the deferred values, potentially improving performance.
 *
 * @param convolve A function that aggregates two items together.
 * @return The result of reducing the list of deferred values into a single value.
 * @throws UnsupportedOperationException if the list is empty. In case it is possible for the list to be empty,
 * consider using [treeReduceOrNull] instead.
 */
suspend fun <T> List<Deferred<T>>.treeReduce(
    convolve: suspend (left: T, right: T) -> T,
): T {
    return when (size) {
        0 -> throw UnsupportedOperationException("Cannot reduce an empty list")
        1 -> this[0].await()
        else -> treeReduceRange(0, size, convolve)
    }
}

suspend fun <T> List<Deferred<T>>.treeReduceOrNull(
    convolve: suspend (left: T, right: T) -> T,
): T? {
    return when (size) {
        0 -> null
        1 -> this[0].await()
        else -> treeReduceRange(0, size, convolve)
    }
}

/**
 * Used to implement [treeReduce] and [treeReduceOrNull].
 * It assumes that the range has at least two elements.
 */
private suspend fun <T> List<Deferred<T>>.treeReduceRange(
    fromIndex: Int,
    toIndex: Int,
    convolve: suspend (left: T, right: T) -> T,
): T {
    val length = toIndex - fromIndex
    if (length == 1) return this[fromIndex].await()
    val mid = fromIndex + (length ushr 1)

    return convolve(
        treeReduceRange(fromIndex, mid, convolve),
        treeReduceRange(mid, toIndex, convolve),
    )
}
