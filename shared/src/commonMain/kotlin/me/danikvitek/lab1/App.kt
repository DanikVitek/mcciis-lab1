package me.danikvitek.lab1

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import lab1.shared.generated.resources.*
import me.danikvitek.lab1.presentation.SimulationViewModel
import me.danikvitek.lab1.presentation.TaskSolverViewModel
import me.danikvitek.lab1.ui.ExperimentRunnerScreen
import me.danikvitek.lab1.ui.TaskSolverScreen
import me.danikvitek.lab1.util.AppLanguage
import me.danikvitek.lab1.util.AppLocaleManager
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Serializable
sealed interface AppRoute : NavKey {
    val titleRes: StringResource

    @Serializable
    data object TaskSolver : AppRoute {
        @Transient
        override val titleRes: StringResource = Res.string.nav_task_solver
    }

    @Serializable
    data object ExperimentRunner : AppRoute {
        @Transient
        override val titleRes: StringResource = Res.string.nav_experiment_runner
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
        val appLanguage by AppLocaleManager.currentLanguage.collectAsState()
        val backStack = rememberNavBackStack(appNavSavedStateConfig, AppRoute.TaskSolver)
        val currentRoute = backStack.lastOrNull() ?: AppRoute.TaskSolver

        // Host ViewModels at the App scope so state is preserved when switching tabs or languages
        val taskSolverViewModel: TaskSolverViewModel = viewModel { TaskSolverViewModel() }
        val experimentViewModel: SimulationViewModel = viewModel { SimulationViewModel() }

        key(appLanguage) {
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
                                text = stringResource(Res.string.app_title),
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
                                            Text(stringResource(route.titleRes))
                                        }
                                    } else {
                                        OutlinedButton(onClick = navigateToRoute) {
                                            Text(stringResource(route.titleRes))
                                        }
                                    }
                                }

                                SingleChoiceSegmentedButtonRow {
                                    AppLanguage.entries.forEachIndexed { index, language ->
                                        SegmentedButton(
                                            selected = appLanguage == language,
                                            onClick = { AppLocaleManager.setLanguage(language) },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = AppLanguage.entries.size,
                                            ),
                                            label = { Text(language.label) },
                                        )
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
}

@Composable
fun AntColonyAppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme {
        content()
    }
}
