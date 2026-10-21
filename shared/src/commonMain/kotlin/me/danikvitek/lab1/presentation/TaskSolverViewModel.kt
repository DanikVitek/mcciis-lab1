package me.danikvitek.lab1.presentation

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.danikvitek.lab1.data.MapFileStorage
import me.danikvitek.lab1.data.MapPresets
import me.danikvitek.lab1.data.PlotProvider
import me.danikvitek.lab1.data.Plotter
import me.danikvitek.lab1.data.getPlotter
import me.danikvitek.lab1.domain.Simulation
import me.danikvitek.lab1.domain.layout.GraphLayoutEngine
import me.danikvitek.lab1.domain.model.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EditorTool(val label: String) {
    SELECT_MOVE("Select / Move"),
    ADD_CITY("Add City"),
    CONNECT_ROAD("Connect Road"),
    DELETE("Delete"),
}

data class RouteLeg(
    val stepNumber: Int,
    val fromCityIndex: Int,
    val fromCityName: String,
    val toCityIndex: Int,
    val toCityName: String,
    val distanceKm: Float,
    val cumulativeKm: Float,
)

class TaskSolverViewModel(private val plotter: Plotter = getPlotter()) : ViewModel() {
    private var simulation: Simulation? = null

    val taskMapState: StateFlow<TaskMap>
        field = MutableStateFlow(MapPresets.ukrainianCities)

    val validationState: StateFlow<GraphValidationResult>
        field = MutableStateFlow(taskMapState.value.validate())

    val pheromonesState: StateFlow<Map<Road, Float>>
        field = MutableStateFlow(emptyMap())

    val simStatusState: StateFlow<SimulationStatus>
        field = MutableStateFlow(SimulationStatus.UNINITIALIZED)

    val ageState: StateFlow<Int>
        field = MutableStateFlow(0)

    val bestRouteState: StateFlow<CyclicRoute>
        field = MutableStateFlow(taskMapState.value.validateSavedRoute())

    val activeToolState: StateFlow<EditorTool>
        field = MutableStateFlow(EditorTool.SELECT_MOVE)

    val selectedCityIndexState: StateFlow<Int?>
        field = MutableStateFlow<Int?>(null)

    val autoAdjustLayoutState: StateFlow<Boolean>
        field = MutableStateFlow(true)

    val statusMessageState: StateFlow<String?>
        field = MutableStateFlow<String?>("Loaded preset: ${MapPresets.ukrainianCities.name}")

    fun plot(citiesAmount: Int): PlotProvider = plotter.plot(citiesAmount.coerceAtLeast(2))

    fun setTool(tool: EditorTool) {
        activeToolState.value = tool
        if (tool != EditorTool.CONNECT_ROAD && tool != EditorTool.SELECT_MOVE) {
            selectedCityIndexState.value = null
        }
    }

    fun selectCity(index: Int?) {
        selectedCityIndexState.value = index?.takeIf { it in taskMapState.value.cities.indices }
    }

    fun setAutoAdjustLayout(enabled: Boolean) {
        autoAdjustLayoutState.value = enabled
        if (enabled) {
            adjustLayoutNow()
        }
    }

    fun adjustLayoutNow() {
        val current = taskMapState.value
        if (current.cities.size >= 2 && current.roadLengthsKm.isNotEmpty()) {
            val adjustedCities = GraphLayoutEngine.adjustPositions(current.cities, current.roadLengthsKm)
            updateTaskMap(current.copy(cities = adjustedCities), runAutoLayout = false)
            statusMessageState.value = "Adjusted city positions according to road distances."
        }
    }

    private suspend fun stopActiveSimulation() {
        simulation?.cancel()
        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        ageState.value = 0
        pheromonesState.value = emptyMap()
    }

    private fun updateTaskMap(newMap: TaskMap, runAutoLayout: Boolean = autoAdjustLayoutState.value) {
        val layoutAppliedMap = if (runAutoLayout && newMap.cities.size >= 2 && newMap.roadLengthsKm.isNotEmpty()) {
            newMap.copy(cities = GraphLayoutEngine.adjustPositions(newMap.cities, newMap.roadLengthsKm))
        } else {
            newMap
        }
        val validRoute = layoutAppliedMap.validateSavedRoute()
        val finalMap = layoutAppliedMap.copy(
            savedBestRouteCities = validRoute.cities,
        )
        taskMapState.value = finalMap
        validationState.value = finalMap.validate()
        bestRouteState.value = validRoute
    }

