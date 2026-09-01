package com.gios.brightway

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gios.brightway.nav.NavSession
import com.gios.brightway.ui.CompassScreen
import com.gios.brightway.ui.HomeScreen
import com.gios.brightway.ui.NavScreen
import com.gios.brightway.ui.PlaceScreen
import com.gios.brightway.ui.RoutesScreen
import com.gios.brightway.ui.SettingsScreen
import com.gios.brightway.ui.TabBar
import com.gios.brightway.ui.WayViewModel
import com.gios.brightway.ui.theme.BrightWayTheme
import com.gios.light.common.hw.LightKey
import com.gios.light.common.hw.LightKeys
import com.gios.light.common.hw.LocalWheelBus
import com.gios.light.common.hw.WheelBus
import com.gios.light.common.report.LightReport
import com.gios.light.common.report.ReportOverlay
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    /** Wheel notches on their way to whichever screen is up. */
    private val wheel = WheelBus()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (LightKeys.of(event)) {
            LightKey.WheelUp -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(1)
                return true
            }
            LightKey.WheelDown -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(-1)
                return true
            }
            else -> Unit
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * A place handed over by another app, waiting for the view model to exist.
     *
     * `onCreate` runs before the composition that creates the view model, and `onNewIntent` can
     * arrive while this app is already up — so the request is parked here and picked up by an
     * effect inside the composition. Held rather than applied twice: a search fired again on every
     * recomposition would spend a Places call each time.
     */
    private var handoff by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handoff = Handoff.queryFrom(intent)
        LightReport.install(
            context = this,
            appName = "BrightWay",
            label = "way",
            token = BuildConfig.REPORT_TOKEN,
        )
        setContent {
            BrightWayTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: WayViewModel = viewModel()
                    App(vm)
                    ReportOverlay()
                }
            }
        }
    }

    /**
     * `singleTask`, so a second handover arrives here rather than on a new instance. Without this
     * every calendar entry tapped would leave another copy of the app on the back stack.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Handoff.queryFrom(intent)?.let { handoff = it }
    }

    @Composable
    private fun App(vm: WayViewModel) {
        val nav = rememberNavController()
        var tab by remember { mutableIntStateOf(0) }

        val askLocation = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> if (granted) vm.locator.acquire("ui") }
        LaunchedEffect(Unit) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) vm.locator.acquire("ui") else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        DisposableEffect(Unit) { onDispose { vm.locator.release("ui") } }

        // A trip that outlived its UI. NavService keeps navigating with the screen off, and if
        // the system reclaimed this activity in the meantime the view model came back empty —
        // so the session, which the service kept, refills it and the app reopens on the trip
        // rather than on a search box that has forgotten where you were going.
        LaunchedEffect(Unit) {
            val session = NavSession.state.value
            if (session != null && vm.chosen.value == null) {
                vm.destination.value = session.destination
                vm.chosen.value = session.route
                nav.navigate("nav")
            }
        }

        val scanQr = rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { vm.setApiKey(it) }
        }
        val launchScan = {
            scanQr.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan the key QR from gi-os.github.io/BrightWay")
                setBeepEnabled(false)
                setOrientationLocked(true)
            })
        }

        // Somewhere handed over by another app: put it in the box and search it. Searched rather
        // than routed on purpose — a calendar's location is a string somebody typed, and walking a
        // stranger somewhere on the strength of it is worse than one extra press. See [Handoff].
        val pending = handoff
        LaunchedEffect(pending) {
            val words = pending ?: return@LaunchedEffect
            handoff = null
            tab = 0
            vm.query.value = words
            vm.search()
        }

        // Errors surface as a one-line toast-equivalent; keep it plain.
        LaunchedEffect(Unit) {
            vm.error.collectLatest { msg ->
                if (msg != null) {
                    android.widget.Toast.makeText(
                        this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    vm.clearError()
                }
            }
        }

        CompositionLocalProvider(LocalWheelBus provides wheel) {
            NavHost(nav, startDestination = "main") {
                composable("main") {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> HomeScreen(
                                    vm,
                                    onRoutes = { nav.navigate("routes") },
                                    onPlace = { p ->
                                        vm.previewPlace.value = p
                                        nav.navigate("place")
                                    },
                                )
                                1 -> CompassScreen(vm)
                                else -> SettingsScreen(vm, onScanKey = launchScan)
                            }
                        }
                        TabBar(tab, listOf("GO", "COMPASS", "SETTINGS")) { tab = it }
                    }
                }
                composable("place") {
                    PlaceScreen(
                        vm,
                        onGo = {
                            vm.previewPlace.value?.let { p ->
                                vm.route(p) { nav.navigate("routes") }
                            }
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("routes") {
                    RoutesScreen(
                        vm,
                        onChosen = { nav.navigate("nav") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("nav") {
                    NavScreen(vm, onDone = { nav.popBackStack("main", false) })
                }
            }
        }
    }
}
