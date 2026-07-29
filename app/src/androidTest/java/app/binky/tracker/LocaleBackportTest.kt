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
 * are load-bearing, and after 3i it is the only leg that runs them — which is a change, and the
 * reason for it is the rest of this comment.
 *
 * **3i set out to remove a gate and ended up moving it.** The gate was there because the probe was
 * `fr`, `fr` is not in `locales_config.xml`, and the platform declines an app locale the app does
 * not declare, so 34 and 36 never resolved against it while the backport applied it regardless.
 * Declaring `pl` fixed exactly that, and 36 went green. Then it went red, on a run whose only
 * change was a comment — one test, the same ten-second wait, `last seen 'en'`. **So the platform
 * legs are not deterministic here**: 34 fails almost always, 36 intermittently, 26 never. Applying
 * a per-app locale on 13+ is a request to a system service that recreates the activity when it gets
 * to it, and "when it gets to it" is not a thing a test can wait on honestly.
 *
 * Two ways round it were tried and both are reverted. Waiting on a freshly launched activity as a
 * second stage did not help. Clearing the override in `@Before`, so no test could inherit one,
 * **turned 36 red on every probe test** — two locale writes in quick succession do not queue, and
 * the clear issued moments before a set can be the one that lands last.
 *
 * What is left is a sharper division than the one 3i planned:
 * - The **recreate-in-place** assertions run below 13, where AppCompat does the work synchronously
 *   enough to assert on. That is the branch no hardware here can reach and the reason this file
 *   exists; it was never the platform legs' job.
 * - What the app owes on **every** leg is that `values-pl` is in the APK and resolves — asserted
 *   directly, through a configuration context, in
 *   [theAppsOwnStringsResolveInPolishAndNotOnlyThePlatformsOwn]. 1.0 could not make that assertion
 *   at all, so the platform legs assert strictly more than they used to, just not via a wait.
 * - That the app *declares* `pl`, which is the only part of the 13+ path that is ours, is asserted
 *   off-device by `AppLanguageTest`.
 *
 * The fallback case an unshipped language lands in is asserted the same way, in
 * [anUnshippedLanguageFallsBackToEnglishRatherThanFailingToResolve] — through a configuration
 * context, because the platform will not hand out an undeclared app locale at all.
 */
@RunWith(AndroidJUnit4::class)
class LocaleBackportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * Restricts the two *recreate-in-place* assertions to the legs where the backport owns them.
     *
     * Not a workaround for a slow emulator — see the class comment. Below 13 AppCompat recreates the
     * activity itself, synchronously enough to assert on, and that branch is what this file exists
     * to cover. On 13+ the same wait is intermittent, and what the app owes there is asserted
     * without it.
     */
    private fun assumeTheBackportOwnsTheOverride() =
        assumeTrue(
            "on 13+ the platform applies the app locale on its own schedule, not on an assertable one",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
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
        assumeTheBackportOwnsTheOverride()
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

            // Not `before` — the backport applies a locale by **recreating the activity**, so the
            // instance that answers now is a different object than the one launched above.
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
        assumeTheBackportOwnsTheOverride()
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
        // Through a configuration context, and ungated, for the same reason the fallback test below
        // is: resource *resolution* is ours and is deterministic, while getting the platform to
        // apply an app locale to a running activity is neither. This is the assertion that fails if
        // `values-pl` is missing from the APK — the one 1.0 had no way to make at all.
        val polish = contextIn(Locale.forLanguageTag(PROBE))
        assertEquals("Ustawienia", polish.getString(R.string.settings_title))

        // The launcher label is deliberately NOT translated: it resolves against the system locale,
        // so a Polish app on an English phone would still have to say "Binky". Asserted rather than
        // trusted, because `values-pl` is exactly where someone would add it.
        assertEquals("Binky", polish.getString(R.string.app_name).removeSuffix(" Debug"))
    }

    @Test
    fun anUnshippedLanguageFallsBackToEnglishRatherThanFailingToResolve() {
        // Through a configuration context rather than an app locale: `fr` is not in
        // `locales_config.xml`, and 13+ declines to apply an app locale the app does not declare
        // (see the class comment). Resource *resolution* is not gated that way, so this is the same
        // fallback a French phone gets, asked directly and on every leg.
        val french = contextIn(Locale.FRENCH)

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

    /** The app's resources as [locale] would resolve them, with no app locale involved. */
    private fun contextIn(locale: Locale): android.content.Context {
        val context = appContext()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
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
