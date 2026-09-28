package me.danikvitek.lab1.domain

import androidx.compose.ui.geometry.Offset

class Ant(
    val currentPosition: Offset,
    val nextPosition: Offset = Offset.Unspecified,
    val visited: List<Offset> = emptyList(),
)