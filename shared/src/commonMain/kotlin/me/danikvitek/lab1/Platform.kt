package me.danikvitek.lab1

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform