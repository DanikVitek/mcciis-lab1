package me.danikvitek.lab1

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.runBlocking
import me.danikvitek.lab1.data.MapJsonCodec
import me.danikvitek.lab1.data.MapPresets
import me.danikvitek.lab1.domain.Simulation
import me.danikvitek.lab1.domain.layout.GraphLayoutEngine
import me.danikvitek.lab1.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskSolverTest {
    @Test
    fun testTriangleInequalityValidation() {
        val cities = listOf(
            NamedCity("A", Offset(0.2f, 0.2f)),
            NamedCity("B", Offset(0.8f, 0.2f)),
            NamedCity("C", Offset(0.5f, 0.8f)),
        )
        val invalidRoads = mapOf(
            Road(0, 1) to 300f, // 300 > 100 + 120
            Road(0, 2) to 100f,
            Road(1, 2) to 120f,
        )
        val invalidMap = TaskMap(cities = cities, roadLengthsKm = invalidRoads)
        val res1 = invalidMap.validate()
        assertFalse(res1.isValidForSimulation)
        assertEquals(1, res1.triangleViolations.size)
        assertEquals(Road(0, 1), res1.triangleViolations.first().violatingRoad)

        val validRoads = mapOf(
            Road(0, 1) to 180f, // 180 <= 100 + 120
            Road(0, 2) to 100f,
            Road(1, 2) to 120f,
        )
        val validMap = TaskMap(cities = cities, roadLengthsKm = validRoads)
        val res2 = validMap.validate()
        assertTrue(res2.isValidForSimulation)
        assertTrue(res2.triangleViolations.isEmpty())
    }

    @Test
    fun testMapAndSolutionJsonRoundTrip() {
        val original = MapPresets.regionalRing5.copy(
            savedBestRouteCities = listOf(0, 1, 2, 3, 4),
        )
        val json = MapJsonCodec.encode(original)
        val decoded = MapJsonCodec.decode(json)

        assertEquals(original.name, decoded.name)
        assertEquals(original.cities.size, decoded.cities.size)
        assertEquals(original.roadLengthsKm, decoded.roadLengthsKm)
        assertEquals(listOf(0, 1, 2, 3, 4), decoded.savedBestRouteCities)

        val validatedRoute = decoded.validateSavedRoute()
        assertTrue(validatedRoute.isNotEmpty())
        assertEquals(530f, validatedRoute.length)
    }

    @Test
    fun testSimulationWarmStartFromPreviousBestRoute() = runBlocking {
        val map = MapPresets.regionalRing5
        // Suboptimal valid tour: 0 -> 2 -> 4 -> 1 -> 3 -> 0 (180 + 195 + 165 + 195 + 180 = 915 km)
        val suboptimalRoute = map.copy(savedBestRouteCities = listOf(0, 2, 4, 1, 3)).validateSavedRoute()
        assertEquals(915f, suboptimalRoute.length)

        val graph = map.toCityGraph(initialPheromoneLevel = 0.1f)
        val sim = Simulation(
            graph = graph,
            ages = 15,
            antsAmount = 10,
            eliteAntsFraction = 0.3f,
            weightingConfig = weightingConfig {
                distanceImportance(1f)
                pheromoneImportance(3f)
                pheromoneDepositMultiplier(500f)
            },
            initialBestRoute = suboptimalRoute,
        )

        // Immediately after initialization, bestRouteSoFar is the previous best route
        assertEquals(suboptimalRoute, sim.bestRouteSoFar)
        assertEquals(915f, sim.bestRouteSoFar.length)

        // Pheromones on edges of the initialBestRoute should be boosted above 0.1f
        assertTrue(graph.roads[Road(0, 2)]!! > 0.1f)

        // After running, the simulation should find a route <= 915f (the perimeter tour is 530f)
        while (!sim.tick()) {
            // run all ages
        }
        assertTrue(sim.bestRouteSoFar.length <= 915f)
    }

    @Test
    fun testGraphLayoutEngineAdjustsPositions() {
        val cities = listOf(
            NamedCity("A", Offset(0.5f, 0.5f)),
            NamedCity("B", Offset(0.5f, 0.5f)),
            NamedCity("C", Offset(0.5f, 0.5f)),
        )
        val roads = mapOf(
            Road(0, 1) to 100f,
            Road(1, 2) to 100f,
            Road(0, 2) to 100f,
        )
        val adjusted = GraphLayoutEngine.adjustPositions(cities, roads)
        val d01 = (adjusted[0].position - adjusted[1].position).getDistance()
        val d12 = (adjusted[1].position - adjusted[2].position).getDistance()
        val d02 = (adjusted[0].position - adjusted[2].position).getDistance()

        assertTrue(d01 > 0.1f)
        // Equilateral triangle should have roughly equal side lengths on the canvas
        assertTrue(kotlin.math.abs(d01 - d12) < 0.05f)
        assertTrue(kotlin.math.abs(d01 - d02) < 0.05f)
    }
}
