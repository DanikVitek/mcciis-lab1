package me.danikvitek.lab1

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication

fun main() = singleWindowApplication(
    state = WindowState(width = 1600.dp, height = 960.dp),
    title = "Lab 1 - Ant Colony",
) {
    App()
}
