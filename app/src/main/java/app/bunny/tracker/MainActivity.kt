package app.bunny.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bunny.tracker.theme.BunnyTrackerTheme
import app.bunny.tracker.ui.wipe.WipeConsentScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BunnyTrackerApplication

        enableEdgeToEdge()
        setContent {
            BunnyTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val pendingWipe by app.pendingWipe.collectAsStateWithLifecycle()

                    // Kotlin note: assigned to a local first because smart-casting a `var` read from
                    // another object is not allowed — the compiler cannot prove it has not changed
                    // between the null check and the use. A local `val` it can.
                    val wipe = pendingWipe
                    if (wipe != null) {
                        // ADR-0007's guard is structural: `MainNavigation` is what first reads
                        // `AppContainer`, so not composing it is what keeps Room out of existence.
                        WipeConsentScreen(pendingWipe = wipe, onContinue = app::consentToWipe)
                    } else {
                        MainNavigation()
                    }
                }
            }
        }
    }
}
