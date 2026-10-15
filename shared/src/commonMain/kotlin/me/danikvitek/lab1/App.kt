package me.danikvitek.lab1

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.runBlocking
import me.danikvitek.lab1.data.PlotProvider
import me.danikvitek.lab1.domain.model.CyclicRoute
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.SimulationUIState
import me.danikvitek.lab1.presentation.SimulationViewModel
import me.danikvitek.lab1.util.SafeSeededRandom
import kotlin.math.ceil
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

enum class CanvasOrPlot {
    CANVAS, PLOT
}

@Composable
fun AntColonyApp(
    viewModel: SimulationViewModel = viewModel { SimulationViewModel() },
) {
    val uiState = viewModel.uiState.collectAsState()
    val plotProvider = derivedStateOf { viewModel.plot(uiState.value.cities.size) }
    val bestRoute = viewModel.bestRoute.collectAsState(initial = CyclicRoute())

    val showPheromones = remember { mutableStateOf(true) }
    val showBestRoute = remember { mutableStateOf(true) }
    var canvasOrPlot by remember { mutableStateOf(CanvasOrPlot.CANVAS) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .then(Modifier.padding(16.dp)),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Canvas")
                Switch(
                    checked = canvasOrPlot == CanvasOrPlot.PLOT,
                    onCheckedChange = { canvasOrPlot = if (it) CanvasOrPlot.PLOT else CanvasOrPlot.CANVAS },
                )
                Text("Plot")
            }
            when (canvasOrPlot) {
                CanvasOrPlot.CANVAS ->
                    MapCanvas(
                        modifier = Modifier
//                .fillMaxHeight()
                            .aspectRatio(1F)
//                .weight(1f, fill = true)
                            .border(1.dp, MaterialTheme.colorScheme.onBackground),
                        uiState = uiState,
                        bestRoute = bestRoute,
                        showPheromones = showPheromones,
                        showBestRoute = showBestRoute,
                    )

                CanvasOrPlot.PLOT ->
                    Image(
                        modifier = Modifier,
                        bitmap = plotProvider.value.png
                            .decodeToImageBitmap(),
                        contentDescription = "Plot of the length of the shortest path found so far",
                    )
            }
        }

        SimulationControls(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 300.dp),
            uiState = uiState,
            bestRoute = bestRoute,
            simulationController = viewModel.SimulationController(),
            showPheromones = showPheromones,
            showBestRoute = showBestRoute,
            plotProvider = plotProvider,
        )
    }
}

@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    uiState: State<SimulationUIState>,
    bestRoute: State<CyclicRoute>,
    showPheromones: State<Boolean>,
    showBestRoute: State<Boolean>,
) {
    val uiState by uiState
    val bestRoute by bestRoute

    val textMeasurer = rememberTextMeasurer()
    val cityLabelStyle = MaterialTheme.typography.labelSmall

    val showPheromones by showPheromones
    val showBestRoute by showBestRoute
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

        if (showPheromones) {
            val maxLevel = uiState.roads.maxOfOrNull { it.value }
            for ((road, level) in uiState.roads) {
                val intensity = level / maxLevel!!

                val lineStart = uiState.cities[road.city1].toCanvasPos()
                val lineEnd = uiState.cities[road.city2].toCanvasPos()

                val lineMid = (lineStart + lineEnd) / 2f;
                val lineNormal = (lineEnd - lineStart).let { it / it.getDistance() }.let { Offset(-it.y, it.x) }

                val brushWidth = (intensity * 10.dp.toPx()).coerceAtLeast(1.dp.toPx())
                val brushStart = lineMid - lineNormal * brushWidth
                val brushEnd = lineMid + lineNormal * brushWidth

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.0625f), intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.25f), intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red, intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.25f), intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.0625f), intensity),
                            Color.Transparent,
                        ),
                        start = brushStart,
                        end = brushEnd,
                    ),
                    start = lineStart,
                    end = lineEnd,
                    strokeWidth = brushWidth,
                )
            }
        }

        if (showBestRoute) {
            bestRoute.cities.takeUnless { it.isEmpty() }?.forEachIndexed { idx, it ->
                drawLine(
                    color = Color.Green,
                    start = uiState.cities[it].toCanvasPos(),
                    end = uiState.cities[bestRoute.cities[(idx + 1) % bestRoute.size]].toCanvasPos(),
                    strokeWidth = 2.dp.toPx(),
                )
            }
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
    showPheromones: MutableState<Boolean>,
    showBestRoute: MutableState<Boolean>,
    plotProvider: State<PlotProvider>,
) {
    val uiState by uiState
    val bestRoute by bestRoute
    var simStatus by remember { mutableStateOf(SimulationStatus.UNINITIALIZED) }
    var showPheromones by showPheromones
    var showBestRoute by showBestRoute

    LaunchedEffect(simStatus) {
        if (simStatus == SimulationStatus.RUNNING) {
            do {
                val result = simulationController.step()
            } while (result == SimulationStatus.RUNNING)
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

        val citiesAmount = rememberIntFieldState("10", Constraint.RangeInt(MIN_CITIES_AMOUNT..MAX_CITIES_AMOUNT))
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = citiesAmount,
            label = "Number of cities",
        )

        val antsPercent = rememberFloatFieldState("100", Constraint.PositiveFloat)
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = antsPercent,
            label = "% of ants per cities amount",
        )

        val ages = rememberIntFieldState("50", Constraint.PositiveInt)
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = ages,
            label = "Number of ages",
        )

        val distanceImportance = rememberFloatFieldState("1", Constraint.NonNegativeFloat)
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = distanceImportance,
            label = "Distance importance (alpha)",
        )

        val pheromoneImportance = rememberFloatFieldState("3", Constraint.NonNegativeFloat)
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = pheromoneImportance,
            label = "Pheromone importance (beta)",
        )

        val pheromoneDepositMultiplier = rememberFloatFieldState("1", Constraint.PositiveFloat)
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = pheromoneDepositMultiplier,
            label = "Pheromone deposit multiplier (Q)",
        )

        val initialPheromoneLevel = rememberFloatFieldState("0.01", Constraint.PositiveFloat)
        ValidatedField(
            modifier = Modifier.fillMaxWidth(),
            state = initialPheromoneLevel,
            label = "Initial pheromone level",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val citiesAmount = citiesAmount.value ?: return@Button
                    val antsAmount = run {
                        val antsPercent = antsPercent.value ?: return@Button
                        ceil(citiesAmount * (antsPercent / 100)).toInt()
                    }
                    val agesLocal = ages.value ?: return@Button
                    val distanceImportance = distanceImportance.value ?: return@Button
                    val pheromoneImportance = pheromoneImportance.value ?: return@Button
                    val pheromoneDepositMultiplier = pheromoneDepositMultiplier.value ?: return@Button
                    val initialPheromoneLevel = initialPheromoneLevel.value ?: return@Button

                    runBlocking {
                        context(rng) {
                            simulationController.reset(
                                citiesAmount = citiesAmount,
                                antsAmount = antsAmount,
                                ages = agesLocal,
                                distanceImportance = distanceImportance,
                                pheromoneImportance = pheromoneImportance,
                                pheromoneDepositMultiplier = pheromoneDepositMultiplier,
                                initialPheromoneLevel = initialPheromoneLevel,
                                preserveTopology = simStatus == SimulationStatus.RUNNING || simStatus == SimulationStatus.FINISHED,
                            )
                        }
                        simStatus = SimulationStatus.NOT_STARTED
                    }
                },
            ) {
                Text(
                    text = if (simStatus == SimulationStatus.UNINITIALIZED) "Init" else "Reset",
                )
            }
            Button(
                enabled = simStatus == SimulationStatus.NOT_STARTED,
                onClick = { simStatus = SimulationStatus.RUNNING },
            ) {
                Text(
                    text = if (simStatus == SimulationStatus.RUNNING) (uiState.age + 1).toString() else "Run",
                )
            }
        }

        Column {
            Text(
                text = "Shortest route length so far:",
            )
            Text(
                text = bestRoute.length.toString(),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = showPheromones,
                onCheckedChange = { showPheromones = it },
            )
            Text(text = "Show pheromones")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = showBestRoute,
                onCheckedChange = { showBestRoute = it },
            )
            Text(text = "Show best route")
        }
        Button(
            onClick = plotProvider.value::save,
        ) {
            Text(text = "Save plot")
        }
    }
}

