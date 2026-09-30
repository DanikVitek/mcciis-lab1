package me.danikvitek.lab1

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lab 1 - Ant Colony",
    ) {
        App()
    }
}