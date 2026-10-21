package me.danikvitek.lab1.data

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.danikvitek.lab1.domain.layout.GraphLayoutEngine
import me.danikvitek.lab1.domain.model.NamedCity
import me.danikvitek.lab1.domain.model.Road
import me.danikvitek.lab1.domain.model.TaskMap
import me.danikvitek.lab1.util.AppLanguage
import me.danikvitek.lab1.util.AppLocaleManager

expect object MapFileStorage {
    fun saveMapWithDialog(map: TaskMap): String?
    fun loadMapWithDialog(): Pair<TaskMap, String>?
}

object MapPresets {
    private val isUk: Boolean
        get() = AppLocaleManager.currentLanguage.value == AppLanguage.UKRAINIAN

    val ukrainianCities: TaskMap
        get() {
            val uk = isUk
            val rawCities = listOf(
                NamedCity(if (uk) "Київ" else "Kyiv", Offset(0.48f, 0.30f)),
                NamedCity(if (uk) "Житомир" else "Zhytomyr", Offset(0.34f, 0.32f)),
                NamedCity(if (uk) "Вінниця" else "Vinnytsia", Offset(0.35f, 0.48f)),
                NamedCity(if (uk) "Львів" else "Lviv", Offset(0.10f, 0.38f)),
                NamedCity(if (uk) "Одеса" else "Odesa", Offset(0.46f, 0.85f)),
                NamedCity(if (uk) "Дніпро" else "Dnipro", Offset(0.75f, 0.56f)),
                NamedCity(if (uk) "Харків" else "Kharkiv", Offset(0.85f, 0.34f)),
                NamedCity(if (uk) "Полтава" else "Poltava", Offset(0.70f, 0.38f)),
            )
            // Indices: 0=Kyiv, 1=Zhytomyr, 2=Vinnytsia, 3=Lviv, 4=Odesa, 5=Dnipro, 6=Kharkiv, 7=Poltava
            val roads = mapOf(
                Road(0, 1) to 140f,
                Road(0, 2) to 268f,
                Road(0, 4) to 475f,
                Road(0, 5) to 477f,
                Road(0, 6) to 480f,
                Road(0, 7) to 342f,
                Road(1, 2) to 128f,
                Road(1, 3) to 400f,
                Road(2, 3) to 369f,
                Road(2, 4) to 429f,
                Road(3, 4) to 790f,
                Road(4, 5) to 454f,
                Road(4, 7) to 590f,
                Road(5, 6) to 218f,
                Road(5, 7) to 185f,
                Road(6, 7) to 143f,
                Road(2, 5) to 570f,
                Road(1, 7) to 480f,
            )
            return TaskMap(
                name = if (uk) "Обласні центри України" else "Ukraine Major Cities",
                cities = GraphLayoutEngine.adjustPositions(rawCities, roads),
                roadLengthsKm = roads,
                startCityIndex = 0,
            )
        }

    val completeLogistics6: TaskMap
        get() {
            val uk = isUk
            val rawCities = listOf(
                NamedCity(if (uk) "База Альфа" else "Depot Alpha", Offset(0.20f, 0.25f)),
                NamedCity(if (uk) "Північний хаб" else "North Hub", Offset(0.50f, 0.12f)),
                NamedCity(if (uk) "Східний термінал" else "East Terminal", Offset(0.84f, 0.32f)),
                NamedCity(if (uk) "Південний порт" else "South Port", Offset(0.76f, 0.78f)),
                NamedCity(if (uk) "Центральна станція" else "Central Station", Offset(0.45f, 0.52f)),
                NamedCity(if (uk) "Західний форпост" else "West Outpost", Offset(0.16f, 0.72f)),
            )
            val roads = mutableMapOf<Road, Float>()
            for (i in rawCities.indices) {
                for (j in i + 1..<rawCities.size) {
                    val distKm = kotlin.math.round((rawCities[i].position - rawCities[j].position).getDistance() * 300f)
                    roads[Road(i, j)] = distKm.coerceAtLeast(10f)
                }
            }
            return TaskMap(
                name = if (uk) "Логістична мережа (6 міст)" else "6-City Logistics Network",
                cities = GraphLayoutEngine.adjustPositions(rawCities, roads),
                roadLengthsKm = roads,
                startCityIndex = 0,
            )
        }

