package com.gios.brightway.nav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.gios.brightway.MainActivity
import com.gios.brightway.R
import com.gios.brightway.data.Place
import com.gios.brightway.data.Trips
import com.gios.brightway.loc.Locator
import com.gios.brightway.net.RouteOption
import com.gios.brightway.share.NavProvider
import com.gios.brightway.util.ColorMode
import com.gios.brightway.util.Geo
import com.gios.brightway.util.NavMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Navigation that survives the screen turning off.
 *
 * The nav screen used to own the location loop, which meant the phone in a pocket stopped
 * navigating: on Android 10+ a backgrounded app simply stops receiving fixes. This service
 * is the fix — a foreground service with the `location` type keeps the app "in use" while
 * the trip runs, so the step keeps advancing between turns with the screen dark, and the
 * lock face (via [com.gios.brightway.share.NavProvider]) always has a current answer.
 *
 * ### Strictly route-scoped, because of LightFog
 *
 * A location service that outlives its purpose once bricked a phone in this collection, so
 * the boundaries here are hard ones. The service exists only between "route chosen" and
 * "arrived / END / back", stops itself the moment the last step's endpoint is reached, and
 * carries a four-hour cap after which it stops no matter what anyone forgot. It is
 * START_NOT_STICKY: the route lives in memory, so a system restart of a dead process would
 * resurrect a service with nothing to navigate — better it stays down.
 *
 * ### One owner of the location loop
 *
 * The service does not run its own GPS subscription next to the UI's — that is the
 * two-subscribers-tearing-each-other-down shape that keeps going wrong. [Locator] is a
 * process-wide singleton with named leases; the UI holds one while it is up, this service
 * holds one for the trip, and the single underlying LocationManager subscription runs while
 * anybody holds any. The activity dying releases its lease and nothing else.
 *
 * ### The notification is quiet on purpose
 *
 * A foreground service must carry a notification; this one is IMPORTANCE_LOW and static.
 * BrightControl's lock face forwards importance >= 3 and drops ongoing notifications, so
 * this never appears there — the lock face draws the trip from the provider instead, which
 * is the whole point. In the shade it is one silent line, "Navigating · tap to return",
 * whose tap lands back on the nav screen.
 */
