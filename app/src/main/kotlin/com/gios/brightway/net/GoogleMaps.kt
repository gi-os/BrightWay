package com.gios.brightway.net

import com.gios.brightway.data.Place
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** One step of a trip, already flattened into what the nav screen draws. */
data class Step(
    val instruction: String,
    val maneuver: String,          // TURN_LEFT, TURN_RIGHT, DEPART, ... or "" / "TRANSIT"
    val distanceM: Double,
    val durationS: Long,
    val endLat: Double,
    val endLon: Double,
    // Transit-only fields; null on a walking step.
    val transit: TransitRide? = null,
)

data class TransitRide(
    val lineName: String,          // "L", "6", "B52"
    val lineColorHex: String,      // "#A7A9AC" — real MTA line colour, drawn when colour is on
    val textColorHex: String,
    val headsign: String,
    val vehicle: String,           // SUBWAY, BUS, RAIL...
    val boardStop: String,
    val exitStop: String,
    val stopCount: Int,
    val departHHMM: String,
    val arriveHHMM: String,
)

data class RouteOption(
    val mode: String,              // "WALK" or "TRANSIT"
    val summary: String,           // "18 min · 0.9 mi" or "31 min · L → 6"
    val durationS: Long,
    val distanceM: Double,
    val steps: List<Step>,
    /** Google encoded polyline for the whole route; the map view hands it straight back
     *  to the Static Maps API without ever decoding it. */
    val encodedPolyline: String = "",
)

class ApiKeyMissing : IOException("No API key — scan one in Settings")
class ApiError(msg: String) : IOException(msg)

/**
 * Google Maps Platform over plain REST. No SDK, no Play Services — an OkHttp call with
 * X-Goog-Api-Key is all the phone needs. The key is the user's own, entered by QR.
 */
class GoogleMaps(private val apiKey: () -> String) {
    private val http = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun key(): String = apiKey().ifBlank { throw ApiKeyMissing() }

