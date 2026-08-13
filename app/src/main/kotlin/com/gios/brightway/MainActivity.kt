package com.gios.brightway

import android.Manifest
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    @Composable
    private fun App(vm: WayViewModel) {
        val nav = rememberNavController()
        var tab by remember { mutableIntStateOf(0) }

        val askLocation = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> if (granted) vm.locator.start() }
        LaunchedEffect(Unit) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) vm.locator.start() else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        DisposableEffect(Unit) { onDispose { vm.locator.stop() } }

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

        // Errors surface as a one-line toast-equivalent; keep it plain.
        val error by vm.error.collectAsState()
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
