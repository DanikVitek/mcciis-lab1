package me.danikvitek.lab1.domain.model

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.BiFunction

/**
 * A set of graph edges, associated with some property. Used to obtain the set of vertices, accessible from the provided vertex.
 */
sealed interface Roads : Map<Road, Float> {
    fun accessibleCities(from: Int): PersistentMap<Int, Float>
}

class MutableRoads : AbstractMutableMap<Road, Float>, Roads {
    internal var packedRoads: LongArray
        private set
    internal var pheromones: FloatArray
        private set

    override var size: Int = 0
        private set

    /**
     * Non-zero when the roads represent an unmodified fully-connected graph of `0..<fullyConnectedCitiesCount`
     * stored in canonical `(i, j)` order (`0 <= i < j < n`), enabling O(1) arithmetic index lookup without hashing.
     */
    internal var fullyConnectedCitiesCount: Int = 0
        private set

    // Open-addressed hash table used only when not in canonical fully-connected mode.
    // Valid Road.packed values are always > 0L (since max(city1, city2) >= 1), so 0L represents an empty slot.
    private var hashKeys: LongArray = EMPTY_LONG_ARRAY
    private var hashIndices: IntArray = EMPTY_INT_ARRAY
    private var hashMask: Int = 0

    private val accessibleCitiesCache: ConcurrentMap<Int, PersistentMap<Int, Float>> = ConcurrentHashMap()

    constructor() {
        packedRoads = LongArray(16)
        pheromones = FloatArray(16)
        initHashTable(16)
    }

    constructor(roads: Map<Road, Float>) {
        val count = roads.size
        if (roads is MutableRoads) {
            packedRoads = roads.packedRoads.copyOf(count)
            pheromones = roads.pheromones.copyOf(count)
            size = count
            fullyConnectedCitiesCount = roads.fullyConnectedCitiesCount
            if (fullyConnectedCitiesCount == 0) {
                rebuildHashTable()
            }
        } else {
            val initialCap = maxOf(count, 16)
            packedRoads = LongArray(initialCap)
            pheromones = FloatArray(initialCap)
            initHashTable(initialCap)
            for ((road, value) in roads) {
                putInternal(road.packed, value)
            }
        }
    }

    internal constructor(citiesCount: Int, initialPheromoneLevel: Float) {
        val totalEdges = citiesCount * (citiesCount - 1) / 2
        val keys = LongArray(totalEdges)
        val values = FloatArray(totalEdges) { initialPheromoneLevel }
        var idx = 0
        for (i in 0..<citiesCount) {
            val iLong = i.toLong() and 0xFFFFFFFFL
            for (j in i + 1..<citiesCount) {
                keys[idx++] = (j.toLong() shl 32) or iLong
            }
        }
        packedRoads = keys
        pheromones = values
        size = totalEdges
        fullyConnectedCitiesCount = citiesCount
    }

    private fun initHashTable(expectedElements: Int) {
        val cap = tableCapacityFor(expectedElements)
        hashKeys = LongArray(cap)
        hashIndices = IntArray(cap)
        hashMask = cap - 1
    }

    private fun rebuildHashTable() {
        val cap = tableCapacityFor(maxOf(size, 16))
        val keys = LongArray(cap)
        val indices = IntArray(cap)
        val mask = cap - 1
        val roadKeys = packedRoads
        for (i in 0..<size) {
            val packed = roadKeys[i]
            var slot = mixHash(packed) and mask
            while (keys[slot] != 0L) {
                slot = (slot + 1) and mask
            }
            keys[slot] = packed
            indices[slot] = i
        }
        hashKeys = keys
        hashIndices = indices
        hashMask = mask
    }

    private fun ensureNotCanonicalFullyConnected() {
        if (fullyConnectedCitiesCount != 0) {
            fullyConnectedCitiesCount = 0
            rebuildHashTable()
        }
    }

