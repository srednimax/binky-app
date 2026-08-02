package app.binky.tracker

import android.content.Intent
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
import app.binky.tracker.ui.wipe.SchemaMismatchScreen
import app.binky.tracker.work.EXTRA_CARE_BUNNY_ID
import app.binky.tracker.work.EXTRA_OPEN_BACKUP
import app.binky.tracker.work.EXTRA_WATCH_BUNNY_ID
import app.binky.tracker.work.ReminderTap
import kotlinx.coroutines.flow.MutableStateFlow

// AppCompatActivity rather than ComponentActivity, and for one reason only: it is where
// AppCompatDelegate lives, and AppCompatDelegate is what applies a per-app language on the
// pre-13 half of the supported range (ADR-0013). Nothing else here uses AppCompat — no views,
// no action bar, no AppCompat widgets — and AppCompatActivity is a ComponentActivity subclass,
// so setContent, enableEdgeToEdge and the activity-result APIs all still work unchanged.
class MainActivity : AppCompatActivity() {
    /**
     * What a tapped reminder notification asked for, waiting to be acted on — a care reminder's
     * bunny ([EXTRA_CARE_BUNNY_ID]) or a watch nag's ([EXTRA_WATCH_BUNNY_ID]).
     *
     * A `MutableStateFlow` rather than a plain read of `intent`, because the tap arrives by two
     * different routes: `onCreate` when the app was not running, and [onNewIntent] when it was, and
     * only a flow makes the second one reach a composition that is already on screen. Cleared once
     * the shell has acted, so a configuration change does not re-navigate the owner away from
     * wherever they went afterwards.
     */
    private val notificationTarget = MutableStateFlow<ReminderTap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BinkyApplication
        notificationTarget.value = intent.reminderTap()

        enableEdgeToEdge()
        setContent {
            BinkyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val schemaMismatch by app.schemaMismatch.collectAsStateWithLifecycle()

                    // Kotlin note: assigned to a local first because smart-casting a `var` read from
                    // another object is not allowed — the compiler cannot prove it has not changed
                    // between the null check and the use. A local `val` it can.
                    val mismatch = schemaMismatch
                    if (mismatch != null) {
                        // ADR-0007's guard is structural: `MainNavigation` is what first reads
                        // `AppContainer`, so not composing it is what keeps Room out of existence.
                        SchemaMismatchScreen(mismatch = mismatch, onContinue = app::consentToWipe)
                    } else {
                        val tap by notificationTarget.collectAsStateWithLifecycle()
                        MainNavigation(
                            notificationTap = tap,
                            onNotificationHandled = { notificationTarget.value = null },
                        )
                    }
                }
            }
        }
    }

    /** The same tap, arriving while the app is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget.value = intent.reminderTap()
    }
}

/**
 * Which reminder, if any, this intent came from.
 *
 * Two extras rather than one plus a convention, so an intent cannot ask for the Care screen and the
 * observation form at once — and if a future one carries both, care wins here rather than in
 * whichever branch happened to be written last.
 */
private fun Intent?.reminderTap(): ReminderTap? {
    val care = this?.getStringExtra(EXTRA_CARE_BUNNY_ID)
    if (care != null) return ReminderTap.Care(care)
    val watch = this?.getStringExtra(EXTRA_WATCH_BUNNY_ID)
    if (watch != null) return ReminderTap.LogObservation(watch)
    // Last, and deliberately: the two above are about an animal and this one is about a file.
    if (this?.getBooleanExtra(EXTRA_OPEN_BACKUP, false) == true) return ReminderTap.OpenBackup
    return null
}
