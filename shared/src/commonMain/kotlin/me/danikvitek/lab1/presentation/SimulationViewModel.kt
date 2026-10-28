package me.danikvitek.lab1.presentation

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.danikvitek.lab1.data.Plotter
import me.danikvitek.lab1.data.getPlotter
import me.danikvitek.lab1.domain.Simulation
import me.danikvitek.lab1.domain.model.*
import me.danikvitek.lab1.util.equalsElementWise
import me.danikvitek.lab1.util.hashCodeElementWise
import kotlin.random.Random

data class MapState(
    val cities: List<Offset> = emptyList(),
    val roads: Map<Road, Float> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MapState

        if (!cities.equalsElementWise(other.cities)) return false
        if (!roads.equalsElementWise(other.roads)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cities.hashCodeElementWise()
        result = 31 * result + roads.hashCodeElementWise()
        return result
    }
}

class SimulationViewModel(private val plotter: Plotter = getPlotter()) : ViewModel() {
    private var simulation: Simulation? = null
    private var lastDistribution: GraphDistribution? = null
    val simStatusState: StateFlow<SimulationStatus>
        field = MutableStateFlow(SimulationStatus.UNINITIALIZED)
    val mapState: StateFlow<MapState>
        field = MutableStateFlow(MapState())
    val ageState: StateFlow<Int>
        field = MutableStateFlow(0)
    val bestRouteState: StateFlow<CyclicRoute>
        field = MutableStateFlow(CyclicRoute())
    val plot = plotter::plot

    inner class SimulationController {
        val simStatus by this@SimulationViewModel::simStatusState
        val ageState by this@SimulationViewModel::ageState

        context(rng: Random)
        suspend fun reset(
            @IntRange(from = 1)
            ages: Int,

            @IntRange(from = 2)
            citiesAmount: Int,

            @IntRange(from = 1)
            antsAmount: Int = citiesAmount,

            @FloatRange(from = 0.0, to = 100.0)
            eliteAntsPercent: Float = 0f,

            @FloatRange(from = 0.0)
            distanceImportance: Float,

            @FloatRange(from = 0.0)
            pheromoneImportance: Float,

            @FloatRange(from = 0.0, to = 1.0)
            pheromoneDepositRate: Float = 0.5F,

            @FloatRange(from = 0.0, fromInclusive = false)
            pheromoneDepositMultiplier: Float = 1F,

            @FloatRange(from = 0.0, fromInclusive = false)
            initialPheromoneLevel: Float = 0.01F,

            distribution: GraphDistribution = GraphDistribution.RANDOM_UNIFORM,
            preserveTopology: Boolean = false,
            reuseBestRoute: Boolean = true,
        ) {
            val prevSimulation = simulation
            simulation = null
            prevSimulation?.cancel()
            plotter.reset()

            val keepPrevious = preserveTopology &&
                prevSimulation != null &&
                prevSimulation.graph.cities.size == citiesAmount &&
                lastDistribution == distribution
            lastDistribution = distribution
            val cityGraph =
                if (keepPrevious)
                    prevSimulation.graph.let {
                        CityGraph(
                            cities = it.cities,
                            roads = it.mutableRoads.resetAllPheromones(initialPheromoneLevel),
                            customRoadLengths = it.customRoadLengths,
                        )
                    }
                else
                    when (distribution) {
                        GraphDistribution.RANDOM_UNIFORM -> CityGraph.randomFullyConnected(
                            initialPheromoneLevel = initialPheromoneLevel,
                            citiesAmount = citiesAmount,
                        )
                        GraphDistribution.RING -> CityGraph.ringFullyConnected(
                            initialPheromoneLevel = initialPheromoneLevel,
                            citiesAmount = citiesAmount,
                        )
                        GraphDistribution.NATURAL_GRID -> CityGraph.naturalGrid(
                            initialPheromoneLevel = initialPheromoneLevel,
                            citiesAmount = citiesAmount,
                        )
                    }
            val previousBestRoute = if (keepPrevious && reuseBestRoute) prevSimulation.bestRouteSoFar else CyclicRoute()
            val weightingConfig = weightingConfig {
                pheromoneImportance(pheromoneImportance)
                distanceImportance(distanceImportance)
                pheromoneDepositRate(pheromoneDepositRate)
                pheromoneDepositMultiplier(pheromoneDepositMultiplier)
            }

            simulation = Simulation(
                ages = ages,
                antsAmount = antsAmount,
                eliteAntsFraction = eliteAntsPercent / 100,
                graph = cityGraph,
                weightingConfig = weightingConfig,
                rng = rng,
                initialBestRoute = previousBestRoute,
            ).also {
                mapState.value = MapState(
                    cities = it.graph.cities,
                    roads = it.graph.mutableRoads.copy(),
                )
                this@SimulationViewModel.ageState.value = it.currentAge
                bestRouteState.value = it.bestRouteSoFar
            }
            this@SimulationViewModel.simStatusState.value = SimulationStatus.NOT_STARTED
        }

        fun start() {
            if (simulation != null && simStatusState.value == SimulationStatus.NOT_STARTED) {
                this@SimulationViewModel.simStatusState.value = SimulationStatus.RUNNING
            }
        }

        suspend fun step() {
            val currentSim = simulation ?: run {
                this@SimulationViewModel.simStatusState.value = SimulationStatus.UNINITIALIZED
                return
            }
            if (simStatus.value == SimulationStatus.FINISHED) return
            this@SimulationViewModel.simStatusState.value = SimulationStatus.RUNNING
            val finished = currentSim.tick()
            if (simulation !== currentSim) return
            if (currentSim.bestRouteSoFar.length.isFinite()) {
                plotter.recordIteration(currentSim.bestRouteSoFar.length)
            }
            mapState.update {
                it.copy(roads = currentSim.graph.mutableRoads.copy())
            }
            this@SimulationViewModel.ageState.value = currentSim.currentAge
            bestRouteState.value = currentSim.bestRouteSoFar
            this@SimulationViewModel.simStatusState.value =
                if (finished) SimulationStatus.FINISHED else SimulationStatus.RUNNING
        }
    }
}

enum class SimulationStatus {
    UNINITIALIZED,
    NOT_STARTED,
    RUNNING,
    FINISHED,
}
