package me.danikvitek.lab1.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.runBlocking
import lab1.shared.generated.resources.*
import me.danikvitek.lab1.CanvasOrPlot
import me.danikvitek.lab1.data.PlotProvider
import me.danikvitek.lab1.domain.model.CyclicRoute
import me.danikvitek.lab1.presentation.MapState
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.SimulationViewModel
import me.danikvitek.lab1.presentation.fieldState.Constraint
import me.danikvitek.lab1.presentation.fieldState.ValidatedField
import me.danikvitek.lab1.presentation.fieldState.rememberFloatFieldState
import me.danikvitek.lab1.presentation.fieldState.rememberIntFieldState
import me.danikvitek.lab1.util.SafeSeededRandom
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random

@Composable
fun ExperimentRunnerScreen(
    viewModel: SimulationViewModel = viewModel { SimulationViewModel() },
) {
    val mapState = viewModel.mapState.collectAsState()
    val map by mapState

    val bestRouteState = viewModel.bestRouteState.collectAsState()
    val bestRoute by bestRouteState

    val plotProviderState = remember(bestRoute) { derivedStateOf { viewModel.plot(map.cities.size) } }
    val plotProvider by plotProviderState

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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = canvasOrPlot.ordinal) {
                Tab(
                    selected = canvasOrPlot == CanvasOrPlot.CANVAS,
                    onClick = { canvasOrPlot = CanvasOrPlot.CANVAS },
                    text = { Text(stringResource(Res.string.tab_graph)) },
                )
                Tab(
                    selected = canvasOrPlot == CanvasOrPlot.PLOT,
                    onClick = { canvasOrPlot = CanvasOrPlot.PLOT },
                    text = { Text(stringResource(Res.string.tab_plot)) },
                )
            }
            when (canvasOrPlot) {
                CanvasOrPlot.CANVAS ->
                    MapCanvas(
                        modifier = Modifier
                            .aspectRatio(1F)
                            .border(1.dp, MaterialTheme.colorScheme.onBackground),
                        uiState = mapState,
                        bestRouteState = bestRouteState,
                        showPheromones = showPheromones,
                        showBestRoute = showBestRoute,
                    )

                CanvasOrPlot.PLOT ->
                    Image(
                        modifier = Modifier.widthIn(max = 900.dp),
                        bitmap = plotProvider.png.decodeToImageBitmap(),
                        contentDescription = stringResource(Res.string.exp_plot_desc),
                    )
            }
        }

        SimulationControls(
            modifier = Modifier.fillMaxHeight(),
            bestRouteLengthState = derivedStateOf { bestRoute.length },
            simulationController = viewModel.SimulationController(),
            showPheromones = showPheromones,
            showBestRoute = showBestRoute,
            plotProviderState = plotProviderState,
        )
    }
}

