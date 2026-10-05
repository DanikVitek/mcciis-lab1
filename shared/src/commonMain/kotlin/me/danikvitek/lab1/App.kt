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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.runBlocking
import me.danikvitek.lab1.domain.model.CyclicRoute
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.SimulationUIState
import me.danikvitek.lab1.presentation.SimulationViewModel
import me.danikvitek.lab1.util.SafeSeededRandom
import kotlin.random.Random

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
    val uiState = viewModel.uiState.collectAsState()
    val bestRoute = viewModel.bestRoute.collectAsState(initial = CyclicRoute())

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .then(Modifier.padding(16.dp)),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MapCanvas(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f, fill = true)
                .border(1.dp, MaterialTheme.colorScheme.onBackground),
            uiState = uiState,
            bestRoute = bestRoute,
        )
        SimulationControls(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 300.dp),
            uiState = uiState,
            bestRoute = bestRoute,
            simulationController = viewModel.SimulationController(),
        )
    }
}

@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    uiState: State<SimulationUIState>,
    bestRoute: State<CyclicRoute>,
) {
    val uiState by uiState
    val bestRoute by bestRoute

    val textMeasurer = rememberTextMeasurer()
    val cityLabelStyle = MaterialTheme.typography.labelSmall
    Canvas(
        modifier = modifier,
        contentDescription = "Ant colony simulation",
    ) {
        /** Padding from the canvas edges to draw points with */
        val imgPadding = 20.dp.toPx()

        fun Offset.toCanvasPos() = Offset(
            x = this.x * (size.width - 2 * imgPadding) + imgPadding,
            y = this.y * (size.height - 2 * imgPadding) + imgPadding,
        )

        for ((road, level) in uiState.roads) {
            val intensity = level.amount.coerceIn(0f, 1f)
            drawLine(
                color = lerp(Color.Blue.copy(alpha = 0f), Color.Red, intensity),
                start = uiState.cities[road.city1].toCanvasPos(),
                end = uiState.cities[road.city2].toCanvasPos(),
                strokeWidth = (intensity * 4.dp.toPx()).coerceAtLeast(1.dp.toPx()),
            )
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

        uiState.cities.map(Offset::toCanvasPos).forEachIndexed { idx, pos ->
            drawText(
                textLayoutResult = textMeasurer.measure(
                    text = idx.toString(),
                    maxLines = 1,
                    style = cityLabelStyle,
                ),
                topLeft = pos + Offset(4.dp.toPx(), 4.dp.toPx()),
                color = Color.Black,
            )
        }
    }
}

const val MIN_CITIES_AMOUNT = 2
const val MAX_CITIES_AMOUNT = 1000

@Composable
fun SimulationControls(
    modifier: Modifier = Modifier,
    uiState: State<SimulationUIState>,
    bestRoute: State<CyclicRoute>,
    simulationController: SimulationViewModel.SimulationController,
) {
    val uiState by uiState
    val bestRoute by bestRoute
    var simStatus by remember { mutableStateOf(SimulationStatus.Uninitialized) }

    LaunchedEffect(simStatus) {
        if (simStatus == SimulationStatus.Running)
            while (simulationController.step() == SimulationStatus.Running) {
//                delay(50.milliseconds)
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
    ) {
        var seed by remember { mutableStateOf("") }
        val rng: Random by derivedStateOf {
            seed.takeUnless { it.isBlank() }
                ?.let {
                    SafeSeededRandom(it.hashCode())
                } ?: Random.Default
        }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("RNG seed") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            value = seed,
            onValueChange = { seed = it.trim() },
            supportingText = {
                if (seed.isBlank()) {
                    Text("Blank seed will use a system's entropy")
                }
            },
        )

        var citiesAmountString by remember { mutableStateOf("10") }
        val citiesAmount by derivedStateOf { citiesAmountString.toIntOrNull() }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Number of cities") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            value = citiesAmountString,
            onValueChange = { citiesAmountString = it },
            isError = citiesAmountString.isNotEmpty() && (citiesAmount == null || citiesAmount !in MIN_CITIES_AMOUNT..MAX_CITIES_AMOUNT),
            supportingText = {
                if (citiesAmountString.isNotEmpty() && (citiesAmount == null || citiesAmount !in MIN_CITIES_AMOUNT..MAX_CITIES_AMOUNT)) {
                    Text("Must be a number between $MIN_CITIES_AMOUNT and $MAX_CITIES_AMOUNT")
                }
            },
        )

        var agesString by remember { mutableStateOf("50") }
        val ages by derivedStateOf { agesString.toIntOrNull() }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Number of ages") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            value = agesString,
            onValueChange = { agesString = it },
            isError = agesString.isNotEmpty() && run {
                val agesLocal = ages
                (agesLocal == null || agesLocal <= 0)
            },
            supportingText = {
                if (agesString.isNotEmpty()) {
                    val agesLocal = ages
                    if (agesLocal == null || agesLocal <= 0) {
                        Text("Must be a positive number")
                    }
                }
            },
        )

        var distanceImportanceString by remember { mutableStateOf("1") }
        val distanceImportance by derivedStateOf { distanceImportanceString.toFloatOrNull() }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Distance importance (alpha)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            value = distanceImportanceString,
            onValueChange = { distanceImportanceString = it },
            isError = distanceImportanceString.isNotEmpty() && run {
                val distanceImportanceLocal = distanceImportance
                (distanceImportanceLocal == null || distanceImportanceLocal < 0)
            },
            supportingText = {
                if (distanceImportanceString.isNotEmpty()) {
                    val distanceImportanceLocal = distanceImportance
                    if (distanceImportanceLocal == null || distanceImportanceLocal < 0) {
                        Text("Must be a non-negative number")
                    }
                }
            },
        )

        var pheromoneImportanceString by remember { mutableStateOf("3") }
        val pheromoneImportance by derivedStateOf { pheromoneImportanceString.toFloatOrNull() }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pheromone importance (beta)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            value = pheromoneImportanceString,
            onValueChange = { pheromoneImportanceString = it },
            isError = pheromoneImportanceString.isNotEmpty() && run {
                val pheromoneImportanceLocal = pheromoneImportance
                (pheromoneImportanceLocal == null || pheromoneImportanceLocal < 0)
            },
            supportingText = {
                if (pheromoneImportanceString.isNotEmpty()) {
                    val pheromoneImportanceLocal = pheromoneImportance
                    if (pheromoneImportanceLocal == null || pheromoneImportanceLocal < 0) {
                        Text("Must be a non-negative number")
                    }
                }
            },
        )

        var pheromoneDepositMultiplierString by remember { mutableStateOf("1") }
        val pheromoneDepositMultiplier by derivedStateOf { pheromoneDepositMultiplierString.toFloatOrNull() }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pheromone deposit multiplier (Q)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            value = pheromoneDepositMultiplierString,
            onValueChange = { pheromoneDepositMultiplierString = it },
            isError = pheromoneDepositMultiplierString.isNotEmpty() && run {
                val pheromoneDepositMultiplierLocal = pheromoneDepositMultiplier
                (pheromoneDepositMultiplierLocal == null || pheromoneDepositMultiplierLocal <= 0)
            },
            supportingText = {
                if (pheromoneDepositMultiplierString.isNotEmpty()) {
                    val pheromoneDepositMultiplierLocal = pheromoneDepositMultiplier
                    if (pheromoneDepositMultiplierLocal == null || pheromoneDepositMultiplierLocal <= 0) {
                        Text("Must be a positive number")
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val citiesAmountLocal = citiesAmount
                    if (citiesAmountLocal == null || citiesAmountLocal !in MIN_CITIES_AMOUNT..MAX_CITIES_AMOUNT) return@Button

                    val agesLocal = ages
                    if (agesLocal == null || agesLocal <= 0) return@Button

                    val pheromoneImportanceLocal = pheromoneImportance
                    if (pheromoneImportanceLocal == null || pheromoneImportanceLocal < 0) return@Button

                    val distanceImportanceLocal = distanceImportance
                    if (distanceImportanceLocal == null || distanceImportanceLocal < 0) return@Button

                    val pheromoneDepositMultiplierLocal = pheromoneDepositMultiplier
                    if (pheromoneDepositMultiplierLocal == null || pheromoneDepositMultiplierLocal <= 0) return@Button

                    runBlocking {
                        context(rng) {
                            simulationController.reset(
                                citiesAmount = citiesAmountLocal,
                                antsAmount = citiesAmountLocal,
                                ages = agesLocal,
                                distanceImportance = distanceImportanceLocal,
                                pheromoneImportance = pheromoneImportanceLocal,
                                pheromoneDepositMultiplier = pheromoneDepositMultiplierLocal,
                                preserveTopology = simStatus == SimulationStatus.Running || simStatus == SimulationStatus.Finished,
                            )
                        }
                        simStatus = SimulationStatus.NotStarted
                    }
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
                    text = if (simStatus == SimulationStatus.Running) (uiState.age + 1).toString() else "Run",
                )
            }
        }

        Column {
            val bestRouteLength by derivedStateOf {
                if (bestRoute.cities.isEmpty())
                    Float.POSITIVE_INFINITY
                else
                    bestRoute.cities.foldIndexed(0f) { index, acc, city ->
                        val nextCity = bestRoute.cities[(index + 1) % bestRoute.size]
                        acc + (uiState.cities[city] - uiState.cities[nextCity]).getDistance()
                    }
            }
            Text(
                text = "Shortest route length so far:",
            )
            Text(
                text = bestRouteLength.toString(),
            )
        }
    }
}