    internal fun indexOfPacked(packedRoad: Long): Int {
        val n = fullyConnectedCitiesCount
        if (n > 0) {
            val c1 = packedRoad.toInt()
            val c2 = (packedRoad ushr 32).toInt()
            if (c1 < 0 || c2 >= n || c1 >= c2) return -1
            return c1 * n - (c1 * (c1 + 1) ushr 1) + (c2 - c1 - 1)
        }
        if (size == 0 || hashKeys.isEmpty()) return -1
        val keys = hashKeys
        val mask = hashMask
        var slot = mixHash(packedRoad) and mask
        while (true) {
            val k = keys[slot]
            if (k == 0L) return -1
            if (k == packedRoad) return hashIndices[slot]
            slot = (slot + 1) and mask
        }
    }

    internal fun indexOfCities(city1: Int, city2: Int): Int {
        if (city1 == city2) return -1
        val n = fullyConnectedCitiesCount
        if (n > 0) {
            val c1 = minOf(city1, city2)
            val c2 = maxOf(city1, city2)
            if (c1 < 0 || c2 >= n) return -1
            return c1 * n - (c1 * (c1 + 1) ushr 1) + (c2 - c1 - 1)
        }
        return indexOfPacked(Road.pack(city1, city2))
    }

    private fun putInternal(packedRoad: Long, value: Float): Float? {
        val existingIdx = indexOfPacked(packedRoad)
        if (existingIdx >= 0) {
            val old = pheromones[existingIdx]
            pheromones[existingIdx] = value
            return old
        }

        ensureNotCanonicalFullyConnected()

        val newIdx = size
        if (newIdx == packedRoads.size) {
            val newCap = maxOf(16, packedRoads.size * 2)
            packedRoads = packedRoads.copyOf(newCap)
            pheromones = pheromones.copyOf(newCap)
        }
        packedRoads[newIdx] = packedRoad
        pheromones[newIdx] = value
        size = newIdx + 1

        if ((size * 4) >= (hashKeys.size * 3)) {
            rebuildHashTable()
        } else {
            val keys = hashKeys
            val mask = hashMask
            var slot = mixHash(packedRoad) and mask
            while (keys[slot] != 0L) {
                slot = (slot + 1) and mask
            }
            keys[slot] = packedRoad
            hashIndices[slot] = newIdx
        }
        return null
    }

    private fun invalidateCacheFor(packedRoad: Long) {
        if (accessibleCitiesCache.isNotEmpty()) {
            accessibleCitiesCache.remove(packedRoad.toInt())
            accessibleCitiesCache.remove((packedRoad ushr 32).toInt())
        }
    }

    override fun accessibleCities(from: Int): PersistentMap<Int, Float> = accessibleCitiesCache.getOrPut(from) {
        persistentHashMapOf<Int, Float>().mutate { map ->
            val keys = packedRoads
            val values = pheromones
            for (i in 0..<size) {
                val road = keys[i]
                val c1 = road.toInt()
                val c2 = (road ushr 32).toInt()
                if (c1 == from) {
                    map[c2] = values[i]
                } else if (c2 == from) {
                    map[c1] = values[i]
                }
            }
        }
    }

    override fun containsKey(key: Road): Boolean = indexOfPacked(key.packed) >= 0

    override fun get(key: Road): Float? {
        val idx = indexOfPacked(key.packed)
        return if (idx >= 0) pheromones[idx] else null
    }

    override fun put(key: Road, value: Float): Float? {
        val prev = putInternal(key.packed, value)
        invalidateCacheFor(key.packed)
        return prev
    }

    fun put(city1: Int, city2: Int, value: Float): Float? {
        val packed = Road.pack(city1, city2)
        val prev = putInternal(packed, value)
        invalidateCacheFor(packed)
        return prev
    }

    override fun remove(key: Road): Float? = removePacked(key.packed)

    fun remove(city1: Int, city2: Int): Float? = removePacked(Road.pack(city1, city2))

    private fun removePacked(packedRoad: Long): Float? {
        val idx = indexOfPacked(packedRoad)
        if (idx < 0) return null

        ensureNotCanonicalFullyConnected()
        val oldValue = pheromones[idx]
        val lastIdx = size - 1
        if (idx != lastIdx) {
            packedRoads[idx] = packedRoads[lastIdx]
            pheromones[idx] = pheromones[lastIdx]
        }
        size = lastIdx
        rebuildHashTable()
        invalidateCacheFor(packedRoad)
        return oldValue
    }

