package me.danikvitek.lab1

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import me.danikvitek.lab1.domain.model.CyclicRoute
import me.danikvitek.lab1.domain.model.Road
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.SimulationViewModel
import kotlin.random.Random
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntColonyApp(
    viewModel: SimulationViewModel = viewModel { SimulationViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val bestRoute by viewModel.bestRoute.collectAsState(initial = CyclicRoute())
    var simStatus by remember { mutableStateOf(SimulationStatus.Uninitialized) }

    LaunchedEffect(simStatus) {
        while (simStatus == SimulationStatus.Running) {
            viewModel.step()
            delay(50.milliseconds)
        }
    }

    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().then(Modifier.padding(16.dp))
            ,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
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

            if (uiState.roads.isNotEmpty()) {
                for (i in uiState.cities.indices) {
                    for (j in i + 1..<uiState.cities.size) {
                        val level = uiState.roads[Road(i, j)]
                        if (level != null) { // Малюємо лише якщо є феромон
                            val intensity = level.amount.coerceIn(0f, 1f)
                            drawLine(
                                color = lerp(Color.Blue, Color.Red, intensity),
                                start = uiState.cities[i].toCanvasPos(),
                                end = uiState.cities[j].toCanvasPos(),
                                strokeWidth = (intensity * 4.dp.toPx()).coerceAtLeast(1.dp.toPx()),
                            )
                        }
                    }
                }
            }

            bestRoute.cities.takeUnless { it.isEmpty() }?.forEachIndexed { idx, it ->
                drawLine(
                    color = Color.Green,
                    start = uiState.cities[it].toCanvasPos(),
                    end = uiState.cities[bestRoute.cities[(idx + 1) % bestRoute.size]].toCanvasPos(),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            drawPoints(
                points = uiState.cities.map(Offset::toCanvasPos),
                pointMode = PointMode.Points,
                color = Color.Black,
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
        ) {
            var seedString by remember { mutableStateOf("") }
            val seed by derivedStateOf { seedString.toIntOrNull() }

            Row {
                var withSeed by remember { mutableStateOf(false) }

                Label(label = { Text("Use RNG seed") }) {
                    Checkbox(
                        checked = withSeed,
                        onCheckedChange = { withSeed = it },
                    )
                }
                TextField(
                    label = { Text("RNG seed") },
                    singleLine = true,
                    enabled = withSeed,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    value = seedString,
                    onValueChange = { seedString = it },
                    isError = withSeed && seedString.isNotEmpty() && seed == null,
                    supportingText = {
                        if (withSeed && seedString.isNotEmpty() && seed == null) {
                            Text("Must be a number")
                        }
                    },
                )
            }
            Row {
                val rng by derivedStateOf { seed?.let(::Random) ?: Random.Default }
                var citiesAmountString by remember { mutableStateOf("10") }
                val citiesAmount by derivedStateOf { citiesAmountString.toIntOrNull() }

                TextField(
                    label = { Text("Number of cities") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    value = citiesAmountString,
                    onValueChange = { citiesAmountString = it },
                    isError = citiesAmountString.isNotEmpty() && (citiesAmount == null || citiesAmount !in 2..100),
                    supportingText = {
                        if (citiesAmountString.isNotEmpty() && (citiesAmount == null || citiesAmount !in 2..100)) {
                            Text("Must be a number between 2 and 100")
                        }
                    },
                )
                Button(
                    onClick = {
                        val citiesAmountLocal = citiesAmount
                        if (citiesAmountLocal == null || citiesAmountLocal !in 2..100) return@Button
                        context(rng) { viewModel.reset(citiesAmount = citiesAmountLocal, antsAmount = citiesAmountLocal) }
                        simStatus = SimulationStatus.NotStarted
                    },
                ) {
                    Text(
                        text = if (simStatus == SimulationStatus.Uninitialized) "Init" else "Reset",
                    )
                }
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