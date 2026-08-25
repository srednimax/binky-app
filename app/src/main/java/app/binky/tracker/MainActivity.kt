package app.binky.tracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.binky.tracker.theme.BinkyTheme
import app.binky.tracker.ui.wipe.SchemaMismatchScreen
import app.binky.tracker.work.EXTRA_CARE_BUNNY_ID
import app.binky.tracker.work.EXTRA_DOSE_BUNNY_ID
import app.binky.tracker.work.EXTRA_DOSE_COURSE_ID
import app.binky.tracker.work.EXTRA_EVENT_BUNNY_ID
import app.binky.tracker.work.EXTRA_OPEN_BACKUP
import app.binky.tracker.work.EXTRA_WATCH_BUNNY_ID
import app.binky.tracker.work.ReminderTap
import kotlinx.coroutines.flow.MutableStateFlow

// AppCompatActivity rather than ComponentActivity, and for one reason only: it is where
// AppCompatDelegate lives, and AppCompatDelegate is what applies a per-app language on the
// pre-13 half of the supported range (ADR-0013). Nothing else here uses AppCompat — no views,
// no action bar, no AppCompat widgets — and AppCompatActivity is a ComponentActivity subclass,
// so setContent, the window itself and the activity-result APIs all still work unchanged.
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

        // Edge-to-edge, and **not** `enableEdgeToEdge()` — Play flagged that call against release
        // 386 and it was right: every path in androidx.activity 1.13.0, `EdgeToEdgeApi35` included,
        // reaches `Window.setStatusBarColor` and `setNavigationBarColor`, deprecated in Android 15.
        // There is no version of the call that avoids them, so what it did is split in two: this
        // line, which is all Compose actually depends on, and the bar colours, which moved to
        // `themes.xml` where an attribute is not a deprecated method. `values/colors.xml` carries
        // the reasoning and `BinkyTheme` writes the icon appearance at runtime.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // **Nobody pans this window; the content pads itself** (PLAN 4f).
        //
        // The manifest asks for `adjustResize`, and on API 26-29 that is the only thing that works:
        // `WindowInsets.ime` is not reported before API 30, so the older half of the supported range
        // depends on the window actually being resized. From API 30 the same request is inert —
        // the line above sets `decorFitsSystemWindows = false`, and the window manager
        // downgrades the resize to a *pan*. Panning is worse than doing nothing: with the keyboard
        // open on the observation form it slid the top of the form under the status bar and carried
        // the `TopAppBar`, Save button and all, off the top of the screen.
        //
        // So on API 30+ the system is told to do neither, and `Modifier.imePadding()` in
        // `Navigation.kt` handles the keyboard as the inset it now is. Set here rather than in the
        // manifest because the manifest cannot say "only on new enough Android", and the old
        // behaviour is still load-bearing below 30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        setContent {
            // Read from the application, never from `app.container` — that property is the `lazy`
            // that *is* ADR-0007's wipe guard, and the theme below wraps the schema-mismatch screen
            // as well as the app, so forcing it here would open the gate from inside the thing
            // standing in front of it.
            //
            // Kotlin note: a plain `Flow` has no current value the way a `StateFlow` does, so
            // collecting one as state needs an initial. `false` is also the stored default, which is
            // what keeps the first frame from being the wrong palette and then repainting.
            val materialYou by app.preferences.materialYou.collectAsStateWithLifecycle(initialValue = false)

            BinkyTheme(dynamicColor = materialYou) {
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
 * One extra per destination rather than one plus a convention, so an intent cannot ask for the Care
 * screen and the observation form at once — and the order below is the tie-break if a future one
 * ever carries two, decided here rather than in whichever branch happened to be written last.
 *
 * **The dose is read first**, because it is the only one whose lateness has consequences (ADR-0003).
 */
private fun Intent?.reminderTap(): ReminderTap? {
    val doseBunny = this?.getStringExtra(EXTRA_DOSE_BUNNY_ID)
    val doseCourse = this?.getStringExtra(EXTRA_DOSE_COURSE_ID)
    if (doseBunny != null && doseCourse != null) return ReminderTap.Medication(doseBunny, doseCourse)
    val care = this?.getStringExtra(EXTRA_CARE_BUNNY_ID)
    if (care != null) return ReminderTap.Care(care)
    val watch = this?.getStringExtra(EXTRA_WATCH_BUNNY_ID)
    if (watch != null) return ReminderTap.LogObservation(watch)
    // After the watch and before the backup, which is where the existing order puts it: these are
    // about an animal and the one below is about a file.
    val event = this?.getStringExtra(EXTRA_EVENT_BUNNY_ID)
    if (event != null) return ReminderTap.Event(event)
    // Last, and deliberately: the three above are about an animal and this one is about a file.
    if (this?.getBooleanExtra(EXTRA_OPEN_BACKUP, false) == true) return ReminderTap.OpenBackup
    return null
}
