package app.binky.tracker

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
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
 * French is the probe rather than Polish because 1.0 ships English alone (ADR-0013 puts the
 * translation at 3i). That separates "the configuration changed" from "we have a second `values-`
 * folder", and the fallback assertion below checks the case an owner of an unshipped language
 * actually gets.
 *
 * **The probe runs below 33 only**, and that is not tidiness — it is what the matrix found. `fr` is
 * not in `locales_config.xml`, and the two platform legs decline to apply an app locale the app does
 * not declare: on API 34 and 36 the activity stayed on `en` and never resolved against `fr` at all.
 * AppCompat's backport applies it regardless, and so, for what it is worth, does the HyperOS phone on
 * the desk — the same request, three different answers, none of which is this app's code. Above 33
 * the per-app locale is the platform's to honour and the only thing that is ours is the declaration,
 * so that is all this file asserts there.
 *
 * The practical consequence for **3i**: once Polish is declared, `pl` is a locale the app *does*
 * claim, and the same probe should hold on every leg. Worth re-running these without the gate then.
 */
@RunWith(AndroidJUnit4::class)
class LocaleBackportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Below 13 the backport applies the override; at 13+ the platform decides, and may decline. */
    private val backportLeg = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    private fun assumeTheProbeIsApplied() =
        assumeTrue(
            "above API 32 the platform declines an app locale outside locales_config.xml",
            backportLeg,
        )

    @After
    fun clearTheOverride() {
        // The override persists — that is the whole point of it — so leaving it set hands every test
        // that runs after this class a French app, and on a real phone leaves the app French until
        // someone notices.
        //
        // It has to be cleared **with an activity alive**, which is why this launches one rather than
        // just calling through. On API 33+ AppCompat forwards the call to the platform's
        // LocaleManager and reaches the Context to do that through a registered activity delegate;
        // with none registered the call is dropped and `getApplicationLocales()` still reads back
        // empty from AppCompat's own field. A bare `@After` therefore looks like it worked, asserts
        // clean, and leaves the device set — which is exactly how this was found.
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync { setAppLanguage(null) }
            awaitActivityResolving(deviceDefaultLanguage())
        }
    }

    @Test
    fun settingAnAppLocaleChangesTheConfigurationTheActivityResolvesStringsAgainst() {
        assumeTheProbeIsApplied()
        val deviceDefault = deviceDefaultLanguage()
        assumeTrue(
            "the probe locale has to differ from the device's own, or the assertion proves nothing",
            deviceDefault != "fr",
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            val before = resumedActivity()
            assertEquals(
                "with no override the activity should follow the phone",
                deviceDefault,
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
        assumeTheProbeIsApplied()
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
        assumeTheProbeIsApplied()
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

            // Asserted against the **configuration the activity ended up with**, not against
            // `getApplicationLocales()`. That getter answers from AppCompat's own field, so it
            // reports a cleared override whether or not the clear reached the device — see
            // `clearTheOverride` for the case where it does not.
            val cleared = awaitActivityResolving(deviceDefaultLanguage())
            assertEquals(
                "clearing the override should put the activity back on the phone's language",
                deviceDefaultLanguage(),
                cleared.resources.configuration.locales[0]
                    .language,
            )

            // Null is "follow the phone", not "unset and broken" — the ordinary state for an owner
            // who never opens the switcher.
            assertNull("clearing the override should read back as following the phone", currentAppLanguage())
        }
    }

    private fun appContext() = instrumentation.targetContext

    /**
     * The phone's own language, read from the **system** resources rather than the app's.
     *
     * On 13+ the platform applies a per-app locale to the app's whole configuration, so asking the
     * app context "what is the default" during an active override answers with the override.
     */
    private fun deviceDefaultLanguage(): String =
        Resources
            .getSystem()
            .configuration.locales[0]
            .language

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
