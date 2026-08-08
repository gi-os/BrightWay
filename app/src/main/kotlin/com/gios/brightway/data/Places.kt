package com.gios.brightway.data

import org.json.JSONArray
import org.json.JSONObject

/** A named point. The only shape the whole app passes around. */
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("address", address).put("lat", lat).put("lon", lon)

    companion object {
        fun fromJson(o: JSONObject): Place = Place(
            name = o.optString("name"),
            address = o.optString("address"),
            lat = o.optDouble("lat"),
            lon = o.optDouble("lon"),
        )

        fun listFromJson(raw: String?): List<Place> = runCatching {
            val arr = JSONArray(raw ?: return emptyList())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        fun listToJson(list: List<Place>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}
