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
 * **Polish is the probe, and these assertions run on every leg.** Until 3i they could not: the probe
 * was `fr`, `fr` is not in `locales_config.xml`, and the matrix found that the platform declines an
 * app locale the app does not declare — on API 34 and 36 the activity stayed on `en` and never
 * resolved against `fr` at all, while AppCompat's backport applied it regardless. Three answers to
 * the same request, none of them this app's code, so the probe was gated below 33 and the two
 * platform legs asserted nothing. `pl` is a locale the app now declares, which removes the gate
 * rather than working around it.
 *
 * The fallback case an unshipped language lands in is still asserted, in
 * [anUnshippedLanguageFallsBackToEnglishRatherThanFailingToResolve] — but through a configuration
 * context rather than through an app locale, precisely because the platform will not hand out an
 * undeclared one.
 *
 * **Removing the gate then found a third answer, at API 34.** The declaration was enough for 36 and
 * the backport was never in doubt at 26, but on 34 the running activity did not pick the locale up
 * within the ten seconds this file used to allow — twice — while a later test in the same run
 * resolved Polish in 1.4 seconds, having inherited the override the timed-out test had set. So the
 * change lands on 34; what varies is whether it reaches an activity that is already on screen, and
 * how fast. [awaitActivityResolving] therefore waits in two stages, the second on an activity it
 * launches itself, which is the weaker claim but the true one and the only one Binky owes anyone.
 *
 * **And the obvious fix for that inherited override made it worse, which is the more useful
 * finding.** Clearing the override in `@Before` — so no test can start on one — took API 34 *and*
 * 36 from a slow apply to no apply at all inside twenty-five seconds. Two locale writes in quick
 * succession do not queue: the clear issued moments before the set can be the one that lands last.
 * Nothing in this file writes a locale to arrange a starting state any more. It asserts the
 * starting state instead, via [assertStartsOnTheDeviceDefault], and lets the single write per test
 * be the only one in flight.
 */
