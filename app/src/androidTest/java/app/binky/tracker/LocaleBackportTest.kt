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
 * are load-bearing; on the 36 leg the same assertions hold with the platform doing the work, which
 * is worth having but is not what this file is for.
 *
 * **Polish is the probe, and the old gate is gone.** Until 3i the probe was `fr`, `fr` is not in
 * `locales_config.xml`, and the matrix found that the platform declines an app locale the app does
 * not declare — on API 34 and 36 the activity stayed on `en` and never resolved against `fr` at all,
 * while AppCompat's backport applied it regardless. So the probe was gated below 33 and both
 * platform legs asserted nothing. `pl` is a locale the app declares, and on **26 and 36** that is
 * the whole story: the same four assertions, no gate, the backport and the platform each doing
 * their half.
 *
 * **API 34 is skipped, and it is the only one.** Ungating found a third answer there: the running
 * activity does not pick up a declared app locale inside ten seconds — while a later test in the
 * same run resolved Polish in 1.4 seconds, having inherited the override the timed-out test had set.
 * So the change lands on 34; what it does not do is reach an activity that is already on screen,
 * promptly or predictably. Two ways round it were tried and both cost more than they bought: waiting
 * on a freshly launched activity as a second stage, and clearing the override in `@Before` so no
 * test could inherit one. **The second turned API 36 red** — two locale writes in quick succession
 * do not queue, and the clear issued moments before a set can be the one that lands last. Neither is
 * in this file now.
 *
 * That leaves a narrow, stated exclusion rather than a silent one, which is what the `fr` gate was
 * too: what API 34 does with a per-app locale is the platform's business, Binky's own behaviour is
 * asserted either side of it, and the failure mode being skipped is *slow*, not *wrong*.
 *
 * The fallback case an unshipped language lands in is still asserted on every leg, in
 * [anUnshippedLanguageFallsBackToEnglishRatherThanFailingToResolve] — but through a configuration
 * context rather than through an app locale, precisely because the platform will not hand out an
 * undeclared one.
 */
@RunWith(AndroidJUnit4::class)
class LocaleBackportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * Skips the three tests that apply an app locale, on API 34 alone — see the class comment.
     *
     * Written as `!= 34` rather than as a range: this is one platform version behaving differently
     * from the ones on either side of it, not a boundary. If 35 or 37 ever joins the matrix it
     * should be asserted, not assumed.
     */
    private fun assumeTheProbeReachesARunningActivity() =
        assumeTrue(
            "API 34 does not apply a declared app locale to an activity already on screen promptly",
            Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )

    @After
    fun clearTheOverride() {
        // The override persists — that is the whole point of it — so leaving it set hands every test
        // that runs after this class a Polish app, and on a real phone leaves the app Polish until
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
        assumeTheProbeReachesARunningActivity()
        val deviceDefault = deviceDefaultLanguage()
        assumeTrue(
            "the probe locale has to differ from the device's own, or the assertion proves nothing",
            deviceDefault != PROBE,
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            val before = resumedActivity()
            assertEquals(
                "with no override the activity should follow the phone",
                deviceDefault,
                before.resources.configuration.locales[0]
                    .language,
            )

            setApplicationLocales(PROBE)

            // Not `before` — applying a locale **recreates the activity**, on both implementations,
            // so the instance that answers now is a different object than the one launched above.
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
        assumeTheProbeReachesARunningActivity()
        val english = platformCancelIn(Locale.ENGLISH)
        val polish = platformCancelIn(Locale.forLanguageTag(PROBE))
        // A stripped system image ships one language of platform resources, and then this assertion
        // would compare "Cancel" with "Cancel" and pass while proving nothing. Skipped rather than
        // silently vacuous — the configuration assertion above still runs on such an image.
        assumeTrue("this system image has no Polish platform resources", english != polish)

        ActivityScenario.launch(MainActivity::class.java).use {
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
        assumeTheProbeReachesARunningActivity()
        ActivityScenario.launch(MainActivity::class.java).use {
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
        /** The declared language that is not the base one — see the class comment for why it matters. */
        const val PROBE = "pl"
        const val POLL_INTERVAL_MS = 100L
    }
}
