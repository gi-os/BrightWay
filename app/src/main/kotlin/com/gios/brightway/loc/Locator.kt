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
 *
 * One instance per process, and one subscription under it, by construction. The UI holds a
 * lease while it is up and [com.gios.brightway.nav.NavService] holds one for the length of a
 * trip; the underlying updates run while anybody holds any lease and stop when the last one
 * is released. Leases exist because the alternative — two callers each owning start/stop —
 * is two subscribers tearing each other down: the activity being destroyed used to be able
 * to switch off the GPS underneath whatever else still needed it. Leaving GPS running with
 * no lease holder is how LightFog earned its battery memory, so release is not optional.
 */
class Locator private constructor(context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _fix = MutableStateFlow<Location?>(null)
    val fix: StateFlow<Location?> = _fix

    private val listener = LocationListener { l -> _fix.value = l }
    private val leases = mutableSetOf<String>()

    /** Start the updates if this is the first lease; idempotent per owner. */
    @Synchronized
    fun acquire(owner: String) {
        val wasEmpty = leases.isEmpty()
        leases += owner
        if (wasEmpty) start()
    }

    /** Stop the updates when the last lease goes. Releasing what you don't hold is a no-op. */
    @Synchronized
    fun release(owner: String) {
        if (leases.remove(owner) && leases.isEmpty()) stop()
    }

    @SuppressLint("MissingPermission")
    private fun start() {
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

    private fun stop() = runCatching { lm.removeUpdates(listener) }.let { }

    companion object {
        @Volatile private var instance: Locator? = null

        fun get(context: Context): Locator =
            instance ?: synchronized(this) {
                instance ?: Locator(context.applicationContext).also { instance = it }
            }
    }
}
