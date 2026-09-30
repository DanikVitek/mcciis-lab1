package me.danikvitek.lab1.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import me.danikvitek.lab1.domain.Simulation
import kotlin.Float
import kotlin.Int
import kotlin.also
import kotlin.collections.List
import kotlin.collections.emptyList
import kotlin.collections.map
import kotlin.run

data class AntUIState(
    val position: Offset,
)

data class SimulationUIState(
    val ants: List<AntUIState> = emptyList(),
    val cities: List<Offset> = emptyList(),
    val age: Int = 0,
) {
    fun citiesOnCanvas(canvasSize: Size) = cities.map { city ->
        Offset(
            x = city.x * canvasSize.width,
            y = city.y * canvasSize.height,
        )
    }
}

class SimulationViewModel(private var simulation: Simulation? = null) : ViewModel() {
    val uiState: StateFlow<SimulationUIState>
        field = MutableStateFlow(SimulationUIState())

    private val bestRouteChannel = Channel<List<Offset>>()
    val bestRoute: Flow<List<Offset>> = bestRouteChannel.consumeAsFlow()

    fun reset(
        ages: Int = 50,
        antsAmount: Int = 60,
        citiesAmount: Int = 60,
        pheromoneImportance: Float = 1F,
        distanceImportance: Float = 3F,
        pheromoneEvaporationRate: Float = 0.5F,
        pheromoneDepositMultiplier: Float = 1F,
        initialPheromoneLevel: Float = 0.01F,
    ) {
        simulation = Simulation(
            bestRouteChannel = bestRouteChannel,
            ages = ages,
            antsAmount = antsAmount,
            citiesAmount = citiesAmount,
            pheromoneImportance = pheromoneImportance,
            distanceImportance = distanceImportance,
            pheromoneEvaporationRate = pheromoneEvaporationRate,
            pheromoneDepositMultiplier = pheromoneDepositMultiplier,
            initialPheromoneLevel = initialPheromoneLevel,
        ).also {
            uiState.value = SimulationUIState(
                ants = it.routes.map { route -> AntUIState(Offset(it.cities[route[0]])) },
                cities = it.cities.map(::Offset),
                age = it.currentAge,
            )
        }
        bestRouteChannel.trySendBlocking(emptyList())
    }

    suspend fun step(): SimulationStatus = simulation?.run {
        if (iterate()) {
            return@run SimulationStatus.Finished
        }
        uiState.update {
            it.copy(
                ants = routes.map { route -> AntUIState(Offset(cities[route[0]])) },
                age = currentAge,
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