abstract class FieldState<T : Any>(
    initialValue: String,
    internal val constraint: Constraint<T>,
    conversion: (String) -> T?,
) {
    var text by mutableStateOf(initialValue)
        internal set

    val value: T? by derivedStateOf { conversion(text).takeIf(constraint::check) }
}

class FloatFieldState(initialValue: String, constraint: Constraint<Float>) : FieldState<Float>(
    initialValue, constraint,
    String::toFloatOrNull,
)

class IntFieldState(initialValue: String, constraint: Constraint<Int>) : FieldState<Int>(
    initialValue, constraint,
    String::toIntOrNull,
)

@Composable
fun rememberIntFieldState(initialValue: String, constraint: Constraint<Int>) =
    remember { IntFieldState(initialValue, constraint) }

@Composable
fun rememberFloatFieldState(initialValue: String, constraint: Constraint<Float>) =
    remember { FloatFieldState(initialValue, constraint) }

@Composable
fun <T : Any> ValidatedField(
    modifier: Modifier,
    state: FieldState<T>,
    label: String,
) {
    TextField(
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = state.constraint.keyboardType),
        value = state.text,
        onValueChange = { state.text = it },
        isError = state.value == null,
        supportingText = {
            if (state.value == null) {
                Text(state.constraint.message)
            }
        },
    )
}

interface Constraint<T> {
    fun check(value: T?): Boolean
    val message: String
    val keyboardType: KeyboardType

    object PositiveInt : Constraint<Int> {
        override fun check(value: Int?) = value.let { it != null && it > 0 }
        override val message = "Must be a positive integer"
        override val keyboardType = KeyboardType.Number
    }

    object NonNegativeInt : Constraint<Int> {
        override fun check(value: Int?) = value.let { it != null && it >= 0 }
        override val message = "Must be a non-negative integer"
        override val keyboardType = KeyboardType.Number
    }

    data class RangeInt(private val range: IntRange) : Constraint<Int> {
        override fun check(value: Int?) = value.let { it != null && it in range }
        override val message by lazy { "Must be an integer in range $range" }
        override val keyboardType = KeyboardType.Number
    }

    object PositiveFloat : Constraint<Float> {
        override fun check(value: Float?) = value.let { it != null && it > 0 }
        override val message = "Must be a positive FP number"
        override val keyboardType = KeyboardType.Decimal
    }

    object NonNegativeFloat : Constraint<Float> {
        override fun check(value: Float?) = value.let { it != null && it >= 0 }
        override val message = "Must be a non-negative FP number"
        override val keyboardType = KeyboardType.Decimal
    }
}

