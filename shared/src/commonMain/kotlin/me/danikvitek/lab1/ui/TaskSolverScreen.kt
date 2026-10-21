package me.danikvitek.lab1.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.danikvitek.lab1.CanvasOrPlot
import me.danikvitek.lab1.data.MapPresets
import me.danikvitek.lab1.domain.model.CyclicRoute
import me.danikvitek.lab1.domain.model.GraphValidationResult
import me.danikvitek.lab1.domain.model.Road
import me.danikvitek.lab1.domain.model.TaskMap
import me.danikvitek.lab1.presentation.EditorTool
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.TaskSolverViewModel
import me.danikvitek.lab1.presentation.fieldState.Constraint
import me.danikvitek.lab1.presentation.fieldState.ValidatedField
import me.danikvitek.lab1.presentation.fieldState.rememberFloatFieldState
import me.danikvitek.lab1.presentation.fieldState.rememberIntFieldState
import me.danikvitek.lab1.util.SafeSeededRandom
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@Composable
fun TaskSolverScreen(
    viewModel: TaskSolverViewModel = viewModel { TaskSolverViewModel() },
) {
    val taskMap by viewModel.taskMapState.collectAsState()
    val validation by viewModel.validationState.collectAsState()
    val pheromones by viewModel.pheromonesState.collectAsState()
    val simStatus by viewModel.simStatusState.collectAsState()
    val age by viewModel.ageState.collectAsState()
    val bestRoute by viewModel.bestRouteState.collectAsState()
    val activeTool by viewModel.activeToolState.collectAsState()
    val selectedCityIndex by viewModel.selectedCityIndexState.collectAsState()
    val autoAdjustLayout by viewModel.autoAdjustLayoutState.collectAsState()
    val statusMessage by viewModel.statusMessageState.collectAsState()

    var showPheromones by remember { mutableStateOf(true) }
    var showBestRoute by remember { mutableStateOf(true) }
    var showRoadLengths by remember { mutableStateOf(true) }
    var canvasOrPlot by remember { mutableStateOf(CanvasOrPlot.CANVAS) }
    var activeRightTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .heightIn(max = 1200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top Map Management Toolbar
        MapManagementBar(
            taskMap = taskMap,
            statusMessage = statusMessage,
            onMapNameChange = viewModel::setMapName,
            onLoadPreset = viewModel::loadPreset,
            onSaveFile = viewModel::saveMapToFile,
            onLoadFile = viewModel::loadMapFromFile,
            onClearMap = viewModel::clearMap,
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Left: Interactive Canvas & Toolbars
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CanvasToolbar(
                    activeTool = activeTool,
                    selectedCityName = selectedCityIndex?.let { taskMap.cities.getOrNull(it)?.name },
                    onSelectTool = viewModel::setTool,
                    autoAdjustLayout = autoAdjustLayout,
                    onToggleAutoAdjust = viewModel::setAutoAdjustLayout,
                    onAdjustLayoutNow = viewModel::adjustLayoutNow,
                    showPheromones = showPheromones,
                    onTogglePheromones = { showPheromones = it },
                    showBestRoute = showBestRoute,
                    onToggleBestRoute = { showBestRoute = it },
                    showRoadLengths = showRoadLengths,
                    onToggleRoadLengths = { showRoadLengths = it },
                    canvasOrPlot = canvasOrPlot,
                    onToggleCanvasOrPlot = { canvasOrPlot = it },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (canvasOrPlot) {
                        CanvasOrPlot.CANVAS -> {
                            InteractiveGraphCanvas(
                                modifier = Modifier.fillMaxSize(),
                                taskMap = taskMap,
                                validation = validation,
                                pheromones = pheromones,
                                bestRoute = bestRoute,
                                activeTool = activeTool,
                                selectedCityIndex = selectedCityIndex,
                                showPheromones = showPheromones,
                                showBestRoute = showBestRoute,
                                showRoadLengths = showRoadLengths,
                                onSelectCity = viewModel::selectCity,
                                onAddCityAt = { pos -> viewModel.addCity(pos) },
                                onMoveCity = viewModel::moveCity,
                                onConnectCities = viewModel::addRoadFromCanvasClick,
                                onDeleteCity = viewModel::deleteCity,
                                onDeleteRoad = viewModel::deleteRoad,
                            )
                        }

                        CanvasOrPlot.PLOT -> {
                            val plotProvider = remember(bestRoute, age) {
                                runCatching { viewModel.plot(taskMap.cities.size) }.getOrNull()
                            }
                            if (plotProvider != null && age > 0) {
                                Image(
                                    modifier = Modifier.fillMaxSize(),
                                    bitmap = plotProvider.png.decodeToImageBitmap(),
                                    contentDescription = "Convergence Plot",
                                )
                            } else {
                                Text(
                                    text = "Run the solver for at least 1 age to generate the convergence plot.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Right: Validation + Graph Builder / Task Solver Tabs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ValidationBanner(
                    taskMap = taskMap,
                    validation = validation,
                    onClampTriangleViolations = viewModel::fixTriangleViolationsByClamping,
                    onRecalculateFromCanvas = viewModel::recalculateAllDistancesFromCanvas,
                    onConnectAll = viewModel::connectAllCitiesFromCanvas,
                )

                PrimaryTabRow(selectedTabIndex = activeRightTab) {
                    Tab(
                        selected = activeRightTab == 0,
                        onClick = { activeRightTab = 0 },
                        text = {
                            Text("1. Graph Builder (${taskMap.cities.size} cities, ${taskMap.roadLengthsKm.size} roads)")
                        },
                    )
                    Tab(
                        selected = activeRightTab == 1,
                        onClick = { activeRightTab = 1 },
                        text = {
                            val badge = if (bestRoute.isNotEmpty()) " • ${TaskSolverViewModel.formatKm(bestRoute.length)} km" else ""
                            Text("2. Task Solver & Route$badge")
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (activeRightTab) {
                        0 -> GraphBuilderPanel(
                            modifier = Modifier.fillMaxSize(),
                            taskMap = taskMap,
                            validation = validation,
                            onAddCityByName = { name ->
                                val nextIdx = taskMap.cities.size
                                val angle = (nextIdx * 1.15)
                                val pos = Offset(
                                    x = (0.5f + 0.32f * kotlin.math.cos(angle).toFloat()).coerceIn(0.1f, 0.9f),
                                    y = (0.5f + 0.32f * kotlin.math.sin(angle).toFloat()).coerceIn(0.1f, 0.9f),
                                )
                                viewModel.addCity(pos, name)
                            },
                            onRenameCity = viewModel::renameCity,
                            onDeleteCity = viewModel::deleteCity,
                            onSetStartCity = viewModel::setStartCity,
                            onAddOrUpdateRoad = { c1, c2, km -> viewModel.addOrUpdateRoad(c1, c2, km) },
                            onDeleteRoad = viewModel::deleteRoad,
                            onConnectAll = viewModel::connectAllCitiesFromCanvas,
                            onRecalculateFromCanvas = viewModel::recalculateAllDistancesFromCanvas,
                        )

                        1 -> SolverAndResultsPanel(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel,
                            taskMap = taskMap,
                            validation = validation,
                            simStatus = simStatus,
                            age = age,
                            bestRoute = bestRoute,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapManagementBar(
    taskMap: TaskMap,
    statusMessage: String?,
    onMapNameChange: (String) -> Unit,
    onLoadPreset: (TaskMap) -> Unit,
    onSaveFile: () -> Unit,
    onLoadFile: () -> Unit,
    onClearMap: () -> Unit,
) {
    var presetsExpanded by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = taskMap.name,
                onValueChange = onMapNameChange,
                label = { Text("Map Name") },
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )

            Box {
                OutlinedButton(onClick = { presetsExpanded = true }) {
                    Text("Load Preset ▾")
                }
                DropdownMenu(
                    expanded = presetsExpanded,
                    onDismissRequest = { presetsExpanded = false },
                ) {
                    MapPresets.all.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text("${preset.name} (${preset.cities.size} cities)") },
                            onClick = {
                                presetsExpanded = false
                                onLoadPreset(preset)
                            },
                        )
                    }
                }
            }

            Button(onClick = onSaveFile) {
                Text("Save Map & Solution")
            }

            OutlinedButton(onClick = onLoadFile) {
                Text("Load Map File")
            }

            OutlinedButton(
                onClick = onClearMap,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Clear Map")
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasToolbar(
    activeTool: EditorTool,
    selectedCityName: String?,
    onSelectTool: (EditorTool) -> Unit,
    autoAdjustLayout: Boolean,
    onToggleAutoAdjust: (Boolean) -> Unit,
    onAdjustLayoutNow: () -> Unit,
    showPheromones: Boolean,
    onTogglePheromones: (Boolean) -> Unit,
    showBestRoute: Boolean,
    onToggleBestRoute: (Boolean) -> Unit,
    showRoadLengths: Boolean,
    onToggleRoadLengths: (Boolean) -> Unit,
    canvasOrPlot: CanvasOrPlot,
    onToggleCanvasOrPlot: (CanvasOrPlot) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Tool:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                EditorTool.entries.forEach { tool ->
                    FilterChip(
                        selected = activeTool == tool,
                        onClick = { onSelectTool(tool) },
                        label = { Text(tool.label) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onAdjustLayoutNow,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Text("Adjust Layout Now", style = MaterialTheme.typography.labelMedium)
                }
            }

            val toolHint = when (activeTool) {
                EditorTool.SELECT_MOVE -> "Hint: Drag any city on the canvas to reposition it, or click a city to select it."
                EditorTool.ADD_CITY -> "Hint: Click anywhere on empty canvas space to place a new city."
                EditorTool.CONNECT_ROAD -> if (selectedCityName != null) {
                    "Hint: Selected \"$selectedCityName\" — now click another city on the canvas to connect them."
                } else {
                    "Hint: Click the first city on the canvas, then click a second city to create a road."
                }
                EditorTool.DELETE -> "Hint: Click any city node or road segment on the canvas to delete it."
            }
            Text(
                text = toolHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoAdjustLayout, onCheckedChange = onToggleAutoAdjust)
                    Text("Auto-adjust positions to km", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showRoadLengths, onCheckedChange = onToggleRoadLengths)
                    Text("Road km", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showPheromones, onCheckedChange = onTogglePheromones)
                    Text("Pheromones", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showBestRoute, onCheckedChange = onToggleBestRoute)
                    Text("Best route", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Map", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = canvasOrPlot == CanvasOrPlot.PLOT,
                        onCheckedChange = { onToggleCanvasOrPlot(if (it) CanvasOrPlot.PLOT else CanvasOrPlot.CANVAS) },
                    )
                    Text("Plot", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private class CanvasTransform(canvasWidth: Float, canvasHeight: Float, padding: Float) {
    val side = (min(canvasWidth, canvasHeight) - 2f * padding).coerceAtLeast(1f)
    val originX = (canvasWidth - side) / 2f
    val originY = (canvasHeight - side) / 2f

    fun toCanvas(normalized: Offset): Offset = Offset(
        x = originX + normalized.x * side,
        y = originY + normalized.y * side,
    )

    fun toNormalized(canvasPos: Offset): Offset = Offset(
        x = ((canvasPos.x - originX) / side).coerceIn(0.03f, 0.97f),
        y = ((canvasPos.y - originY) / side).coerceIn(0.03f, 0.97f),
    )
}

@Composable
private fun InteractiveGraphCanvas(
    modifier: Modifier = Modifier,
    taskMap: TaskMap,
    validation: GraphValidationResult,
    pheromones: Map<Road, Float>,
    bestRoute: CyclicRoute,
    activeTool: EditorTool,
    selectedCityIndex: Int?,
    showPheromones: Boolean,
    showBestRoute: Boolean,
    showRoadLengths: Boolean,
    onSelectCity: (Int?) -> Unit,
    onAddCityAt: (Offset) -> Unit,
    onMoveCity: (Int, Offset) -> Unit,
    onConnectCities: (Int, Int) -> Unit,
    onDeleteCity: (Int) -> Unit,
    onDeleteRoad: (Road) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val cityLabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val roadLabelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)

    val currentTaskMap by rememberUpdatedState(taskMap)
    val currentTool by rememberUpdatedState(activeTool)
    val currentSelectedCity by rememberUpdatedState(selectedCityIndex)

    var draggedCityIndex by remember { mutableStateOf<Int?>(null) }

    val violatingRoads = remember(validation) {
        validation.triangleViolations.map { it.violatingRoad }.toSet()
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val transform = CanvasTransform(size.width.toFloat(), size.height.toFloat(), 28.dp.toPx())
                    val hitRadius = 18.dp.toPx()
                    val cities = currentTaskMap.cities
                    val hitCityIdx = cities.indices.minByOrNull { idx ->
                        (transform.toCanvas(cities[idx].position) - tapOffset).getDistance()
                    }?.takeIf { idx ->
                        (transform.toCanvas(cities[idx].position) - tapOffset).getDistance() <= hitRadius
                    }

                    when (currentTool) {
                        EditorTool.ADD_CITY -> {
                            if (hitCityIdx == null) {
                                onAddCityAt(transform.toNormalized(tapOffset))
                            } else {
                                onSelectCity(hitCityIdx)
                            }
                        }

                        EditorTool.SELECT_MOVE -> {
                            onSelectCity(hitCityIdx)
                        }

                        EditorTool.CONNECT_ROAD -> {
                            if (hitCityIdx != null) {
                                val prevSelected = currentSelectedCity
                                if (prevSelected == null) {
                                    onSelectCity(hitCityIdx)
                                } else if (prevSelected == hitCityIdx) {
                                    onSelectCity(null)
                                } else {
                                    onConnectCities(prevSelected, hitCityIdx)
                                    onSelectCity(hitCityIdx)
                                }
                            } else {
                                onSelectCity(null)
                            }
                        }

                        EditorTool.DELETE -> {
                            if (hitCityIdx != null) {
                                onDeleteCity(hitCityIdx)
                            } else {
                                val roadHitThreshold = 14.dp.toPx()
                                val hitRoad = currentTaskMap.roadLengthsKm.keys.firstOrNull { road ->
                                    val p1 = cities.getOrNull(road.city1)?.position?.let(transform::toCanvas)
                                        ?: return@firstOrNull false
                                    val p2 = cities.getOrNull(road.city2)?.position?.let(transform::toCanvas)
                                        ?: return@firstOrNull false
                                    distanceToSegment(tapOffset, p1, p2) <= roadHitThreshold
                                }
                                if (hitRoad != null) {
                                    onDeleteRoad(hitRoad)
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (currentTool != EditorTool.SELECT_MOVE) return@detectDragGestures
                        val transform = CanvasTransform(size.width.toFloat(), size.height.toFloat(), 28.dp.toPx())
                        val hitRadius = 20.dp.toPx()
                        val cities = currentTaskMap.cities
                        val hitIdx = cities.indices.minByOrNull { idx ->
                            (transform.toCanvas(cities[idx].position) - startOffset).getDistance()
                        }?.takeIf { idx ->
                            (transform.toCanvas(cities[idx].position) - startOffset).getDistance() <= hitRadius
                        }
                        draggedCityIndex = hitIdx
                        if (hitIdx != null) {
                            onSelectCity(hitIdx)
                        }
                    },
                    onDrag = { change, _ ->
                        val idx = draggedCityIndex ?: return@detectDragGestures
                        change.consume()
                        val transform = CanvasTransform(size.width.toFloat(), size.height.toFloat(), 28.dp.toPx())
                        onMoveCity(idx, transform.toNormalized(change.position))
                    },
                    onDragEnd = {
                        draggedCityIndex = null
                    },
                    onDragCancel = {
                        draggedCityIndex = null
                    },
                )
            },
    ) {
        val transform = CanvasTransform(size.width, size.height, 28.dp.toPx())
        fun Offset.toCanvasPos() = transform.toCanvas(this)

        val cities = taskMap.cities

        // 1. Draw base road lines
        for ((road, _) in taskMap.roadLengthsKm) {
            if (road.city1 !in cities.indices || road.city2 !in cities.indices) continue
            val start = cities[road.city1].position.toCanvasPos()
            val end = cities[road.city2].position.toCanvasPos()
            val isViolating = road in violatingRoads

            drawLine(
                color = if (isViolating) Color(0xFFD32F2F) else Color(0xFFB0BEC5),
                start = start,
                end = end,
                strokeWidth = if (isViolating) 3.dp.toPx() else 1.5.dp.toPx(),
                pathEffect = if (isViolating) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
            )
        }

        // 2. Draw pheromone overlays if available
        if (showPheromones && pheromones.isNotEmpty()) {
            val maxLevel = pheromones.values.maxOrNull()?.takeIf { it > 0f }
            if (maxLevel != null) {
                for ((road, level) in pheromones) {
                    if (road.city1 !in cities.indices || road.city2 !in cities.indices) continue
                    val intensity = (level / maxLevel).coerceIn(0.01f, 1f)
                    val lineStart = cities[road.city1].position.toCanvasPos()
                    val lineEnd = cities[road.city2].position.toCanvasPos()
                    val dist = (lineEnd - lineStart).getDistance()
                    if (dist < 1f) continue

                    val lineMid = (lineStart + lineEnd) / 2f
                    val lineNormal = (lineEnd - lineStart).let { it / dist }.let { Offset(-it.y, it.x) }

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
        }

        // 3. Draw best route highlight
        if (showBestRoute && bestRoute.isNotEmpty()) {
            for (road in bestRoute) {
                if (road.city1 !in cities.indices || road.city2 !in cities.indices) continue
                drawLine(
                    color = Color(0xFF2E7D32),
                    start = cities[road.city1].position.toCanvasPos(),
                    end = cities[road.city2].position.toCanvasPos(),
                    strokeWidth = 3.5.dp.toPx(),
                )
            }
        }

        // 4. Draw road length labels ("XXX km")
        if (showRoadLengths) {
            for ((road, lengthKm) in taskMap.roadLengthsKm) {
                if (road.city1 !in cities.indices || road.city2 !in cities.indices) continue
                val p1 = cities[road.city1].position.toCanvasPos()
                val p2 = cities[road.city2].position.toCanvasPos()
                val mid = (p1 + p2) / 2f
                val isViolating = road in violatingRoads
                val isOnBestRoute = showBestRoute && bestRoute.contains(road)

                val labelText = "${TaskSolverViewModel.formatKm(lengthKm)} km"
                val measured = textMeasurer.measure(
                    text = labelText,
                    style = roadLabelStyle,
                    maxLines = 1,
                )
                val padX = 4.dp.toPx()
                val padY = 2.dp.toPx()
                val boxW = measured.size.width + padX * 2
                val boxH = measured.size.height + padY * 2
                val topLeft = Offset(mid.x - boxW / 2f, mid.y - boxH / 2f)

                val bgColor = when {
                    isViolating -> Color(0xFFFFEBEE)
                    isOnBestRoute -> Color(0xFFE8F5E9)
                    else -> Color.White.copy(alpha = 0.90f)
                }
                val textColor = when {
                    isViolating -> Color(0xFFB71C1C)
                    isOnBestRoute -> Color(0xFF1B5E20)
                    else -> Color(0xFF37474F)
                }

                drawRoundRect(
                    color = bgColor,
                    topLeft = topLeft,
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                drawRoundRect(
                    color = textColor.copy(alpha = 0.35f),
                    topLeft = topLeft,
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(topLeft.x + padX, topLeft.y + padY),
                    color = textColor,
                )
            }
        }

        // 5. Draw city nodes and their name labels
        cities.forEachIndexed { idx, city ->
            val pos = city.position.toCanvasPos()
            val isStart = idx == taskMap.startCityIndex
            val isSelected = idx == selectedCityIndex

            if (isSelected) {
                drawCircle(
                    color = Color(0xFF1976D2).copy(alpha = 0.25f),
                    radius = 14.dp.toPx(),
                    center = pos,
                )
            }

            if (isStart) {
                drawCircle(
                    color = Color(0xFFF57C00),
                    radius = 9.dp.toPx(),
                    center = pos,
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = pos,
                )
            } else {
                drawCircle(
                    color = if (isSelected) Color(0xFF1976D2) else Color(0xFF263238),
                    radius = 7.dp.toPx(),
                    center = pos,
                )
            }

            val labelText = if (isStart) "★ ${city.name}" else city.name
            val measured = textMeasurer.measure(
                text = labelText,
                style = cityLabelStyle,
                maxLines = 1,
            )
            val padX = 5.dp.toPx()
            val padY = 2.dp.toPx()
            val boxW = measured.size.width + padX * 2
            val boxH = measured.size.height + padY * 2

            // Keep city label inside canvas bounds
            val rawX = pos.x + 10.dp.toPx()
            val rawY = pos.y - boxH / 2f
            val clampedX = rawX.coerceIn(4.dp.toPx(), (size.width - boxW - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()))
            val clampedY = rawY.coerceIn(4.dp.toPx(), (size.height - boxH - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()))

            drawRoundRect(
                color = if (isStart) Color(0xFFFFF3E0) else Color.White.copy(alpha = 0.92f),
                topLeft = Offset(clampedX, clampedY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(clampedX + padX, clampedY + padY),
                color = if (isStart) Color(0xFFE65100) else Color(0xFF1C1B1F),
            )
        }
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lenSq = ab.x * ab.x + ab.y * ab.y
    if (lenSq < 1e-6f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lenSq).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * ab.x, a.y + t * ab.y)
    return (p - proj).getDistance()
}

@Composable
private fun ValidationBanner(
    taskMap: TaskMap,
    validation: GraphValidationResult,
    onClampTriangleViolations: () -> Unit,
    onRecalculateFromCanvas: () -> Unit,
    onConnectAll: () -> Unit,
) {
    if (validation.isValidForSimulation) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Graph Constraints & Validation Issues",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            if (!validation.hasEnoughCities) {
                Text(
                    text = "• Add at least 2 cities to run the Ant Colony solver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                if (!validation.isConnected) {
                    Text(
                        text = "• The city graph is disconnected. Connect all components with roads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (validation.lowDegreeCities.isNotEmpty()) {
                    val names = validation.lowDegreeCities.mapNotNull { taskMap.cities.getOrNull(it)?.name }.joinToString(", ")
                    val minRoads = if (taskMap.cities.size == 2) 1 else 2
                    Text(
                        text = "• Each city needs at least $minRoads road(s) for a cyclic tour. Check: $names",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (validation.triangleViolations.isNotEmpty()) {
                    Text(
                        text = "• Triangle inequality violated on ${validation.triangleViolations.size} triangle(s):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    validation.triangleViolations.take(3).forEach { v ->
                        val nameA = taskMap.cities.getOrNull(v.cityA)?.name ?: "#${v.cityA}"
                        val nameB = taskMap.cities.getOrNull(v.cityB)?.name ?: "#${v.cityB}"
                        val nameC = taskMap.cities.getOrNull(v.cityC)?.name ?: "#${v.cityC}"
                        Text(
                            text = "   $nameA ↔ $nameB (${TaskSolverViewModel.formatKm(v.directLengthKm)} km) > $nameA → $nameC → $nameB (${TaskSolverViewModel.formatKm(v.indirectLengthKm)} km)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (validation.triangleViolations.size > 3) {
                        Text(
                            text = "   ...and ${validation.triangleViolations.size - 3} more.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!validation.isConnected || validation.lowDegreeCities.isNotEmpty()) {
                        FilledTonalButton(onClick = onConnectAll) {
                            Text("Connect All Cities")
                        }
                    }
                    if (validation.triangleViolations.isNotEmpty()) {
                        FilledTonalButton(onClick = onClampTriangleViolations) {
                            Text("Auto-Clamp Violating Roads")
                        }
                        OutlinedButton(onClick = onRecalculateFromCanvas) {
                            Text("Sync km from Canvas")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphBuilderPanel(
    modifier: Modifier = Modifier,
    taskMap: TaskMap,
    validation: GraphValidationResult,
    onAddCityByName: (String) -> Unit,
    onRenameCity: (Int, String) -> Unit,
    onDeleteCity: (Int) -> Unit,
    onSetStartCity: (Int) -> Unit,
    onAddOrUpdateRoad: (Int, Int, Float) -> Unit,
    onDeleteRoad: (Road) -> Unit,
    onConnectAll: () -> Unit,
    onRecalculateFromCanvas: () -> Unit,
) {
    var newCityName by remember { mutableStateOf("") }
    var fromCityIdx by remember(taskMap.cities.size) { mutableStateOf(0) }
    var toCityIdx by remember(taskMap.cities.size) { mutableStateOf(if (taskMap.cities.size > 1) 1 else 0) }
    var newRoadKmText by remember { mutableStateOf("150") }

    val violatingRoads = remember(validation) {
        validation.triangleViolations.map { it.violatingRoad }.toSet()
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Column 1: Cities List & Creator
        Card(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Cities (${taskMap.cities.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = newCityName,
                        onValueChange = { newCityName = it },
                        label = { Text("New city name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            onAddCityByName(newCityName)
                            newCityName = ""
                        },
                    ) {
                        Text("Add")
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(taskMap.cities) { idx, city ->
                        val isStart = idx == taskMap.startCityIndex
                        Surface(
                            tonalElevation = if (isStart) 4.dp else 1.dp,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RadioButton(
                                    selected = isStart,
                                    onClick = { onSetStartCity(idx) },
                                )
                                OutlinedTextField(
                                    value = city.name,
                                    onValueChange = { onRenameCity(idx, it) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    label = { Text(if (isStart) "Start City #${idx + 1}" else "City #${idx + 1}") },
                                )
                                TextButton(
                                    onClick = { onDeleteCity(idx) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Text("✕")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Column 2: Roads List & Creator
        Card(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Roads (${taskMap.roadLengthsKm.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onConnectAll) {
                            Text("Connect All")
                        }
                        TextButton(onClick = onRecalculateFromCanvas) {
                            Text("Canvas → km")
                        }
                    }
                }

                // Add/Update Road Form
                if (taskMap.cities.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CityDropdownSelector(
                            modifier = Modifier.weight(1f),
                            label = "From",
                            cities = taskMap.cities.map { it.name },
                            selectedIndex = fromCityIdx.coerceIn(taskMap.cities.indices),
                            onSelectIndex = { fromCityIdx = it },
                        )
                        CityDropdownSelector(
                            modifier = Modifier.weight(1f),
                            label = "To",
                            cities = taskMap.cities.map { it.name },
                            selectedIndex = toCityIdx.coerceIn(taskMap.cities.indices),
                            onSelectIndex = { toCityIdx = it },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = newRoadKmText,
                            onValueChange = { newRoadKmText = it },
                            label = { Text("Length (km)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = fromCityIdx != toCityIdx && (newRoadKmText.toFloatOrNull() ?: 0f) > 0f,
                            onClick = {
                                val km = newRoadKmText.toFloatOrNull() ?: return@Button
                                onAddOrUpdateRoad(fromCityIdx, toCityIdx, km)
                            },
                        ) {
                            Text("Set Road")
                        }
                    }
                }

                HorizontalDivider()

                val sortedRoads = remember(taskMap.roadLengthsKm) {
                    taskMap.roadLengthsKm.entries.sortedWith(compareBy({ it.key.city1 }, { it.key.city2 }))
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sortedRoads, key = { it.key.packed }) { (road, lengthKm) ->
                        val c1Name = taskMap.cities.getOrNull(road.city1)?.name ?: "#${road.city1}"
                        val c2Name = taskMap.cities.getOrNull(road.city2)?.name ?: "#${road.city2}"
                        val isViolating = road in violatingRoads

                        var kmText by remember(road, lengthKm) {
                            mutableStateOf(TaskSolverViewModel.formatKm(lengthKm))
                        }

                        Surface(
                            tonalElevation = 1.dp,
                            shape = RoundedCornerShape(6.dp),
                            color = if (isViolating) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "$c1Name ↔ $c2Name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = kmText,
                                    onValueChange = { updated ->
                                        kmText = updated
                                    },
                                    suffix = { Text("km") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(115.dp),
                                )
                                TextButton(
                                    enabled = kmText.toFloatOrNull()?.let { it > 0f && it != lengthKm } == true,
                                    onClick = {
                                        val parsed = kmText.toFloatOrNull() ?: return@TextButton
                                        onAddOrUpdateRoad(road.city1, road.city2, parsed)
                                    },
                                ) {
                                    Text("Set")
                                }
                                TextButton(
                                    onClick = { onDeleteRoad(road) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Text("✕")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityDropdownSelector(
    modifier: Modifier = Modifier,
    label: String,
    cities: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = cities.getOrNull(selectedIndex) ?: "None"

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "$label: $selectedName",
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            cities.forEachIndexed { idx, name ->
                DropdownMenuItem(
                    text = { Text("${idx + 1}. $name") },
                    onClick = {
                        expanded = false
                        onSelectIndex(idx)
                    },
                )
            }
        }
    }
}

@Composable
private fun SolverAndResultsPanel(
    modifier: Modifier = Modifier,
    viewModel: TaskSolverViewModel,
    taskMap: TaskMap,
    validation: GraphValidationResult,
    simStatus: SimulationStatus,
    age: Int,
    bestRoute: CyclicRoute,
) {
    val coroutineScope = rememberCoroutineScope()

    var autoRunActive by remember { mutableStateOf(false) }
    var autoScaleQ by remember { mutableStateOf(true) }
    var showAdvancedParams by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf("") }

    val antsPercent = rememberFloatFieldState("150", Constraint.PositiveFloat)
    val eliteAntsPercent = rememberFloatFieldState("25", Constraint.RangeFloat(0f..100f))
    val ages = rememberIntFieldState("60", Constraint.PositiveInt)
    val distanceImportance = rememberFloatFieldState("1", Constraint.NonNegativeFloat)
    val pheromoneImportance = rememberFloatFieldState("3", Constraint.NonNegativeFloat)
    val evaporationRate = rememberFloatFieldState("0.4", Constraint.RangeFloat(0f..1f))
    val customQ = rememberFloatFieldState("1000", Constraint.PositiveFloat)
    val initialPheromoneLevel = rememberFloatFieldState("0.1", Constraint.PositiveFloat)

    val rng: Random by remember {
        derivedStateOf {
            seed.takeUnless { it.isBlank() }?.let { SafeSeededRandom(it.hashCode()) } ?: Random.Default
        }
    }

    LaunchedEffect(autoRunActive, simStatus) {
        if (autoRunActive && (simStatus == SimulationStatus.NOT_STARTED || simStatus == SimulationStatus.RUNNING)) {
            while (autoRunActive) {
                val status = viewModel.stepSimulation()
                if (status == SimulationStatus.FINISHED || status == SimulationStatus.UNINITIALIZED) {
                    autoRunActive = false
                    break
                }
            }
        }
    }

    fun startOrResetSimulation(runImmediately: Boolean) {
        val agesVal = ages.value ?: return
        val antsPctVal = antsPercent.value ?: return
        val elitePctVal = eliteAntsPercent.value ?: return
        val alphaVal = distanceImportance.value ?: return
        val betaVal = pheromoneImportance.value ?: return
        val rhoVal = evaporationRate.value ?: return
        val qVal = if (autoScaleQ) null else (customQ.value ?: return)
        val initPheromoneVal = initialPheromoneLevel.value ?: return

        coroutineScope.launch {
            autoRunActive = false
            val initialized = viewModel.initSimulation(
                ages = agesVal,
                antsPercent = antsPctVal,
                eliteAntsPercent = elitePctVal,
                distanceImportance = alphaVal,
                pheromoneImportance = betaVal,
                pheromoneEvaporationRate = rhoVal,
                pheromoneDepositMultiplier = qVal,
                initialPheromoneLevel = initPheromoneVal,
                rng = rng,
            )
            if (initialized && runImmediately) {
                autoRunActive = true
            }
        }
    }

    Column(
        modifier = modifier
            .heightIn(max = 850.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Task & Solver Configuration Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Ant Colony Task Solver",
                    style = MaterialTheme.typography.titleMedium,
                )

                if (taskMap.cities.isNotEmpty()) {
                    CityDropdownSelector(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Start / Depot City",
                        cities = taskMap.cities.map { it.name },
                        selectedIndex = taskMap.startCityIndex.coerceIn(taskMap.cities.indices),
                        onSelectIndex = viewModel::setStartCity,
                    )
                }

                // Warm-start status banner
                if (bestRoute.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Previous Best Route Saved (${TaskSolverViewModel.formatKm(bestRoute.length)} km)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = "Rerunning the simulation will warm-start from this route.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            TextButton(onClick = viewModel::clearSavedSolution) {
                                Text("Clear Solution")
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = validation.isValidForSimulation && !autoRunActive,
                        onClick = { startOrResetSimulation(runImmediately = true) },
                    ) {
                        Text(if (bestRoute.isNotEmpty()) "Rerun Solver (Warm-Start)" else "Solve Optimal Route")
                    }

                    OutlinedButton(
                        enabled = validation.isValidForSimulation,
                        onClick = {
                            if (autoRunActive) {
                                autoRunActive = false
                            } else {
                                startOrResetSimulation(runImmediately = false)
                            }
                        },
                    ) {
                        Text(if (autoRunActive) "Pause" else "Init / Reset")
                    }

                    OutlinedButton(
                        enabled = validation.isValidForSimulation && !autoRunActive &&
                                  (simStatus == SimulationStatus.NOT_STARTED || simStatus == SimulationStatus.RUNNING),
                        onClick = {
                            coroutineScope.launch { viewModel.stepSimulation() }
                        },
                    ) {
                        Text("Step (Age $age)")
                    }

                    if (age > 0) {
                        OutlinedButton(
                            onClick = {
                                runCatching { viewModel.plot(taskMap.cities.size).save() }
                            },
                        ) {
                            Text("Save Plot")
                        }
                    }
                }

                // Expandable Advanced ACO Parameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoScaleQ, onCheckedChange = { autoScaleQ = it })
                        Text("Auto-scale Q to km road distances", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { showAdvancedParams = !showAdvancedParams }) {
                        Text(if (showAdvancedParams) "Hide ACO Params ▲" else "ACO Params ▼")
                    }
                }

                if (showAdvancedParams) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = ages,
                                label = "Number of ages",
                            )
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = antsPercent,
                                label = "% of ants per city",
                            )
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = eliteAntsPercent,
                                label = "% of elite ants",
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("RNG Seed (optional)") },
                                singleLine = true,
                                value = seed,
                                onValueChange = { seed = it.trim() },
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = distanceImportance,
                                label = "Pheromone weight (α)",
                            )
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = pheromoneImportance,
                                label = "Visibility weight (β)",
                            )
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = evaporationRate,
                                label = "Evaporation rate (ρ)",
                            )
                            ValidatedField(
                                modifier = Modifier.fillMaxWidth(),
                                state = initialPheromoneLevel,
                                label = "Initial pheromone",
                            )
                            if (!autoScaleQ) {
                                ValidatedField(
                                    modifier = Modifier.fillMaxWidth(),
                                    state = customQ,
                                    label = "Deposit multiplier (Q)",
                                )
                            }
                        }
                    }
                }
            }
        }

        // Solution / Itinerary Breakdown Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Optimal Tour Solution",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (bestRoute.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                text = "Total: ${TaskSolverViewModel.formatKm(bestRoute.length)} km",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (bestRoute.isEmpty()) {
                    Text(
                        text = "No route computed yet. Click \"Solve Optimal Route\" above to find the shortest cyclic tour visiting all cities.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val itinerary = remember(bestRoute, taskMap) {
                        viewModel.buildRouteItinerary(bestRoute)
                    }
                    val summarySequence = remember(itinerary) {
                        if (itinerary.isEmpty()) ""
                        else (itinerary.map { it.fromCityName } + itinerary.first().fromCityName).joinToString(" → ")
                    }

                    Text(
                        text = summarySequence,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    HorizontalDivider()

                    itinerary.forEach { leg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${leg.stepNumber}. ${leg.fromCityName} → ${leg.toCityName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "+${TaskSolverViewModel.formatKm(leg.distanceKm)} km",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(95.dp),
                            )
                            Text(
                                text = "(Σ ${TaskSolverViewModel.formatKm(leg.cumulativeKm)} km)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(105.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
