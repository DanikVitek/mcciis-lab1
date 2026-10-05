package me.danikvitek.lab1.util

import kotlin.random.Random

expect class SafeSeededRandom(seed: Long) : Random {
    constructor(seed: Int)
}