    fun computeIfPresent(city1: Int, city2: Int, transform: (Float) -> Float) {
        val packed = Road.pack(city1, city2)
        val idx = indexOfPacked(packed)
        if (idx >= 0) {
            pheromones[idx] = transform(pheromones[idx])
            invalidateCacheFor(packed)
        }
    }

    override fun replaceAll(function: BiFunction<in Road, in Float, out Float>) {
        val keys = packedRoads
        val values = pheromones
        for (i in 0..<size) {
            values[i] = function.apply(Road(keys[i]), values[i])
        }
        if (accessibleCitiesCache.isNotEmpty()) {
            accessibleCitiesCache.clear()
        }
    }

    internal fun updateAllPheromones(evaporationRate: Float, deltas: FloatArray) {
        val values = pheromones
        for (i in 0..<size) {
            values[i] = evaporationRate * values[i] + deltas[i]
        }
        if (accessibleCitiesCache.isNotEmpty()) {
            accessibleCitiesCache.clear()
        }
    }

    internal fun copy(): MutableRoads {
        val copy = MutableRoads()
        copy.packedRoads = packedRoads.copyOf(size)
        copy.pheromones = pheromones.copyOf(size)
        copy.size = size
        copy.fullyConnectedCitiesCount = fullyConnectedCitiesCount
        if (fullyConnectedCitiesCount == 0) {
            copy.hashKeys = hashKeys.copyOf()
            copy.hashIndices = hashIndices.copyOf()
            copy.hashMask = hashMask
        }
        return copy
    }

    internal fun resetAllPheromones(initialPheromoneLevel: Float): MutableRoads {
        val copy = MutableRoads()
        copy.packedRoads = packedRoads.copyOf(size)
        copy.pheromones = FloatArray(size) { initialPheromoneLevel }
        copy.size = size
        copy.fullyConnectedCitiesCount = fullyConnectedCitiesCount
        if (fullyConnectedCitiesCount == 0) {
            copy.hashKeys = hashKeys.copyOf()
            copy.hashIndices = hashIndices.copyOf()
            copy.hashMask = hashMask
        }
        return copy
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Road, Float>> =
        object : AbstractMutableSet<MutableMap.MutableEntry<Road, Float>>() {
            override val size: Int get() = this@MutableRoads.size

            override fun add(element: MutableMap.MutableEntry<Road, Float>): Boolean {
                val prev = put(element.key, element.value)
                return prev != element.value
            }

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<Road, Float>> =
                object : MutableIterator<MutableMap.MutableEntry<Road, Float>> {
                    private var index = 0
                    private var lastReturned = -1

                    override fun hasNext(): Boolean = index < this@MutableRoads.size

                    override fun next(): MutableMap.MutableEntry<Road, Float> {
                        if (index >= this@MutableRoads.size) throw NoSuchElementException()
                        lastReturned = index++
                        return EntryView(lastReturned)
                    }

                    override fun remove() {
                        check(lastReturned >= 0) { "next() must be called before remove()" }
                        removePacked(packedRoads[lastReturned])
                        index = lastReturned
                        lastReturned = -1
                    }
                }
        }

    private inner class EntryView(private val index: Int) : MutableMap.MutableEntry<Road, Float> {
        override val key: Road
            get() = Road(packedRoads[index])
        override val value: Float
            get() = pheromones[index]

        override fun setValue(newValue: Float): Float {
            val old = pheromones[index]
            pheromones[index] = newValue
            invalidateCacheFor(packedRoads[index])
            return old
        }

        override fun equals(other: Any?): Boolean {
            return other is Map.Entry<*, *> && other.key == key && other.value == value
        }

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
        override fun toString(): String = "$key=$value"
    }

    companion object {
        private val EMPTY_LONG_ARRAY = LongArray(0)
        private val EMPTY_INT_ARRAY = IntArray(0)

        private fun mixHash(packed: Long): Int {
            val h = (packed xor (packed ushr 32)) * -0x61c8864680b583ebL
            return (h xor (h ushr 32)).toInt()
        }

        private fun tableCapacityFor(expectedElements: Int): Int {
            val minCap = maxOf(16, ((expectedElements * 4) / 3) + 1)
            return minCap.takeHighestOneBit().let { if (it < minCap) it shl 1 else it }
        }
    }
}
