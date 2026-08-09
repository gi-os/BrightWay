package com.gios.brightway.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The map view is the Maps Static API: one GET, one finished PNG with the route already
 * drawn on it. No tiles, no projection math, no Maps SDK (which needs the Play Services
 * this phone doesn't have). The panel renders it greyscale anyway — the style params just
 * strip POI clutter that would dither into noise at LPIII contrast.
 */
class StaticMap(private val apiKey: () -> String) {
    private val http = OkHttpClient()

    // A wheel-zoom session revisits the same few URLs; ~8 x 640x1280 bitmaps ≈ 25 MB tops.
    private val cache = LruCache<String, Bitmap>(8)

    /**
     * @param zoom null = auto-fit the whole route; 12..19 = follow mode around [centerLat/Lon].
     */
    suspend fun fetch(
        encodedPolyline: String,
        centerLat: Double?, centerLon: Double?,
        destLat: Double, destLon: Double,
        zoom: Int?,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = apiKey().ifBlank { return@withContext null }
        val sb = StringBuilder("https://maps.googleapis.com/maps/api/staticmap")
        sb.append("?size=640x640&scale=2&maptype=roadmap")
        sb.append("&style=feature:poi%7Cvisibility:off")
        sb.append("&style=feature:transit%7Celement:labels%7Cvisibility:off")
        if (encodedPolyline.isNotBlank()) {
            sb.append("&path=weight:5%7Ccolor:0x000000ff%7Cenc:")
                .append(java.net.URLEncoder.encode(encodedPolyline, "UTF-8"))
        }
        sb.append("&markers=size:mid%7C").append(destLat).append(',').append(destLon)
        if (centerLat != null && centerLon != null) {
            sb.append("&markers=size:tiny%7C").append(centerLat).append(',').append(centerLon)
            if (zoom != null) {
                sb.append("&center=").append(centerLat).append(',').append(centerLon)
                sb.append("&zoom=").append(zoom.coerceIn(3, 20))
            }
        }
        sb.append("&key=").append(key)
        val url = sb.toString()
        cache.get(url)?.let { return@withContext it }
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bmp = BitmapFactory.decodeStream(resp.body?.byteStream())
                if (bmp != null) cache.put(url, bmp)
                bmp
            }
        }.getOrNull()
    }
}
