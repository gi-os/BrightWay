package com.gios.brightway.loc

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plain LocationManager — there is no Play Services on LightOS, so FusedLocationProvider
 * does not exist here. GPS with a network-provider warm start, exposed as a StateFlow.
 * Callers own start/stop from their lifecycle; leaving GPS running with the screen off is
 * how LightFog earned its battery memory.
 */
class Locator(context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _fix = MutableStateFlow<Location?>(null)
    val fix: StateFlow<Location?> = _fix

    private val listener = LocationListener { l -> _fix.value = l }

    @SuppressLint("MissingPermission")
    fun start() {
        runCatching {
            // Whatever is cached is better than a blank screen while GPS warms up.
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null && _fix.value == null) _fix.value = last
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5_000L, 10f, listener)
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 2f, listener)
            }
        }
    }

    fun stop() = runCatching { lm.removeUpdates(listener) }.let { }
}
