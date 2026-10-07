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
): R {
    return when (size) {
        0 -> initial()
        1 -> aggregateItem(
            initial(),
            this[0].await(),
        )

        else -> {
            val mid = size / 2
            val left = subList(0, mid)
            val right = subList(mid, size)

            aggregateAccumulators(
                left.treeFold(initial, aggregateItem, aggregateAccumulators),
                right.treeFold(initial, aggregateItem, aggregateAccumulators),
            )
        }
    }
}

suspend fun <T, R : T> List<Deferred<T>>.treeFold(
    initial: () -> R,
    convolve: suspend (acc: R, item: T) -> R,
): R {
    return when (size) {
        0 -> initial()
        1 -> convolve(
            initial(),
            this[0].await(),
        )

        else -> {
            val mid = size / 2
            val left = subList(0, mid)
            val right = subList(mid, size)

            convolve(
                left.treeFold(initial, convolve),
                right.treeFold(initial, convolve),
            )
        }
    }
}

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
        else -> treeReduce2(convolve)
    }
}

suspend fun <T> List<Deferred<T>>.treeReduceOrNull(
    convolve: suspend (left: T, right: T) -> T,
): T? {
    return when (size) {
        0 -> null
        1 -> this[0].await()
        else -> treeReduce2(convolve)
    }
}

/**
 * Used to implement [treeReduce] and [treeReduceOrNull].
 * It assumes that the list has at least two elements.
 */
private suspend fun <T> List<Deferred<T>>.treeReduce2(
    convolve: suspend (left: T, right: T) -> T,
): T {
    val mid = size / 2
    val left = subList(0, mid)
    val right = subList(mid, size)

    return convolve(
        left.treeReduce(convolve),
        right.treeReduce(convolve),
    )
}