    fun setMapName(name: String) {
        updateTaskMap(taskMapState.value.copy(name = name), runAutoLayout = false)
    }

    fun setStartCity(index: Int) {
        val current = taskMapState.value
        if (index in current.cities.indices) {
            updateTaskMap(current.copy(startCityIndex = index), runAutoLayout = false)
        }
    }

    fun addCity(position: Offset, customName: String? = null) {
        val current = taskMapState.value
        val nextNumber = current.cities.size + 1
        val name = customName?.trim()?.takeIf { it.isNotEmpty() } ?: "City $nextNumber"
        val clampedPos = Offset(position.x.coerceIn(0.04f, 0.96f), position.y.coerceIn(0.04f, 0.96f))
        val newCities = current.cities + NamedCity(name = name, position = clampedPos)
        // Adding a city invalidates any active simulation run
        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        pheromonesState.value = emptyMap()
        updateTaskMap(
            current.copy(
                cities = newCities,
                savedBestRouteCities = emptyList(),
            ),
            runAutoLayout = false,
        )
        selectedCityIndexState.value = newCities.lastIndex
        statusMessageState.value = "Added city \"$name\"."
    }

    fun renameCity(index: Int, newName: String) {
        val current = taskMapState.value
        if (index !in current.cities.indices) return
        val updatedCities = current.cities.toMutableList()
        updatedCities[index] = updatedCities[index].copy(name = newName)
        updateTaskMap(current.copy(cities = updatedCities), runAutoLayout = false)
    }

    fun moveCity(index: Int, newPosition: Offset) {
        val current = taskMapState.value
        if (index !in current.cities.indices) return
        val clampedPos = Offset(newPosition.x.coerceIn(0.03f, 0.97f), newPosition.y.coerceIn(0.03f, 0.97f))
        val updatedCities = current.cities.toMutableList()
        updatedCities[index] = updatedCities[index].copy(position = clampedPos)
        updateTaskMap(current.copy(cities = updatedCities), runAutoLayout = false)
    }

    fun deleteCity(index: Int) {
        val current = taskMapState.value
        if (index !in current.cities.indices) return
        val removedName = current.cities[index].name
        val newCities = current.cities.filterIndexed { idx, _ -> idx != index }

        fun remapIndex(oldIdx: Int): Int? = when {
            oldIdx == index -> null
            oldIdx > index -> oldIdx - 1
            else -> oldIdx
        }

        val newRoads = mutableMapOf<Road, Float>()
        for ((road, km) in current.roadLengthsKm) {
            val c1 = remapIndex(road.city1) ?: continue
            val c2 = remapIndex(road.city2) ?: continue
            newRoads[Road(c1, c2)] = km
        }

        val newStart = when {
            newCities.isEmpty() -> 0
            current.startCityIndex == index -> 0
            current.startCityIndex > index -> current.startCityIndex - 1
            else -> current.startCityIndex
        }

        selectedCityIndexState.value = null
        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        pheromonesState.value = emptyMap()

        updateTaskMap(
            current.copy(
                cities = newCities,
                roadLengthsKm = newRoads,
                startCityIndex = newStart,
                savedBestRouteCities = emptyList(),
            ),
            runAutoLayout = false,
        )
        statusMessageState.value = "Removed city \"$removedName\"."
    }

    fun estimateCanvasScaleKm(): Float {
        val current = taskMapState.value
        if (current.roadLengthsKm.isEmpty()) return 500f
        var ratioSum = 0f
        var count = 0
        for ((road, km) in current.roadLengthsKm) {
            val c1 = current.cities.getOrNull(road.city1)?.position ?: continue
            val c2 = current.cities.getOrNull(road.city2)?.position ?: continue
            val dist = (c1 - c2).getDistance()
            if (dist > 1e-3f) {
                ratioSum += km / dist
                count++
            }
        }
        return if (count > 0) (ratioSum / count).coerceIn(50f, 5000f) else 500f
    }

    fun addOrUpdateRoad(city1: Int, city2: Int, lengthKm: Float, adjustLayout: Boolean = autoAdjustLayoutState.value) {
        val current = taskMapState.value
        if (city1 == city2 || city1 !in current.cities.indices || city2 !in current.cities.indices) return
        if (lengthKm <= 0f) return

        val road = Road(city1, city2)
        val newRoads = current.roadLengthsKm.toMutableMap()
        val roundedKm = (lengthKm * 10f).roundToInt() / 10f
        newRoads[road] = roundedKm.coerceAtLeast(0.1f)

        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        pheromonesState.value = emptyMap()

        updateTaskMap(
            current.copy(roadLengthsKm = newRoads),
            runAutoLayout = adjustLayout,
        )
        val c1Name = current.cities[city1].name
        val c2Name = current.cities[city2].name
        statusMessageState.value = "Set road $c1Name ↔ $c2Name to ${newRoads[road]} km."
    }

