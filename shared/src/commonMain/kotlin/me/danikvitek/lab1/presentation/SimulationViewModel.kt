package me.danikvitek.lab1.presentation

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import me.danikvitek.lab1.domain.Simulation
import me.danikvitek.lab1.domain.model.CityGraph
import me.danikvitek.lab1.domain.model.CyclicRoute
import me.danikvitek.lab1.domain.model.Pheromone
import me.danikvitek.lab1.domain.model.Road
import me.danikvitek.lab1.domain.model.weightingConfig
import me.danikvitek.lab1.util.equalsElementWise
import me.danikvitek.lab1.util.hashCodeElementWise
import kotlin.random.Random

data class SimulationUIState(
    val cities: List<Offset> = emptyList(),
    val roads: Map<Road, Pheromone> = emptyMap(),
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

class SimulationViewModel() : ViewModel() {
    private var simulation: Simulation? = null
    val uiState: StateFlow<SimulationUIState>
        field = MutableStateFlow(SimulationUIState())

    val bestRouteChannel: ReceiveChannel<CyclicRoute>
        field = Channel<CyclicRoute>()
    val bestRoute: Flow<CyclicRoute> = bestRouteChannel.receiveAsFlow()

    context(_: Random)
    fun reset(
        ages: Int = 50,
        antsAmount: Int = 60,
        citiesAmount: Int = 60,
        distanceImportance: Float = 1F,
        pheromoneImportance: Float = 1F,
        pheromoneDepositRate: Float = 0.5F,
        pheromoneDepositMultiplier: Float = 1F,
        initialPheromoneLevel: Float = 0.01F,
    ) {
        val cityGraph = CityGraph.randomFullyConnected(
            initialPheromoneLevel = Pheromone(initialPheromoneLevel),
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
            return@run SimulationStatus.Finished
        }
        uiState.update {
            it.copy(
                age = currentAge,
                roads = graph.roads,
            )
        }
        return@run SimulationStatus.Running
    } ?: SimulationStatus.NotStarted
}

enum class SimulationStatus {
    Uninitialized,
    NotStarted,
    Running,
    Finished,
}
