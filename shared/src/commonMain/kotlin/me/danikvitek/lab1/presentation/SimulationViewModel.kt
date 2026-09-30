package me.danikvitek.lab1.presentation

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import me.danikvitek.lab1.domain.Simulation

data class SimulationUIState(
    val ants: List<Offset> = emptyList(),
    val cities: List<Offset> = emptyList(),
    val pheromones: Array<FloatArray> = emptyArray(),
    val age: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimulationUIState

        if (age != other.age) return false
        if (ants != other.ants) return false
        if (cities != other.cities) return false
        if (!pheromones.contentDeepEquals(other.pheromones)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = age
        result = 31 * result + ants.hashCode()
        result = 31 * result + cities.hashCode()
        result = 31 * result + pheromones.contentDeepHashCode()
        return result
    }
}

class SimulationViewModel() : ViewModel() {
    private var simulation: Simulation? = null
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
                cities = it.cities.map(::Offset),
                pheromones = it.currentPheromones,
                age = it.currentAge,
            )
        }
        bestRouteChannel.trySendBlocking(emptyList())
    }

    suspend fun step(): SimulationStatus = simulation?.run {
        if (tick()) {
            return@run SimulationStatus.Finished
        }
        uiState.update {
            // Мурахи знаходяться на індексі (currentStep - 1). Якщо step = 0, епоха тільки завершилась.
            val currentCityIndex = if (currentStep == 0) citiesAmount - 1 else currentStep - 1
            it.copy(
                ants = routes.map { route -> Offset(cities[route[currentCityIndex]]) },
                age = currentAge,
                pheromones = currentPheromones,
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