    fun addRoadFromCanvasClick(city1: Int, city2: Int) {
        val current = taskMapState.value
        if (city1 == city2 || city1 !in current.cities.indices || city2 !in current.cities.indices) return
        val existing = current.roadLengthsKm[Road(city1, city2)]
        if (existing != null) {
            statusMessageState.value = "Road between ${current.cities[city1].name} and ${current.cities[city2].name} already exists ($existing km)."
            return
        }
        val scaleKm = estimateCanvasScaleKm()
        val canvasDist = (current.cities[city1].position - current.cities[city2].position).getDistance()
        val estimatedKm = (canvasDist * scaleKm).roundToInt().toFloat().coerceAtLeast(10f)
        // When connecting on canvas using existing canvas distance, we don't need to force-relayout immediately unless enabled
        addOrUpdateRoad(city1, city2, estimatedKm, adjustLayout = false)
    }

    fun deleteRoad(road: Road) {
        val current = taskMapState.value
        if (!current.roadLengthsKm.containsKey(road)) return
        val newRoads = current.roadLengthsKm.toMutableMap()
        newRoads.remove(road)

        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        pheromonesState.value = emptyMap()

        updateTaskMap(
            current.copy(roadLengthsKm = newRoads),
            runAutoLayout = false,
        )
        val c1Name = current.cities.getOrNull(road.city1)?.name ?: "#${road.city1}"
        val c2Name = current.cities.getOrNull(road.city2)?.name ?: "#${road.city2}"
        statusMessageState.value = "Removed road $c1Name ↔ $c2Name."
    }

    fun connectAllCitiesFromCanvas() {
        val current = taskMapState.value
        val n = current.cities.size
        if (n < 2) return
        val scaleKm = estimateCanvasScaleKm()
        val newRoads = current.roadLengthsKm.toMutableMap()
        var addedCount = 0
        for (i in 0..<n) {
            for (j in i + 1..<n) {
                val road = Road(i, j)
                if (!newRoads.containsKey(road)) {
                    val dist = (current.cities[i].position - current.cities[j].position).getDistance()
                    newRoads[road] = (dist * scaleKm).roundToInt().toFloat().coerceAtLeast(5f)
                    addedCount++
                }
            }
        }
        if (addedCount > 0) {
            simulation = null
            simStatusState.value = SimulationStatus.UNINITIALIZED
            pheromonesState.value = emptyMap()
            updateTaskMap(current.copy(roadLengthsKm = newRoads), runAutoLayout = false)
            statusMessageState.value = "Connected $addedCount missing road(s) using canvas distances."
        } else {
            statusMessageState.value = "All city pairs are already connected."
        }
    }

    fun fixTriangleViolationsByClamping() {
        val current = taskMapState.value
        val n = current.cities.size
        if (n < 3) return
        val roads = current.roadLengthsKm.toMutableMap()
        var changed = true
        var iterations = 0
        while (changed && iterations < 50) {
            changed = false
            iterations++
            for (i in 0..<n) {
                for (j in i + 1..<n) {
                    val rIJ = Road(i, j)
                    val dIJ = roads[rIJ] ?: continue
                    for (k in j + 1..<n) {
                        val rIK = Road(i, k)
                        val rJK = Road(j, k)
                        val dIK = roads[rIK] ?: continue
                        val dJK = roads[rJK] ?: continue

                        if (dIJ > dIK + dJK) {
                            roads[rIJ] = ((dIK + dJK) * 10f).roundToInt() / 10f
                            changed = true
                        }
                        val updatedIJ = roads[rIJ]!!
                        if (dIK > updatedIJ + dJK) {
                            roads[rIK] = ((updatedIJ + dJK) * 10f).roundToInt() / 10f
                            changed = true
                        }
                        val updatedIK = roads[rIK]!!
                        if (dJK > updatedIJ + updatedIK) {
                            roads[rJK] = ((updatedIJ + updatedIK) * 10f).roundToInt() / 10f
                            changed = true
                        }
                    }
                }
            }
        }
        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        pheromonesState.value = emptyMap()
        updateTaskMap(current.copy(roadLengthsKm = roads))
        statusMessageState.value = "Clamped violating road lengths to satisfy the triangle inequality."
    }

