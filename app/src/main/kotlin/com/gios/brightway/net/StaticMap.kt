package com.gios.brightway.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.gios.brightway.util.Polyline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The map view is the Maps Static API: one GET, one finished PNG with the route already
 * drawn on it. No tiles, no projection math, no Maps SDK (which needs the Play Services
 * this phone doesn't have). The panel renders it greyscale anyway — the style params just
 * strip POI clutter that would dither into noise at LPIII contrast.
 *
 * Failures come back as words, not null: a swallowed error here is indistinguishable from
 * "loading map…" forever, which is exactly the bug it used to have.
 */
class MapFetch(val bitmap: Bitmap?, val error: String? = null)

class StaticMap(private val apiKey: () -> String) {
    private val http = OkHttpClient()

    // A wheel-zoom session revisits the same few URLs; ~8 x 640x1280 bitmaps ≈ 25 MB tops.
    private val cache = LruCache<String, Bitmap>(8)

    /**
     * One picture, three optional layers: a route ([encodedPolyline]), the user's position
     * ([userLat]/[userLon], tiny marker), and the destination (mid marker). zoom == null
     * auto-fits everything on the picture; otherwise the view is [centerLat]/[centerLon]
     * at [zoom].
     */
    suspend fun fetch(
        destLat: Double, destLon: Double,
        encodedPolyline: String = "",
        userLat: Double? = null, userLon: Double? = null,
        centerLat: Double? = null, centerLon: Double? = null,
        zoom: Int? = null,
    ): MapFetch = withContext(Dispatchers.IO) {
        val key = apiKey()
        if (key.isBlank()) return@withContext MapFetch(null, "No API key — scan one in Settings")
        // Static Maps rejects URLs past ~8k chars; a long transit polyline alone can blow
        // that. Thin it — 150 points is indistinguishable at panel resolution.
        val enc = if (encodedPolyline.length > 5800) {
            Polyline.encode(Polyline.downsample(Polyline.decode(encodedPolyline), 150))
        } else encodedPolyline
        val sb = StringBuilder("https://maps.googleapis.com/maps/api/staticmap")
        sb.append("?size=640x640&scale=2&maptype=roadmap")
        sb.append("&style=feature:poi%7Cvisibility:off")
        sb.append("&style=feature:transit%7Celement:labels%7Cvisibility:off")
        if (enc.isNotBlank()) {
            sb.append("&path=weight:5%7Ccolor:0x000000ff%7Cenc:")
                .append(java.net.URLEncoder.encode(enc, "UTF-8"))
        }
        sb.append("&markers=size:mid%7C").append(destLat).append(',').append(destLon)
        if (userLat != null && userLon != null) {
            sb.append("&markers=size:tiny%7C").append(userLat).append(',').append(userLon)
        }
        if (zoom != null && centerLat != null && centerLon != null) {
            sb.append("&center=").append(centerLat).append(',').append(centerLon)
            sb.append("&zoom=").append(zoom.coerceIn(3, 20))
        }
        sb.append("&key=").append(key)
        val url = sb.toString()
        cache.get(url)?.let { return@withContext MapFetch(it) }
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // Static Maps errors are short plain text and name the actual problem.
                    val body = resp.body?.string().orEmpty().replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ").trim().take(180)
                    val hint = if (resp.code == 403)
                        " — is Maps Static API enabled on this key?" else ""
                    return@use MapFetch(null, (body.ifBlank { "HTTP ${resp.code}" }) + hint)
                }
                val bmp = BitmapFactory.decodeStream(resp.body?.byteStream())
                if (bmp != null) {
                    cache.put(url, bmp)
                    MapFetch(bmp)
                } else MapFetch(null, "Maps sent back a broken image")
            }
        }.getOrElse { MapFetch(null, it.message ?: "Network error") }
    }
}
