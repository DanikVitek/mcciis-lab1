package me.danikvitek.lab1.presentation

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import me.danikvitek.lab1.data.Plotter
import me.danikvitek.lab1.data.getPlotter
import me.danikvitek.lab1.domain.Simulation
import me.danikvitek.lab1.domain.model.*
import me.danikvitek.lab1.util.equalsElementWise
import me.danikvitek.lab1.util.hashCodeElementWise
import kotlin.random.Random

data class SimulationUIState(
    val cities: List<Offset> = emptyList(),
    val roads: Map<Road, Float> = emptyMap(),
    val age: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimulationUIState

        if (age != other.age) return false

        if (!cities.equalsElementWise(other.cities)) return false
        if (!roads.equalsElementWise(other.roads)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = age
        result = 31 * result + cities.hashCodeElementWise()
        result = 31 * result + roads.hashCodeElementWise()
        return result
    }
}

class SimulationViewModel(private val plotter: Plotter = getPlotter()) : ViewModel() {
    private var simulation: Simulation? = null
    val uiState: StateFlow<SimulationUIState>
        field = MutableStateFlow(SimulationUIState())
    val plot = plotter::plot

    val bestRouteChannel: ReceiveChannel<CyclicRoute>
        field = Channel<CyclicRoute>()
    val bestRoute: Flow<CyclicRoute> = bestRouteChannel.receiveAsFlow()

    inner class SimulationController {
        context(_: Random)
        suspend fun reset(
            ages: Int,
            antsAmount: Int,
            citiesAmount: Int,
            distanceImportance: Float,
            pheromoneImportance: Float,
            pheromoneDepositRate: Float = 0.5F,
            pheromoneDepositMultiplier: Float = 1F,
            initialPheromoneLevel: Float = 0.01F,
            preserveTopology: Boolean = false,
        ) {
            simulation?.cancel()
            plotter.reset()

            val cityGraph =
                if (preserveTopology && simulation != null)
                    simulation!!.graph.let {
                        CityGraph(
                            cities = it.cities,
                            roads = MutableRoads(
                                it.roads.asSequence().associateTo(mutableMapOf()) { entry ->
                                    entry.key to initialPheromoneLevel
                                },
                            ),
                        )
                    }
                else
                    CityGraph.randomFullyConnected(
                        initialPheromoneLevel = initialPheromoneLevel,
                        citiesAmount = citiesAmount,
                    )
            val weightingConfig = weightingConfig {
                pheromoneImportance(pheromoneImportance)
                distanceImportance(distanceImportance)
                pheromoneDepositRate(pheromoneDepositRate)
                pheromoneDepositMultiplier(pheromoneDepositMultiplier)
            }

            simulation = Simulation(
                bestRouteChannel = bestRouteChannel,
                ages = ages,
                antsAmount = antsAmount,
                graph = cityGraph,
                weightingConfig = weightingConfig,
            ).also {
                uiState.value = SimulationUIState(
                    cities = it.graph.cities,
                    roads = it.graph.roads,
                    age = it.currentAge,
                )
            }

            bestRouteChannel.trySendBlocking(CyclicRoute())
        }

        suspend fun step(): SimulationStatus = simulation?.run {
            if (tick()) {
                plotter.recordIteration(bestLengthSoFar)
                return@run SimulationStatus.FINISHED
            }
            plotter.recordIteration(bestLengthSoFar)
            uiState.update {
                it.copy(
                    age = currentAge,
                    roads = graph.roads,
                )
            }
            return@run SimulationStatus.RUNNING
        } ?: SimulationStatus.NOT_STARTED

    }
}

enum class SimulationStatus {
    UNINITIALIZED,
    NOT_STARTED,
    RUNNING,
    FINISHED,
}