    fun recalculateAllDistancesFromCanvas() {
        val current = taskMapState.value
        val scaleKm = estimateCanvasScaleKm()
        val newRoads = current.roadLengthsKm.mapValues { (road, _) ->
            val p1 = current.cities[road.city1].position
            val p2 = current.cities[road.city2].position
            ((p1 - p2).getDistance() * scaleKm).roundToInt().toFloat().coerceAtLeast(1f)
        }
        simulation = null
        simStatusState.value = SimulationStatus.UNINITIALIZED
        pheromonesState.value = emptyMap()
        updateTaskMap(current.copy(roadLengthsKm = newRoads), runAutoLayout = false)
        statusMessageState.value = "Recalculated road lengths from current canvas positions."
    }

    fun clearMap() {
        simulation = null
        plotter.reset()
        simStatusState.value = SimulationStatus.UNINITIALIZED
        ageState.value = 0
        pheromonesState.value = emptyMap()
        selectedCityIndexState.value = null
        updateTaskMap(
            TaskMap(
                name = "New Custom Map",
                cities = emptyList(),
                roadLengthsKm = emptyMap(),
                startCityIndex = 0,
                savedBestRouteCities = emptyList(),
            ),
            runAutoLayout = false,
        )
        statusMessageState.value = "Cleared map."
    }

    fun clearSavedSolution() {
        val current = taskMapState.value
        simulation = null
        plotter.reset()
        simStatusState.value = SimulationStatus.UNINITIALIZED
        ageState.value = 0
        pheromonesState.value = emptyMap()
        updateTaskMap(current.copy(savedBestRouteCities = emptyList()), runAutoLayout = false)
        statusMessageState.value = "Cleared saved route solution. Next run will start from scratch."
    }

    fun loadPreset(preset: TaskMap) {
        simulation = null
        plotter.reset()
        simStatusState.value = SimulationStatus.UNINITIALIZED
        ageState.value = 0
        pheromonesState.value = emptyMap()
        selectedCityIndexState.value = null
        updateTaskMap(preset, runAutoLayout = false)
        statusMessageState.value = "Loaded preset: ${preset.name}"
    }

    fun saveMapToFile() {
        val current = taskMapState.value.copy(
            savedBestRouteCities = bestRouteState.value.cities,
        )
        val savedPath = MapFileStorage.saveMapWithDialog(current)
        if (savedPath != null) {
            statusMessageState.value = "Saved map & solution to $savedPath"
        }
    }

    fun loadMapFromFile() {
        val loaded = MapFileStorage.loadMapWithDialog() ?: return
        val (loadedMap, fileName) = loaded
        simulation = null
        plotter.reset()
        simStatusState.value = SimulationStatus.UNINITIALIZED
        ageState.value = 0
        pheromonesState.value = emptyMap()
        selectedCityIndexState.value = null
        updateTaskMap(loadedMap, runAutoLayout = autoAdjustLayoutState.value)
        val hasSavedRoute = bestRouteState.value.isNotEmpty()
        statusMessageState.value = if (hasSavedRoute) {
            "Loaded \"$fileName\" with saved best route (${formatKm(bestRouteState.value.length)} km)."
        } else {
            "Loaded \"$fileName\"."
        }
    }

