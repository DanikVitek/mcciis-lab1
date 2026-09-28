package me.danikvitek.lab1.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.danikvitek.lab1.domain.Simulation

data class AntUIState(
    val position: Offset,
)

data class SimulationUIState(
    val ants: List<AntUIState> = emptyList(),
    val cities: List<Offset> = emptyList(),
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

    fun reset(
        ages: Int = 50,
        antsAmount: Int = 60,
        citiesAmount: Int = 60,
        pheromonePerRoute: Float = 1F,
        distanceOverPheromoneImportance: Float = 3F,
        pheromoneByArent: Float = .5F,
        pheromoneOnRoute: Float = 1F,
        pheromone0: Float = 1F,
    ) {
        simulation = Simulation(
            ages = ages,
            antsAmount = antsAmount,
            citiesAmount = citiesAmount,
            pheromonePerRoute = pheromonePerRoute,
            distanceOverPheromoneImportance = distanceOverPheromoneImportance,
            pheromoneByArent = pheromoneByArent,
            pheromoneOnRoute = pheromoneOnRoute,
            pheromone0 = pheromone0,
        )

        uiState.value = SimulationUIState(
            cities = simulation!!.cities.map(::Offset),
            ants = emptyList(), /*simulation!!.routes1.map { route -> AntUIState(Offset(simulation!!.cities[route[0]])) }*/
        )
    }

    fun step(): SimulationStatus {
        return simulation?.let { sim ->
            if (sim.iterate()) {
                return@let SimulationStatus.Finished
            }
            uiState.update {
                it.copy(
                    ants = sim.routes1.map { route -> AntUIState(Offset(sim.cities[route[sim.currentAge]])) },
                )
            }
            return@let SimulationStatus.Running
        } ?: SimulationStatus.NotStarted
    }
}

enum class SimulationStatus {
    Uninitialized,
    NotStarted,
    Running,
    Finished,
}
