package com.gios.brightway

import android.content.Intent
import android.net.Uri

/**
 * Somewhere to go, arriving from another app.
 *
 * The whole of this app's intent surface, and it is deliberately one function: whatever shape the
 * request comes in, what BrightWay can do with it is put words in the search box. There is no
 * "navigate to these coordinates" path because there is no shape of request this app trusts
 * enough to start walking somebody on — see the manifest for that argument.
 *
 * Three shapes are understood:
 *
 *  - `brightway://go?q=Regal+Union+Square` — ours, used by BrightNotebook when a calendar entry
 *    has a location on it.
 *  - `geo:0,0?q=350+5th+Ave` — the standard one. The `0,0` prefix is the convention for "no
 *    coordinates, here are words instead", and every maps app on Android accepts it.
 *  - `geo:40.748,-73.985` — coordinates, which are handed to the same search. The Places API
 *    resolves a lat/lon pair to the thing that is there, which is more useful on screen than the
 *    numbers were and keeps one path through the app instead of two.
 *
 * Anything else answers null, and null means the app opens as it always does. An intent that
 * cannot be read is not an error worth a screen.
 */
object Handoff {

    fun queryFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return when (uri.scheme?.lowercase()) {
            SCHEME_BRIGHTWAY -> uri.getQueryParameter(PARAM_QUERY)?.trim()?.takeIf { it.isNotEmpty() }
            SCHEME_GEO -> fromGeo(uri)
            else -> null
        }
    }

    /**
     * A `geo:` URI as words.
     *
     * `Uri.getQueryParameter` cannot be used here: `geo:` is an opaque URI, so the query is part of
     * the scheme-specific part and Android's parser will not split it. Hence the string work, and
     * hence the manual decode — a location with a comma in it arrives percent-encoded, and an
     * address that reads `350 5th Ave%2C New York` on screen is worse than one that does not
     * resolve at all, because it looks like the app mangled it.
     */
    private fun fromGeo(uri: Uri): String? {
        val body = uri.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return null
        val query = body.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("$PARAM_QUERY=") }
            ?.removePrefix("$PARAM_QUERY=")
        if (!query.isNullOrBlank()) {
            return runCatching { Uri.decode(query.replace('+', ' ')) }.getOrNull()?.trim()
        }
        // No `q`, so the coordinates are the request. Dropped only when they are the placeholder
        // pair, which means "I have nothing to tell you" rather than "the Gulf of Guinea".
        val coords = body.substringBefore('?').trim()
        if (coords.isBlank() || coords == PLACEHOLDER) return null
        return coords
    }

    private const val SCHEME_BRIGHTWAY = "brightway"
    private const val SCHEME_GEO = "geo"
    private const val PARAM_QUERY = "q"
    private const val PLACEHOLDER = "0,0"
}
