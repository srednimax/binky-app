package app.binky.tracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.binky.tracker.theme.BinkyTheme
import app.binky.tracker.ui.wipe.WipeConsentScreen

// AppCompatActivity rather than ComponentActivity, and for one reason only: it is where
// AppCompatDelegate lives, and AppCompatDelegate is what applies a per-app language on the
// pre-13 half of the supported range (ADR-0013). Nothing else here uses AppCompat — no views,
// no action bar, no AppCompat widgets — and AppCompatActivity is a ComponentActivity subclass,
// so setContent, enableEdgeToEdge and the activity-result APIs all still work unchanged.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BinkyApplication

        enableEdgeToEdge()
        setContent {
            BinkyTheme {
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
