package me.danikvitek.lab1

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.SimulationViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
@Preview
fun App() {
    AntColonyAppTheme {
        AntColonyApp()
    }
}

@Composable
fun AntColonyAppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme {
        content()
    }
}

@Composable
fun AntColonyApp(
    viewModel: SimulationViewModel = viewModel { SimulationViewModel() },
) {
    var citiesAmount by remember { mutableIntStateOf(10) }
    val uiState by viewModel.uiState.collectAsState()
    val bestRoute by viewModel.bestRoute.collectAsState(initial = emptyList())
    var simStatus by remember { mutableStateOf(SimulationStatus.Uninitialized) }

    LaunchedEffect(simStatus) {
        while (simStatus == SimulationStatus.Running) {
            viewModel.step()
            delay(50.milliseconds)
        }
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().then(Modifier.padding(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = getPlatform().name,
        )

        // Створюємо список анімованих позицій ДО виклику Canvas
        val animatedAnts = uiState.ants.map {
            animateOffsetAsState(
                targetValue = it,
                animationSpec = tween(durationMillis = 45, easing = LinearEasing),
                label = "ant_movement",
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .border(1.dp, MaterialTheme.colorScheme.onBackground),
            contentDescription = "Ant colony simulation",
        ) {
            /** Padding from the canvas edges to draw points with */
            val imgPadding = 8.dp.toPx()

            fun Offset.toCanvasPos() = Offset(
                x = this.x * (size.width - 2 * imgPadding) + imgPadding,
                y = this.y * (size.height - 2 * imgPadding) + imgPadding,
            )

            if (uiState.pheromones.isNotEmpty()) {
                val maxPheromone = uiState.pheromones.maxOf { it.maxOrNull() ?: 0f }
                for (i in uiState.cities.indices) {
                    for (j in uiState.cities.indices) {
                        val level = uiState.pheromones[i][j]
                        if (i != j && level > 0.01f) { // Малюємо лише якщо є феромон
                            val intensity = (level / maxPheromone).coerceIn(0f, 1f)
                            drawLine(
                                color = Color.Green.copy(alpha = intensity),
                                start = uiState.cities[i].toCanvasPos(),
                                end = uiState.cities[j].toCanvasPos(),
                                strokeWidth = (intensity * 4.dp.toPx()).coerceAtLeast(1f),
                            )
                        }
                    }
                }
            }

            bestRoute.takeUnless { it.isEmpty() }?.forEachIndexed { idx, it ->
                drawLine(
                    color = Color.Blue,
                    start = it.toCanvasPos(),
                    end = bestRoute[(idx + 1) % bestRoute.size].toCanvasPos(),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            drawPoints(
                points = uiState.cities.map(Offset::toCanvasPos),
                pointMode = PointMode.Points,
                color = Color.Black,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )

            drawPoints(
                points = animatedAnts.map { it.value.toCanvasPos() },
                pointMode = PointMode.Points,
                color = Color.Red,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            TextField(
//                modifier = Modifier.fi
                label = { Text("Number of cities") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                value = citiesAmount.toString(),
                onValueChange = { newValue ->
                    citiesAmount = newValue.toIntOrNull()?.coerceIn(2, 100) ?: citiesAmount
                },
            )
            Button(
                onClick = {
                    viewModel.reset(citiesAmount = citiesAmount, antsAmount = citiesAmount)
                    simStatus = SimulationStatus.NotStarted
                },
            ) {
                Text(
                    text = if (simStatus == SimulationStatus.Uninitialized) "Init" else "Reset",
                )
            }
            Button(
                enabled = simStatus == SimulationStatus.NotStarted,
                onClick = { simStatus = SimulationStatus.Running },
            ) {
                Text(
                    text = if (simStatus == SimulationStatus.Running) uiState.age.toString() else "Run",
                )
            }
        }
    }
}