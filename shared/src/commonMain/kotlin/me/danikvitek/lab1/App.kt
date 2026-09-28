package me.danikvitek.lab1

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.danikvitek.lab1.presentation.SimulationStatus
import me.danikvitek.lab1.presentation.SimulationViewModel
import java.util.*
import kotlin.concurrent.timerTask

@Composable
@Preview
fun App(

) {
    AntColonyAppTheme {
        AntColonyApp()
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

@Composable
fun AntColonyApp(
    viewModel: SimulationViewModel = viewModel { SimulationViewModel() },
) {
    var citiesAmount by remember { mutableIntStateOf(10) }
    val uiState by viewModel.uiState.collectAsState()
    var simStatus by remember { mutableStateOf(SimulationStatus.NotStarted) }
    val timer = Timer("Simulation step", true)
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().then(Modifier.padding(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .border(1.dp, MaterialTheme.colorScheme.onBackground),
            contentDescription = "Ant colony simulation",
        ) {
            drawPoints(
                points = uiState.cities.map { Offset(it.x * size.width, it.y * size.height) },
                pointMode = PointMode.Points,
                color = Color.Black,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawPoints(
                points = uiState.ants.map { ant -> Offset(ant.position.x * size.width, ant.position.y * size.height) },
                pointMode = PointMode.Points,
                color = Color.Red,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            TextField(
                label = { Text("Number of cities") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                value = citiesAmount.toString(),
                onValueChange = { newValue ->
                    citiesAmount = newValue.toIntOrNull()?.coerceIn(2, 100) ?: citiesAmount
                },
            )
            Button(
                onClick = when (simStatus) {
                    SimulationStatus.Uninitialized -> {
                        {
                            viewModel.reset(citiesAmount = citiesAmount, antsAmount = citiesAmount)
                            simStatus = SimulationStatus.NotStarted
                        }
                    }

                    SimulationStatus.NotStarted -> {
                        {
                            viewModel.reset(citiesAmount = citiesAmount, antsAmount = citiesAmount)
                        }
                    }

                    SimulationStatus.Running -> {
                        {
                            timer.cancel()
                            viewModel.reset(citiesAmount = citiesAmount, antsAmount = citiesAmount)
                            simStatus = SimulationStatus.NotStarted
                        }
                    }

                    SimulationStatus.Finished -> {
                        {
                            timer.purge()
                            viewModel.reset(citiesAmount = citiesAmount, antsAmount = citiesAmount)
                            simStatus = SimulationStatus.NotStarted
                        }
                    }

                },
            ) {
                Text(
                    text = if (simStatus == SimulationStatus.Uninitialized) "Init" else "Reset",
                )
            }
            Button(
                enabled = simStatus == SimulationStatus.NotStarted,
                onClick = {
                    timer.scheduleAtFixedRate(
                        timerTask {
                            if (viewModel.step() != SimulationStatus.Running) {
                                cancel()
                                simStatus = SimulationStatus.Finished
                            }
                        },
                        0, 100,
                    )
                    simStatus = SimulationStatus.Running
                },
            ) {
                Text(
                    text = "Run",
                )
            }
        }
    }
}