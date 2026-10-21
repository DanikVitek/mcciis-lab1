package me.danikvitek.lab1

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import me.danikvitek.lab1.presentation.SimulationViewModel
import me.danikvitek.lab1.presentation.TaskSolverViewModel
import me.danikvitek.lab1.ui.ExperimentRunnerScreen
import me.danikvitek.lab1.ui.TaskSolverScreen

@Serializable
sealed interface AppRoute : NavKey {
    val title: String

    @Serializable
    data object TaskSolver : AppRoute {
        override val title: String = "Intellectual Information System (Graph Builder & Task Solver)"
    }

    @Serializable
    data object ExperimentRunner : AppRoute {
        override val title: String = "Experiment Runner (Random Graph Simulation)"
    }

    companion object {
        val entries: List<AppRoute> = listOf(TaskSolver, ExperimentRunner)
    }
}

private val appNavSavedStateConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AppRoute.TaskSolver::class)
            subclass(AppRoute.ExperimentRunner::class)
        }
    }
}

enum class CanvasOrPlot {
    CANVAS, PLOT
}

@Composable
@Preview
fun App() {
    AntColonyAppTheme {
        val backStack = rememberNavBackStack(appNavSavedStateConfig, AppRoute.TaskSolver)
        val currentRoute = backStack.lastOrNull() ?: AppRoute.TaskSolver

        // Host ViewModels at the App scope so state is preserved when switching tabs
        val taskSolverViewModel: TaskSolverViewModel = viewModel { TaskSolverViewModel() }
        val experimentViewModel: SimulationViewModel = viewModel { SimulationViewModel() }

        Scaffold(
            topBar = {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "ACO Route Optimization System",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppRoute.entries.forEach { route ->
                                val selected = currentRoute == route
                                val navigateToRoute = {
                                    if (currentRoute != route) {
                                        backStack.removeAll { it == route }
                                        backStack.add(route)
                                    }
                                }
                                if (selected) {
                                    Button(onClick = navigateToRoute) {
                                        Text(route.title)
                                    }
                                } else {
                                    OutlinedButton(onClick = navigateToRoute) {
                                        Text(route.title)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<AppRoute.TaskSolver> {
                        TaskSolverScreen(viewModel = taskSolverViewModel)
                    }
                    entry<AppRoute.ExperimentRunner> {
                        ExperimentRunnerScreen(viewModel = experimentViewModel)
                    }
                },
            )
        }
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
