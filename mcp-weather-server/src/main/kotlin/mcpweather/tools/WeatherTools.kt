package mcpweather.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

private data class WeatherProfile(
    val condition: String,
    val tempCelsius: Int,
    val humidity: Int,
    val windKmh: Int,
)

private val CITY_PROFILES: Map<String, WeatherProfile> = mapOf(
    "москва"      to WeatherProfile("Облачно",       -3,  78, 12),
    "moscow"      to WeatherProfile("Cloudy",         -3,  78, 12),
    "санкт-петербург" to WeatherProfile("Метель",     -6,  85, 20),
    "saint petersburg" to WeatherProfile("Blizzard",  -6,  85, 20),
    "лондон"      to WeatherProfile("Дождь",          8,  88, 18),
    "london"      to WeatherProfile("Rain",            8,  88, 18),
    "токио"       to WeatherProfile("Ясно",           15,  60, 8),
    "tokyo"       to WeatherProfile("Clear",          15,  60, 8),
    "нью-йорк"    to WeatherProfile("Пасмурно",        4,  72, 22),
    "new york"    to WeatherProfile("Overcast",        4,  72, 22),
    "берлин"      to WeatherProfile("Переменная облачность", 3, 70, 14),
    "berlin"      to WeatherProfile("Partly cloudy",   3,  70, 14),
    "дубай"       to WeatherProfile("Солнечно",       28,  40,  5),
    "dubai"       to WeatherProfile("Sunny",          28,  40,  5),
)

private fun profileFor(city: String): WeatherProfile {
    val key = city.trim().lowercase()
    return CITY_PROFILES[key] ?: WeatherProfile(
        condition = "Переменная облачность",
        tempCelsius = Random.nextInt(-5, 20),
        humidity = Random.nextInt(50, 90),
        windKmh = Random.nextInt(5, 30),
    )
}

fun Server.registerWeatherTools() {

    // ── get_current_weather ──────────────────────────────────────────────────
    addTool(
        name = "get_current_weather",
        description = "Get current weather conditions for a city",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("city") {
                    put("type", "string")
                    put("description", "City name (e.g. Moscow, London, Tokyo)")
                }
            },
            required = listOf("city"),
        ),
    ) { request ->
        val city = request.arguments?.get("city")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'city' is required")), isError = true)

        val w = profileFor(city)
        val text = buildString {
            appendLine("=== Current weather in $city ===")
            appendLine("Condition : ${w.condition}")
            appendLine("Temp      : ${w.tempCelsius}°C (${w.tempCelsius * 9 / 5 + 32}°F)")
            appendLine("Humidity  : ${w.humidity}%")
            appendLine("Wind      : ${w.windKmh} km/h")
        }
        CallToolResult(content = listOf(TextContent(text.trimEnd())))
    }

    // ── get_forecast ─────────────────────────────────────────────────────────
    addTool(
        name = "get_forecast",
        description = "Get a multi-day weather forecast for a city",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("city") {
                    put("type", "string")
                    put("description", "City name")
                }
                putJsonObject("days") {
                    put("type", "number")
                    put("description", "Number of forecast days (1–7, default 3)")
                }
            },
            required = listOf("city"),
        ),
    ) { request ->
        val city = request.arguments?.get("city")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'city' is required")), isError = true)
        val days = request.arguments?.get("days")?.jsonPrimitive?.content?.toIntOrNull()
            ?.coerceIn(1, 7) ?: 3

        val base = profileFor(city)
        val sb = StringBuilder()
        sb.appendLine("=== $days-day forecast for $city ===")
        val conditions = listOf("Ясно", "Облачно", "Дождь", "Переменная облачность", "Туман")
        repeat(days) { i ->
            val delta = Random.nextInt(-3, 4)
            val cond = if (i == 0) base.condition else conditions.random()
            sb.appendLine("Day ${i + 1}: $cond, ${base.tempCelsius + delta}°C, wind ${base.windKmh + Random.nextInt(-5, 6)} km/h")
        }
        CallToolResult(content = listOf(TextContent(sb.toString().trimEnd())))
    }

    // ── get_air_quality ──────────────────────────────────────────────────────
    addTool(
        name = "get_air_quality",
        description = "Get air quality index (AQI) and pollutant levels for a city",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("city") {
                    put("type", "string")
                    put("description", "City name")
                }
            },
            required = listOf("city"),
        ),
    ) { request ->
        val city = request.arguments?.get("city")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'city' is required")), isError = true)

        val aqi = Random.nextInt(10, 150)
        val category = when {
            aqi <= 50  -> "Good"
            aqi <= 100 -> "Moderate"
            else       -> "Unhealthy for sensitive groups"
        }
        val text = buildString {
            appendLine("=== Air quality in $city ===")
            appendLine("AQI      : $aqi ($category)")
            appendLine("PM2.5    : ${Random.nextInt(5, 60)} μg/m³")
            appendLine("PM10     : ${Random.nextInt(10, 80)} μg/m³")
            appendLine("NO₂      : ${Random.nextInt(10, 50)} μg/m³")
        }
        CallToolResult(content = listOf(TextContent(text.trimEnd())))
    }
}