    val regionalRing5: TaskMap
        get() {
            val uk = isUk
            val rawCities = listOf(
                NamedCity(if (uk) "Альфа" else "Alpha", Offset(0.50f, 0.15f)),
                NamedCity(if (uk) "Браво" else "Bravo", Offset(0.85f, 0.40f)),
                NamedCity(if (uk) "Чарлі" else "Charlie", Offset(0.72f, 0.82f)),
                NamedCity(if (uk) "Дельта" else "Delta", Offset(0.28f, 0.82f)),
                NamedCity(if (uk) "Ехо" else "Echo", Offset(0.15f, 0.40f)),
            )
            val roads = mapOf(
                Road(0, 1) to 95f,
                Road(1, 2) to 110f,
                Road(2, 3) to 120f,
                Road(3, 4) to 110f,
                Road(4, 0) to 95f,
                Road(0, 2) to 180f,
                Road(0, 3) to 180f,
                Road(1, 3) to 195f,
                Road(1, 4) to 165f,
                Road(2, 4) to 195f,
            )
            return TaskMap(
                name = if (uk) "Симетричний п’ятикутник (5 міст)" else "5-City Symmetric Pentagon",
                cities = GraphLayoutEngine.adjustPositions(rawCities, roads),
                roadLengthsKm = roads,
                startCityIndex = 0,
            )
        }

    val all: List<TaskMap>
        get() = listOf(ukrainianCities, completeLogistics6, regionalRing5)
}

@Serializable
data class CityDto(
    val name: String,
    val x: Float,
    val y: Float,
)

@Serializable
data class RoadDto(
    val city1: Int,
    val city2: Int,
    val lengthKm: Float,
)

@Serializable
data class TaskMapDto(
    val name: String = "Loaded Map",
    val startCityIndex: Int = 0,
    val cities: List<CityDto> = emptyList(),
    val roads: List<RoadDto> = emptyList(),
    val savedBestRouteLengthKm: Float? = null,
    val savedBestRouteCities: List<Int> = emptyList(),
)

object MapJsonCodec {
    private val jsonFormat = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(map: TaskMap): String {
        val validatedRoute = map.validateSavedRoute()
        val routeCities = if (validatedRoute.isNotEmpty()) validatedRoute.cities else map.savedBestRouteCities
        val routeLength = if (validatedRoute.isNotEmpty() && validatedRoute.length.isFinite()) validatedRoute.length else null

        val dto = TaskMapDto(
            name = map.name,
            startCityIndex = map.startCityIndex,
            cities = map.cities.map { city ->
                CityDto(
                    name = city.name,
                    x = city.position.x,
                    y = city.position.y,
                )
            },
            roads = map.roadLengthsKm.entries
                .sortedWith(compareBy({ it.key.city1 }, { it.key.city2 }))
                .map { (road, lengthKm) ->
                    RoadDto(
                        city1 = road.city1,
                        city2 = road.city2,
                        lengthKm = lengthKm,
                    )
                },
            savedBestRouteLengthKm = routeLength,
            savedBestRouteCities = routeCities,
        )
        return jsonFormat.encodeToString(dto)
    }

    fun decode(json: String): TaskMap {
        val dto = jsonFormat.decodeFromString<TaskMapDto>(json)
        val cities = dto.cities.map { cityDto ->
            NamedCity(
                name = cityDto.name,
                position = Offset(
                    x = cityDto.x.coerceIn(0f, 1f),
                    y = cityDto.y.coerceIn(0f, 1f),
                ),
            )
        }
        val roads = buildMap {
            for (roadDto in dto.roads) {
                val c1 = roadDto.city1
                val c2 = roadDto.city2
                val len = roadDto.lengthKm
                if (c1 != c2 && c1 in cities.indices && c2 in cities.indices && len > 0f) {
                    put(Road(c1, c2), len)
                }
            }
        }
        return TaskMap(
            name = dto.name,
            cities = cities,
            roadLengthsKm = roads,
            startCityIndex = if (cities.isNotEmpty()) dto.startCityIndex.coerceIn(cities.indices) else 0,
            savedBestRouteCities = dto.savedBestRouteCities,
        )
    }
}