class NavService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown()
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        // startForegroundService demands startForeground promptly, even on the path where
        // there turns out to be nothing to do — so the notification goes up first, always.
        ServiceCompat.startForeground(
            this, NOTE_ID, buildNote(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        // A start queued behind shutdown() lands on a corpse: the scope is already
        // cancelled, so the launches below would silently never run — running would read
        // true and the GPS lease would be held with nothing driving it and no cap timer
        // to ever let go. A finished instance only ever stops.
        if (finished) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val st = NavSession.state.value
        if (st == null) { shutdown(); return }
        // A degenerate route (origin on top of destination) parses to zero steps, and a
        // trip with no steps has no endpoint to arrive at — it would run to the cap.
        if (st.route.steps.isEmpty()) { shutdown(); return }
        // Whatever fix the locator already has beats "waiting for GPS" until the next one.
        seed(st)
        if (running) return
        running = true
        Locator.get(this).acquire(LEASE)
        scope.launch {
            Locator.get(this@NavService).fix.filterNotNull().collect { f ->
                // hasAccuracy() false means "no estimate at all", which is worse than a bad one.
                onFix(f.latitude, f.longitude, if (f.hasAccuracy()) f.accuracy else Float.MAX_VALUE)
            }
        }
        scope.launch {
            // The safety cap. Nothing legitimate about this app is a four-hour trip; a
            // service still alive then is a service somebody forgot, and LightFog is the
            // memory of what a forgotten location loop does to this phone.
            delay(HARD_CAP_MS)
            shutdown()
        }
    }

    private fun seed(st: NavState) {
        val f = Locator.get(this).fix.value ?: return
        val step = st.route.steps.getOrNull(st.stepIndex) ?: return
        val d = Geo.distanceM(f.latitude, f.longitude, step.endLat, step.endLon)
        NavSession.update { it.copy(distToNextM = d, updatedMs = System.currentTimeMillis()) }
        NavProvider.announce(this)
    }

    /** The same advance rule the screen used to run: under 20 m of the step's end, move on. */
    private fun onFix(lat: Double, lon: Double, accuracyM: Float) {
        // A coarse network fix can land "within 20 m" of an endpoint it is nowhere near;
        // no decision — advance or arrival — is worth making on one.
        if (accuracyM > MAX_FIX_ACCURACY_M) return
        val st = NavSession.state.value ?: return
        val step = st.route.steps.getOrNull(st.stepIndex) ?: return
        val d = Geo.distanceM(lat, lon, step.endLat, step.endLon)
        val next = NavMath.advanced(st.stepIndex, st.route.steps.lastIndex, d)
        if (next != st.stepIndex) {
            val target = st.route.steps[next]
            val nd = Geo.distanceM(lat, lon, target.endLat, target.endLon)
            // A step change resets the off-route run — this same distance seeds the new one.
            NavSession.driftFix(nd, stepChanged = true)
            NavSession.update {
                it.copy(
                    stepIndex = next, distToNextM = nd, offRoute = false,
                    updatedMs = System.currentTimeMillis(),
                )
            }
            // A short last step can be entered and finished by this same fix, and a phone
            // standing at the destination gets no more fixes (2 m of movement each) — so
            // arrival must be decided now, not deferred to a next fix that never comes.
            if (NavMath.arrived(next, st.route.steps.lastIndex, nd)) {
                shutdown()
                return
            }
        } else {
            // Same step, another accepted fix: feed the off-route rule. Only fixes past the
            // 40 m gate above get here, so the streak can't be built out of network slop.
            val off = NavSession.driftFix(d, stepChanged = false)
            NavSession.update {
                it.copy(distToNextM = d, offRoute = off, updatedMs = System.currentTimeMillis())
            }
            // Standing on the last step's endpoint is arrival, and arrival ends the service.
            if (NavMath.arrived(st.stepIndex, st.route.steps.lastIndex, d)) {
                shutdown()
                return
            }
        }
        NavProvider.announce(this)
    }

    /**
     * The one way out, whoever asks — END, arrival, the cap, or a start with nothing to do.
     *
     * Closes the journey-log trip here rather than in the UI, because the UI may be long
     * gone by the time a pocketed trip arrives. "Arrived" keeps its old meaning: the last
     * step had been reached when navigation ended, however it ended.
     */
    private fun shutdown() {
        if (!finished) {
            finished = true
            NavSession.state.value?.let { st ->
                val arrived =
                    st.route.steps.isNotEmpty() && st.stepIndex >= st.route.steps.lastIndex
                runCatching { Trips(this).finish(arrived, System.currentTimeMillis()) }
            }
            NavSession.end()
            NavProvider.announce(this)
            if (running) Locator.get(this).release(LEASE)
            running = false
            scope.cancel()
        }
        // State, not transition — the daltonizer lesson. Navigation just ended, however it
        // ended: arrival in a pocket, the cap, END from the screen. If nobody is navigating,
        // the phone should be grey, and the nav screen's own restore may never run — arrival
        // with the app backgrounded leaves that composition alive but its onDispose unfired
        // until the user comes back. When BrightControl holds the colour this write is a
        // harmless second opinion; when the direct-write fallback lifted it, this is the only
        // restore that runs. setColor swallows the missing grant.
        if (NavSession.state.value == null) ColorMode.setColor(this, false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // The system tearing the service down uninvited must not leave a ghost session
        // behind — a provider that says "navigating" with nothing driving it is a lie.
        if (!finished) {
            finished = true
            NavSession.end()
            runCatching { NavProvider.announce(this) }
            if (running) Locator.get(this).release(LEASE)
            running = false
            scope.cancel()
        }
        super.onDestroy()
    }

    private fun buildNote(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW),
        )
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Navigating · tap to return")
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL = "nav"
        private const val NOTE_ID = 41
        private const val LEASE = "nav"
        private const val ACTION_STOP = "com.gios.brightway.nav.STOP"

        /** Four hours. A hard cap, not a feature — see the class comment. */
        private const val HARD_CAP_MS = 4L * 60 * 60 * 1000

        /** Fixes vaguer than this decide nothing; twice the arrive radius, GPS is well under. */
        private const val MAX_FIX_ACCURACY_M = 40f

        /**
         * Called from the nav screen the moment it comes up, which is always in the
         * foreground — so startForegroundService never hits the background-start wall.
         * Re-entering the screen mid-trip finds the same route already in the session and
         * leaves it alone; only a genuinely new route resets the step to zero.
         */
        fun start(context: Context, route: RouteOption, destination: Place?) {
            val current = NavSession.state.value
            if (current == null || current.route !== route) {
                NavSession.begin(route, destination, System.currentTimeMillis())
            }
            context.startForegroundService(
                Intent(context, NavService::class.java),
            )
        }

        /** Deliberate end — the END row or the back gesture, from a live screen. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, NavService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