    /** Places Text Search (New). Natural queries — "wing shop delancey" works. */
    suspend fun search(query: String, nearLat: Double?, nearLon: Double?): List<Place> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("textQuery", query)
            if (nearLat != null && nearLon != null) {
                body.put("locationBias", JSONObject().put("circle", JSONObject()
                    .put("center", JSONObject().put("latitude", nearLat).put("longitude", nearLon))
                    .put("radius", 20_000.0)))
            }
            val req = Request.Builder()
                .url("https://places.googleapis.com/v1/places:searchText")
                .header("X-Goog-Api-Key", key())
                .header("X-Goog-FieldMask",
                    "places.displayName,places.formattedAddress,places.location")
                .post(body.toString().toRequestBody(json))
                .build()
            val o = execute(req)
            val places = o.optJSONArray("places") ?: JSONArray()
            (0 until places.length()).mapNotNull { i ->
                val p = places.getJSONObject(i)
                val loc = p.optJSONObject("location") ?: return@mapNotNull null
                Place(
                    name = p.optJSONObject("displayName")?.optString("text") ?: "?",
                    address = p.optString("formattedAddress"),
                    lat = loc.optDouble("latitude"),
                    lon = loc.optDouble("longitude"),
                )
            }.take(8)
        }

    /** Routes API computeRoutes; one call per travel mode. */
    suspend fun routes(
        fromLat: Double, fromLon: Double, to: Place, mode: String,
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("origin", latLng(fromLat, fromLon))
            .put("destination", latLng(to.lat, to.lon))
            .put("travelMode", mode)
            .put("computeAlternativeRoutes", mode == "TRANSIT")
            .put("languageCode", "en-US")
            .put("units", "IMPERIAL")
        if (mode == "TRANSIT") {
            body.put("departureTime", Instant.now().toString())
            body.put("transitPreferences",
                JSONObject().put("routingPreference", "FEWER_TRANSFERS"))
        }
        val req = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .header("X-Goog-Api-Key", key())
            // Coarse mask on purpose: we want legs whole, and the response for a single
            // trip is a few KB either way.
            .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.legs,routes.polyline.encodedPolyline")
            .post(body.toString().toRequestBody(json))
            .build()
        val o = execute(req)
        val routes = o.optJSONArray("routes") ?: JSONArray()
        (0 until routes.length()).map { i -> parseRoute(routes.getJSONObject(i), mode) }
    }

    private fun latLng(lat: Double, lon: Double) = JSONObject().put("location",
        JSONObject().put("latLng", JSONObject().put("latitude", lat).put("longitude", lon)))

    private fun execute(req: Request): JSONObject {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw ApiError(msg?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}")
            }
            return JSONObject(text)
        }
    }

    internal fun parseRoute(r: JSONObject, mode: String): RouteOption {
        val durationS = parseSeconds(r.optString("duration"))
        val distanceM = r.optDouble("distanceMeters", 0.0)
        val steps = mutableListOf<Step>()
        val legs = r.optJSONArray("legs") ?: JSONArray()
        for (li in 0 until legs.length()) {
            val stepArr = legs.getJSONObject(li).optJSONArray("steps") ?: continue
            for (si in 0 until stepArr.length()) {
                val s = stepArr.getJSONObject(si)
                val end = s.optJSONObject("endLocation")?.optJSONObject("latLng")
                val nav = s.optJSONObject("navigationInstruction")
                val transit = s.optJSONObject("transitDetails")?.let(::parseTransit)
                val instruction = nav?.optString("instructions").orEmpty().ifBlank {
                    transit?.let { "Take the ${it.lineName} toward ${it.headsign}" } ?: "Continue"
                }
                // Zero-length walking steps are navigational noise: the origin "depart" step
                // Google prepends (same street text as the real first step — the old
                // duplicate-first-step bug) and any bare "Continue". Transit steps always stay.
                val dist = s.optDouble("distanceMeters", 0.0)
                if (transit == null && dist == 0.0) continue
                val step = Step(
                    instruction = instruction,
                    maneuver = if (transit != null) "TRANSIT"
                        else nav?.optString("maneuver").orEmpty(),
                    distanceM = dist,
                    durationS = parseSeconds(s.optString("staticDuration")),
                    endLat = end?.optDouble("latitude") ?: 0.0,
                    endLon = end?.optDouble("longitude") ?: 0.0,
                    transit = transit,
                )
                // Belt and braces: never list two consecutive walking steps with the same
                // words (e.g. a repeated "Continue onto <street>" after a merge).
                if (transit == null) {
                    val prev = steps.lastOrNull()
                    if (prev != null && prev.transit == null && prev.instruction == instruction) continue
                }
                steps += step
            }
        }
        val rides = steps.mapNotNull { it.transit }
        val summary = if (mode == "TRANSIT" && rides.isNotEmpty()) {
            rides.joinToString(" → ") { it.lineName }
        } else {
            com.gios.brightway.util.Geo.prettyDistance(distanceM)
        }
        val poly = r.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
        return RouteOption(mode, summary, durationS, distanceM, steps, poly)
    }

    private fun parseTransit(t: JSONObject): TransitRide {
        val line = t.optJSONObject("transitLine")
        val stops = t.optJSONObject("stopDetails")
        return TransitRide(
            lineName = line?.optString("nameShort").orEmpty()
                .ifBlank { line?.optString("name").orEmpty() },
            lineColorHex = line?.optString("color").orEmpty().ifBlank { "#444444" },
            textColorHex = line?.optString("textColor").orEmpty().ifBlank { "#FFFFFF" },
            headsign = t.optString("headsign"),
            vehicle = line?.optJSONObject("vehicle")?.optString("type").orEmpty(),
            boardStop = stops?.optJSONObject("departureStop")?.optString("name").orEmpty(),
            exitStop = stops?.optJSONObject("arrivalStop")?.optString("name").orEmpty(),
            stopCount = t.optInt("stopCount"),
            departHHMM = hhmm(stops?.optString("departureTime")),
            arriveHHMM = hhmm(stops?.optString("arrivalTime")),
        )
    }

    companion object {
        /** Routes durations arrive as "1234s". */
        fun parseSeconds(raw: String?): Long =
            raw.orEmpty().removeSuffix("s").toLongOrNull() ?: 0L

        fun hhmm(rfc3339: String?): String = runCatching {
            val inst = Instant.parse(rfc3339 ?: return "")
            val t = inst.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            String.format("%d:%02d", ((t.hour + 11) % 12) + 1, t.minute)
        }.getOrDefault("")
    }
}
