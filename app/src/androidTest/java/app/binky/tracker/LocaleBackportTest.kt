package app.binky.tracker

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.binky.tracker.ui.settings.AppLanguage
import app.binky.tracker.ui.settings.currentAppLanguage
import app.binky.tracker.ui.settings.setAppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * ADR-0013's per-app language, asserted where the phone in the drawer cannot assert it.
 *
 * The switcher has two implementations behind one call. On Android 13+ the platform owns per-app
 * locales; below 13 **AppCompat's backport** owns them, applying the override by wrapping each
 * activity's base context and recreating the activity. Half the supported range — `minSdk` 26 up to
 * 32 — runs the backport, and the test device runs Android 16, so nothing on this desk has ever
 * executed the branch that half the range depends on. CI's API **26** leg is where these assertions
 * are load-bearing; on the 34 and 36 legs the same assertions hold with the platform doing the work,
 * which is worth having but is not what this file is for.
 *
 * Deliberately **not** gated on `SDK_INT < 33`: a test that only ever runs on one emulator leg is a
 * test nobody can reproduce by hand. The claim is the same on every leg — *setting an app locale
 * changes the configuration the app's own `getString` resolves against* — and only the machinery
 * underneath it differs.
 *
 * French is the probe rather than Polish because 1.0 ships English alone (ADR-0013 puts the
 * translation at 3i). That is a feature here: it separates "the configuration changed" from "we have
 * a second `values-` folder", and the fallback assertion below checks the case an owner of an
 * unshipped language actually gets.
 */
@RunWith(AndroidJUnit4::class)
class LocaleBackportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun clearTheOverride() {
        // The override persists — that is the whole point of it — so leaving it set would hand the
        // next test in the run a French app. Cleared on the main thread, like every other call into
        // AppCompatDelegate.
        instrumentation.runOnMainSync { setAppLanguage(null) }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun settingAnAppLocaleChangesTheConfigurationTheActivityResolvesStringsAgainst() {
        val deviceDefault = appContext().resources.configuration.locales[0]
        assumeTrue(
            "the probe locale has to differ from the device's own, or the assertion proves nothing",
            deviceDefault.language != "fr",
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            val before = resumedActivity()
            assertEquals(
                "with no override the activity should follow the phone",
                deviceDefault.language,
                before.resources.configuration.locales[0]
                    .language,
            )

            setApplicationLocales("fr")

            // Not `before` — applying a locale **recreates the activity**, on both implementations,
            // so the instance that answers now is a different object than the one launched above.
            val after = awaitActivityResolving("fr")
            assertEquals(
                "the app locale should reach the activity's own resources, not just a stored setting",
                "fr",
                after.resources.configuration.locales[0]
                    .language,
            )
        }
    }

    @Test
    fun theActivityResolvesAPlatformStringThroughTheOverriddenConfiguration() {
        val english = platformCancelIn(Locale.ENGLISH)
        val french = platformCancelIn(Locale.FRENCH)
        // A stripped system image ships one language of platform resources, and then this assertion
        // would compare "Cancel" with "Cancel" and pass while proving nothing. Skipped rather than
        // silently vacuous — the configuration assertion above still runs on such an image.
        assumeTrue("this system image has no French platform resources", english != french)

        ActivityScenario.launch(MainActivity::class.java).use {
            setApplicationLocales("fr")
            val activity = awaitActivityResolving("fr")

            // The end-to-end claim: `getString` on the activity — which is what every
            // `stringResource` in the Compose tree ultimately calls — goes through the override.
            assertEquals(
                "the activity should resolve strings against the app locale",
                french,
                activity.getString(android.R.string.cancel),
            )
            assertNotEquals(french, english)
        }
    }

    @Test
    fun anUnshippedLanguageFallsBackToEnglishRatherThanFailingToResolve() {
        ActivityScenario.launch(MainActivity::class.java).use {
            setApplicationLocales("fr")
            val activity = awaitActivityResolving("fr")

            // Binky has no `values-fr`, so its own strings fall back to the default folder. This is
            // the state ADR-0013 leaves a French owner in at 1.0, and it has to be plain English
            // rather than an empty string or a resource-not-found crash.
            assertEquals("Settings", activity.getString(R.string.settings_title))
            assertTrue(activity.getString(R.string.settings_language_help).isNotEmpty())
        }
    }

    @Test
    fun theSwitcherReadsBackWhatItWroteAndClearingItReturnsToFollowingThePhone() {
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync { setAppLanguage(AppLanguage.ENGLISH) }
            awaitActivityResolving("en")

            // `currentAppLanguage()` reads AppCompatDelegate rather than a preference of our own,
            // so this is the round trip the Settings row depends on (AppLanguage.kt).
            assertEquals(AppLanguage.ENGLISH, currentAppLanguage())

            instrumentation.runOnMainSync { setAppLanguage(null) }
            instrumentation.waitForIdleSync()

            // Null is "follow the phone", not "unset and broken" — the ordinary state for an owner
            // who never opens the switcher.
            assertNull("clearing the override should read back as following the phone", currentAppLanguage())
            assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        }
    }

    private fun appContext() = instrumentation.targetContext

    private fun setApplicationLocales(tag: String) {
        // AppCompatDelegate is main-thread only; below 13 this is also what triggers the recreate.
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /** The platform's own translation of a string every Android build ships, resolved by hand. */
    private fun platformCancelIn(locale: Locale): String {
        val context = appContext()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).getString(android.R.string.cancel)
    }

    /**
     * The resumed activity, asked of the instrumentation rather than of [ActivityScenario].
     *
     * `ActivityScenario.onActivity` tracks the instance it launched; a locale change destroys that
     * instance from underneath it. The lifecycle monitor just reports whatever is resumed now, which
     * is exactly the question being asked.
     */
    private fun resumedActivity(): Activity {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity =
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull()
        }
        return checkNotNull(activity) { "no activity was resumed" }
    }

    /**
     * Polls until a resumed activity reports [language], because the recreate is asynchronous.
     *
     * Kotlin note: this is the shape a JS test would write as `await waitFor(() => ...)` — there is
     * no promise to await here, so the wait is an explicit loop over the main thread's idle state.
     */
    private fun awaitActivityResolving(
        language: String,
        timeoutMs: Long = 10_000,
    ): Activity {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: String? = null
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val activity = runCatching { resumedActivity() }.getOrNull()
            if (activity != null) {
                last =
                    activity.resources.configuration.locales[0]
                        .language
                if (last == language) return activity
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError(
            "activity never resolved against '$language' (last seen '$last') " +
                "on API ${Build.VERSION.SDK_INT}",
        )
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
    }
}