    suspend fun initSimulation(
        ages: Int,
        antsPercent: Float,
        eliteAntsPercent: Float,
        distanceImportance: Float,
        pheromoneImportance: Float,
        pheromoneEvaporationRate: Float,
        pheromoneDepositMultiplier: Float?,
        initialPheromoneLevel: Float,
        rng: Random,
    ): Boolean {
        val currentMap = taskMapState.value
        val validation = currentMap.validate()
        validationState.value = validation
        if (!validation.isValidForSimulation) {
            statusMessageState.value = "Cannot start solver: please fix graph validation errors first."
            return false
        }

        stopActiveSimulation()
        plotter.reset()

        val citiesCount = currentMap.cities.size
        val antsAmount = ceil(citiesCount * (antsPercent / 100f)).toInt().coerceAtLeast(1)

        // If Q is null (auto-mode), estimate Q from average road length * citiesCount
        val autoQ = run {
            val avgRoad = currentMap.roadLengthsKm.values.average().toFloat().takeIf { it > 0f } ?: 100f
            (avgRoad * citiesCount).coerceAtLeast(1f)
        }
        val effectiveQ = pheromoneDepositMultiplier ?: autoQ

        val cityGraph = currentMap.toCityGraph(initialPheromoneLevel)
        val weightingConfig = weightingConfig {
            distanceImportance(distanceImportance)
            pheromoneImportance(pheromoneImportance)
            pheromoneDepositRate(pheromoneEvaporationRate)
            pheromoneDepositMultiplier(effectiveQ)
        }

        // Use previous solution's best route as starting point if valid for this graph
        val previousBest = bestRouteState.value.takeIf { it.isNotEmpty() }
            ?: currentMap.validateSavedRoute()

        simulation = Simulation(
            graph = cityGraph,
            ages = ages,
            antsAmount = antsAmount,
            eliteAntsFraction = (eliteAntsPercent / 100f).coerceIn(0f, 1f),
            weightingConfig = weightingConfig,
            rng = rng,
            initialBestRoute = previousBest,
        ).also { sim ->
            pheromonesState.value = HashMap(sim.graph.roads)
            ageState.value = sim.currentAge
            bestRouteState.value = sim.bestRouteSoFar
            if (sim.bestRouteSoFar.isNotEmpty()) {
                taskMapState.value = currentMap.copy(savedBestRouteCities = sim.bestRouteSoFar.cities)
            }
        }
        simStatusState.value = SimulationStatus.NOT_STARTED
        statusMessageState.value = if (previousBest.isNotEmpty()) {
            "Initialized solver warm-started from previous best route (${formatKm(previousBest.length)} km)."
        } else {
            "Initialized solver ($antsAmount ants, $ages ages)."
        }
        return true
    }

    suspend fun stepSimulation(): SimulationStatus {
        val sim = simulation ?: return SimulationStatus.UNINITIALIZED
        if (simStatusState.value == SimulationStatus.FINISHED) return SimulationStatus.FINISHED

        val finished = sim.tick()
        if (sim.bestRouteSoFar.length.isFinite()) {
            plotter.recordIteration(sim.bestRouteSoFar.length)
        }
        pheromonesState.value = HashMap(sim.graph.roads)
        ageState.value = sim.currentAge
        bestRouteState.value = sim.bestRouteSoFar

        if (sim.bestRouteSoFar.isNotEmpty() &&
            sim.bestRouteSoFar.cities != taskMapState.value.savedBestRouteCities
        ) {
            taskMapState.value = taskMapState.value.copy(
                savedBestRouteCities = sim.bestRouteSoFar.cities,
            )
        }

        val newStatus = if (finished) SimulationStatus.FINISHED else SimulationStatus.RUNNING
        simStatusState.value = newStatus
        if (finished) {
            statusMessageState.value = if (sim.bestRouteSoFar.isNotEmpty()) {
                "Finished! Optimal route length: ${formatKm(sim.bestRouteSoFar.length)} km."
            } else {
                "Finished, but ants could not find a complete Hamiltonian cycle on this graph."
            }
        }
        return newStatus
    }

    fun buildRouteItinerary(route: CyclicRoute = bestRouteState.value): List<RouteLeg> {
        val current = taskMapState.value
        if (route.isEmpty() || current.cities.size < 2) return emptyList()
        val ordered = route.citiesStartingFrom(current.startCityIndex)
        if (ordered.size < 2) return emptyList()

        val legs = ArrayList<RouteLeg>(ordered.size)
        var cumulative = 0f
        for (i in ordered.indices) {
            val fromIdx = ordered[i]
            val toIdx = ordered[(i + 1) % ordered.size]
            val fromName = current.cities.getOrNull(fromIdx)?.name ?: "City #$fromIdx"
            val toName = current.cities.getOrNull(toIdx)?.name ?: "City #$toIdx"
            val dist = current.roadLengthsKm[Road(fromIdx, toIdx)] ?: 0f
            cumulative += dist
            legs.add(
                RouteLeg(
                    stepNumber = i + 1,
                    fromCityIndex = fromIdx,
                    fromCityName = fromName,
                    toCityIndex = toIdx,
                    toCityName = toName,
                    distanceKm = dist,
                    cumulativeKm = cumulative,
                )
            )
        }
        return legs
    }

    companion object {
        fun formatKm(value: Float): String {
            if (!value.isFinite()) return "∞"
            val rounded10 = (value * 10f).roundToInt()
            return if (rounded10 % 10 == 0) {
                (rounded10 / 10).toString()
            } else {
                "${rounded10 / 10}.${kotlin.math.abs(rounded10 % 10)}"
            }
        }
    }
}