@RunWith(AndroidJUnit4::class)
class LocaleBackportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Held so [awaitActivityResolving]'s relaunch can be closed; see the comment there. */
    private var relaunched: ActivityScenario<MainActivity>? = null

    /**
     * The starting state each probe test needs, asserted rather than imposed.
     *
     * The obvious way to stop a test inheriting an override from the one before it is to clear the
     * override in `@Before`. **That was tried and it broke the two legs that worked**: a clear
     * issued moments before a set can be the one that wins, and API 34 and 36 both went from a
     * ten-second wait to no locale at all inside twenty-five. Two writes in quick succession do not
     * queue. So the setup here writes nothing — it reads, and fails loudly if what it reads is not
     * the state the test needs.
     */
    private fun assertStartsOnTheDeviceDefault(activity: Activity) =
        assertEquals(
            "the test should start with no override — one has leaked from an earlier test",
            deviceDefaultLanguage(),
            activity.resources.configuration.locales[0]
                .language,
        )

    @After
    fun clearTheOverride() {
        // The override persists — that is the whole point of it — so leaving it set hands every test
        // that runs after this class a Polish app, and on a real phone leaves the app Polish until
        // someone notices.
        resetToFollowThePhone()
        // Closed here rather than left to the next test, so only one activity is ever resumed when
        // a test starts polling for one.
        relaunched?.close()
        relaunched = null
    }

    private fun resetToFollowThePhone() {
        // It has to be cleared **with an activity alive**, which is why this launches one rather than
        // just calling through. On API 33+ AppCompat forwards the call to the platform's
        // LocaleManager and reaches the Context to do that through a registered activity delegate;
        // with none registered the call is dropped and `getApplicationLocales()` still reads back
        // empty from AppCompat's own field. A bare teardown therefore looks like it worked, asserts
        // clean, and leaves the device set — which is exactly how this was found.
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync { setAppLanguage(null) }
            awaitActivityResolving(deviceDefaultLanguage())
        }
    }

    @Test
    fun settingAnAppLocaleChangesTheConfigurationTheActivityResolvesStringsAgainst() {
        val deviceDefault = deviceDefaultLanguage()
        assumeTrue(
            "the probe locale has to differ from the device's own, or the assertion proves nothing",
            deviceDefault != PROBE,
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            val before = resumedActivity()
            assertStartsOnTheDeviceDefault(before)

            setApplicationLocales(PROBE)

            // Not `before` — applying a locale recreates the activity wherever it is applied in
            // place, so the instance that answers now is a different object than the one launched
            // above. On API 34 it may be a fresh one the wait had to launch itself; either way it
            // is not `before`.
            val after = awaitActivityResolving(PROBE)
            assertEquals(
                "the app locale should reach the activity's own resources, not just a stored setting",
                PROBE,
                after.resources.configuration.locales[0]
                    .language,
            )
        }
    }

    @Test
    fun theActivityResolvesAPlatformStringThroughTheOverriddenConfiguration() {
        val english = platformCancelIn(Locale.ENGLISH)
        val polish = platformCancelIn(Locale.forLanguageTag(PROBE))
        // A stripped system image ships one language of platform resources, and then this assertion
        // would compare "Cancel" with "Cancel" and pass while proving nothing. Skipped rather than
        // silently vacuous — the configuration assertion above still runs on such an image.
        assumeTrue("this system image has no Polish platform resources", english != polish)

        ActivityScenario.launch(MainActivity::class.java).use {
            assertStartsOnTheDeviceDefault(resumedActivity())
            setApplicationLocales(PROBE)
            val activity = awaitActivityResolving(PROBE)

            // The end-to-end claim: `getString` on the activity — which is what every
            // `stringResource` in the Compose tree ultimately calls — goes through the override.
            assertEquals(
                "the activity should resolve strings against the app locale",
                polish,
                activity.getString(android.R.string.cancel),
            )
            assertNotEquals(polish, english)
        }
    }

    @Test
    fun theAppsOwnStringsResolveInPolishAndNotOnlyThePlatformsOwn() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Without this the test can pass on an override left behind by an earlier one, having
            // applied nothing itself. That is not hypothetical: API 34 produced exactly such a pass,
            // in 1.4 seconds, on the run that first ungated this file.
            assertStartsOnTheDeviceDefault(resumedActivity())
            setApplicationLocales(PROBE)
            val activity = awaitActivityResolving(PROBE)

            // The platform assertion above would pass with no `values-pl` at all — it only proves
            // the configuration reached Android's own resources. This is the one that proves the
            // translation is in the APK and is what the switcher actually delivers (3i).
            assertEquals("Ustawienia", activity.getString(R.string.settings_title))

            // The launcher label is deliberately NOT translated: it resolves against the system
            // locale, so a Polish app on an English phone would still have to say "Binky". Asserted
            // rather than trusted, because `values-pl` is exactly where someone would add it.
            assertEquals("Binky", activity.getString(R.string.app_name).removeSuffix(" Debug"))
        }
    }

    @Test
    fun anUnshippedLanguageFallsBackToEnglishRatherThanFailingToResolve() {
        // Through a configuration context rather than an app locale: `fr` is not in
        // `locales_config.xml`, and 13+ declines to apply an app locale the app does not declare
        // (see the class comment). Resource *resolution* is not gated that way, so this is the same
        // fallback a French phone gets, asked directly and on every leg.
        val context = appContext()
        val french =
            Configuration(context.resources.configuration)
                .apply { setLocale(Locale.FRENCH) }
                .let { context.createConfigurationContext(it) }

        // Binky has no `values-fr`, so its own strings fall back to the default folder — plain
        // English, rather than an empty string or a resource-not-found crash.
        assertEquals("Settings", french.getString(R.string.settings_title))
        assertTrue(french.getString(R.string.settings_language_help).isNotEmpty())
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
     * Waits until an activity reports [language], in two stages, because "the activity is recreated
     * in place" turned out not to be true everywhere.
     *
     * The **first** stage is the one this file was written around: applying an app locale recreates
     * the running activity, and polling the resumed one eventually sees the new configuration. That
     * is what the backport does below 13 and what API 36 does above it.
     *
     * API 34 does not reliably do it inside ten seconds. What it does do — proven by a test that
     * passed in 1.4s on the same run, having inherited an override set by the test before it — is
     * end up applying it. Whether that is latency in AppCompat's hand-off to `LocaleManager` or the
     * platform declining to recreate a foreground activity and applying on next launch is not
     * something this file can tell apart from CI, and the difference does not change what Binky
     * owes anyone: a **launched** activity resolves its strings against the app locale. So the
     * **second** stage launches one and asks again. If the first stage answers, nothing else runs.
     *
     * Kotlin note: this is the shape a JS test would write as `await waitFor(() => ...)` — there is
     * no promise to await here, so the wait is an explicit loop over the main thread's idle state.
     */
    private fun awaitActivityResolving(language: String): Activity {
        pollForActivityResolving(language, IN_PLACE_TIMEOUT_MS)?.let { return it }

        // Not `.use { }`: the activity has to outlive this call for the caller to assert on it, so
        // the scenario is closed in teardown instead.
        relaunched?.close()
        relaunched = ActivityScenario.launch(MainActivity::class.java)
        pollForActivityResolving(language, AFTER_RELAUNCH_TIMEOUT_MS)?.let { return it }

        throw AssertionError(
            "no activity resolved against '$language' (last seen '$lastResolved') on API " +
                "${Build.VERSION.SDK_INT}, including one launched fresh after the change",
        )
    }

    private var lastResolved: String? = null

    private fun pollForActivityResolving(
        language: String,
        timeoutMs: Long,
    ): Activity? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val activity = runCatching { resumedActivity() }.getOrNull()
            if (activity != null) {
                lastResolved =
                    activity.resources.configuration.locales[0]
                        .language
                if (lastResolved == language) return activity
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    private companion object {
        /** The declared language that is not the base one — see the class comment for why it matters. */
        const val PROBE = "pl"
        const val POLL_INTERVAL_MS = 100L

        /** How long the running activity is given to be recreated under the new locale. */
        const val IN_PLACE_TIMEOUT_MS = 10_000L

        /** And how long a freshly launched one is, once the first stage has given up. */
        const val AFTER_RELAUNCH_TIMEOUT_MS = 15_000L
    }
}
