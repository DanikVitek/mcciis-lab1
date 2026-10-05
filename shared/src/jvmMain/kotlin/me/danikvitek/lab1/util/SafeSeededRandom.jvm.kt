package me.danikvitek.lab1.util

import kotlin.random.Random

actual class SafeSeededRandom actual constructor(seed: Long) : Random() {
    actual constructor(seed: Int) : this(seed.toLong())

    private val implStorage = object : ThreadLocal<java.util.Random>() {
        override fun initialValue(): java.util.Random = java.util.Random(seed)
    }

    private val impl: java.util.Random
        get() = implStorage.get()

    override fun nextBits(bitCount: Int): Int {
        return impl.nextInt(1 shl bitCount)
    }
}