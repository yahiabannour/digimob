package de.mhh.digimob

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.feature.menu.presentation.MainMenuRoute
import de.mhh.digimob.feature.menu.presentation.MenuGraphRoute
import de.mhh.digimob.feature.menu.presentation.menuGraph
import de.mhh.digimob.feature.training.presentation.RecordingTestRoute
import de.mhh.digimob.feature.training.presentation.TrainingRoute
import de.mhh.digimob.feature.training.presentation.trainingGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            DigiMobTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = MenuGraphRoute) {
                    menuGraph(
                        navController = navController,
                        // "Aufnahmen testen" nur in der Entwickler-Version (Desktop: --entwickler)
                        showRecordingTest = BuildConfig.DEBUG,
                        onStartTraining = { ids, target ->
                            navController.navigate(TrainingRoute(exerciseIds = ids.map { it.key }, target = target))
                        },
                        onRecordingTest = { navController.navigate(RecordingTestRoute) },
                        onQuit = { finish() }
                    )
                    trainingGraph(onBackToMenu = { navController.popBackStack(MainMenuRoute, inclusive = false) })
                }
            }
        }
    }

    // Nach einem Systemdialog (Kamera-Erlaubnis) kommen die Leisten zurueck: wieder ausblenden
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Vollbild wie die Kiosk-App: Leisten nur nach Wischen vom Rand. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