@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    uiState: State<MapState>,
    bestRouteState: State<CyclicRoute>,
    showPheromones: State<Boolean>,
    showBestRoute: State<Boolean>,
) {
    val mapState by uiState
    val bestRoute by bestRouteState

    val textMeasurer = rememberTextMeasurer()
    val cityLabelStyle = MaterialTheme.typography.labelSmall

    val showPheromones by showPheromones
    val showBestRoute by showBestRoute
    Canvas(
        modifier = modifier,
        contentDescription = stringResource(Res.string.exp_canvas_desc),
    ) {
        /** Padding from the canvas edges to draw points with */
        val imgPadding = 20.dp.toPx()

        fun Offset.toCanvasPos() = Offset(
            x = this.x * (size.width - 2 * imgPadding) + imgPadding,
            y = this.y * (size.height - 2 * imgPadding) + imgPadding,
        )

        if (showPheromones) {
            val maxLevel = mapState.roads.maxOfOrNull { it.value }
            for ((road, level) in mapState.roads) {
                val intensity = level / maxLevel!!

                val lineStart = mapState.cities[road.city1].toCanvasPos()
                val lineEnd = mapState.cities[road.city2].toCanvasPos()

                val lineMid = (lineStart + lineEnd) / 2f
                val lineNormal = (lineEnd - lineStart).let { it / it.getDistance() }.let { Offset(-it.y, it.x) }

                val brushWidth = (intensity * 10.dp.toPx()).coerceAtLeast(1.dp.toPx())
                val brushStart = lineMid - lineNormal * brushWidth
                val brushEnd = lineMid + lineNormal * brushWidth

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.0625f.pow(1f / intensity)), intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.25f.pow(1f / intensity)), intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red, intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.25f.pow(1f / intensity)), intensity),
                            lerp(Color.Blue.copy(alpha = 0f), Color.Red.copy(alpha = 0.0625f.pow(1f / intensity)), intensity),
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
            bestRoute.forEach { it ->
                drawLine(
                    color = Color.Green,
                    start = mapState.cities[it.city1].toCanvasPos(),
                    end = mapState.cities[it.city2].toCanvasPos(),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        drawPoints(
            points = mapState.cities.map(Offset::toCanvasPos),
            pointMode = PointMode.Points,
            color = Color.Black,
            strokeWidth = 10.dp.toPx(),
            cap = StrokeCap.Round,
        )

        mapState.cities.map(Offset::toCanvasPos).forEachIndexed { idx, pos ->
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
    bestRouteLengthState: State<Float>,
    simulationController: SimulationViewModel.SimulationController,
    showPheromones: MutableState<Boolean>,
    showBestRoute: MutableState<Boolean>,
    plotProviderState: State<PlotProvider>,
) {
    val bestRouteLength by bestRouteLengthState
    val simStatus by simulationController.simStatus.collectAsState()
    val age by simulationController.ageState.collectAsState()
    var showPheromones by showPheromones
    var showBestRoute by showBestRoute
    var reuseBestRoute by remember { mutableStateOf(true) }

    LaunchedEffect(simStatus) {
        if (simStatus == SimulationStatus.RUNNING) {
            do {
                simulationController.step()
            } while (simulationController.simStatus.value == SimulationStatus.RUNNING)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        var seed by remember { mutableStateOf("") }
        val rng: Random by derivedStateOf {
            seed.takeUnless { it.isBlank() }
                ?.let {
                    SafeSeededRandom(it.hashCode())
                } ?: Random.Default
        }

        val citiesAmount = rememberIntFieldState("10", Constraint.RangeInt(MIN_CITIES_AMOUNT..MAX_CITIES_AMOUNT))
        val antsPercent = rememberFloatFieldState("100", Constraint.PositiveFloat)
        val eliteAntsPercent = rememberFloatFieldState("0", Constraint.RangeFloat(0f..100f))
        val ages = rememberIntFieldState("50", Constraint.PositiveInt)
        val distanceImportance = rememberFloatFieldState("1", Constraint.NonNegativeFloat)
        val pheromoneImportance = rememberFloatFieldState("3", Constraint.NonNegativeFloat)
        val pheromoneDepositMultiplier = rememberFloatFieldState("1", Constraint.PositiveFloat)
        val initialPheromoneLevel = rememberFloatFieldState("0.01", Constraint.PositiveFloat)

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.param_rng_seed)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                value = seed,
                onValueChange = { seed = it.trim() },
                supportingText = {
                    if (seed.isBlank()) {
                        Text(stringResource(Res.string.param_rng_seed_blank_hint))
                    }
                },
            )

            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = citiesAmount,
                label = stringResource(Res.string.param_cities_amount),
            )

            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = antsPercent,
                label = stringResource(Res.string.param_ants_percent),
            )
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = eliteAntsPercent,
                label = stringResource(Res.string.param_elite_ants_all),
            )

            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = ages,
                label = stringResource(Res.string.param_ages),
            )

            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = distanceImportance,
                label = stringResource(Res.string.param_distance_importance),
            )
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = pheromoneImportance,
                label = stringResource(Res.string.param_pheromone_importance),
            )

            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = pheromoneDepositMultiplier,
                label = stringResource(Res.string.param_deposit_multiplier_q),
            )

            ValidatedField(
                modifier = Modifier.fillMaxWidth(),
                state = initialPheromoneLevel,
                label = stringResource(Res.string.param_initial_pheromone_level),
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
                        val eliteAntsPercent = eliteAntsPercent.value ?: return@Button
                        val ages = ages.value ?: return@Button
                        val distanceImportance = distanceImportance.value ?: return@Button
                        val pheromoneImportance = pheromoneImportance.value ?: return@Button
                        val pheromoneDepositMultiplier = pheromoneDepositMultiplier.value ?: return@Button
                        val initialPheromoneLevel = initialPheromoneLevel.value ?: return@Button

                        runBlocking {
                            context(rng) {
                                simulationController.reset(
                                    citiesAmount = citiesAmount,
                                    antsAmount = antsAmount,
                                    eliteAntsPercent = eliteAntsPercent,
                                    ages = ages,
                                    distanceImportance = distanceImportance,
                                    pheromoneImportance = pheromoneImportance,
                                    pheromoneDepositMultiplier = pheromoneDepositMultiplier,
                                    initialPheromoneLevel = initialPheromoneLevel,
                                    preserveTopology = simStatus == SimulationStatus.RUNNING || simStatus == SimulationStatus.FINISHED,
                                    reuseBestRoute = reuseBestRoute,
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        text = if (simStatus == SimulationStatus.UNINITIALIZED) {
                            stringResource(Res.string.btn_init)
                        } else {
                            stringResource(Res.string.btn_reset)
                        },
                    )
                }
                Button(
                    enabled = simStatus == SimulationStatus.NOT_STARTED,
                    onClick = {
                        runBlocking { simulationController.step() }
                    },
                ) {
                    Text(
                        text = if (simStatus == SimulationStatus.RUNNING) {
                            (age + 1).toString()
                        } else {
                            stringResource(Res.string.btn_run)
                        },
                    )
                }
            }

            Column {
                Text(
                    text = stringResource(Res.string.shortest_route_so_far),
                )
                Text(
                    text = bestRouteLength.toString(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = reuseBestRoute,
                    onCheckedChange = { reuseBestRoute = it },
                )
                Text(text = stringResource(Res.string.reuse_best_route))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = showPheromones,
                    onCheckedChange = { showPheromones = it },
                )
                Text(text = stringResource(Res.string.show_pheromones))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = showBestRoute,
                    onCheckedChange = { showBestRoute = it },
                )
                Text(text = stringResource(Res.string.show_best_route))
            }
            Button(
                onClick = plotProviderState.value::save,
            ) {
                Text(text = stringResource(Res.string.btn_save_plot))
            }
        }
    }
}
