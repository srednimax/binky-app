package app.binky.tracker

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.binky.tracker.ui.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The plural form Android actually picks at a real count, in every language Binky ships.
 *
 * `TranslationTest` proves each locale *declares* the categories CLDR gives it — that Polish
 * carries `one`, `few`, `many` and `other`, and that none is missing. What no JVM test can see is
 * which item comes back when the app asks for five days: that selection belongs to the platform,
 * driven by CLDR's rules rather than by anything in this repo. A form filed under the wrong
 * category is a *complete* translation file that reads wrong on the phone, and it is invisible to
 * every other check here. Hence a device.
 *
 * **The four counts are chosen to separate the categories, not to be typical:**
 * - **1** is `one` in all nine.
 * - **2** is `few` in Polish, Czech and Ukrainian, and `other` in the one/other languages.
 * - **5** is `many` in Polish and Ukrainian — but **`other` in Czech**, whose `many` is for
 *   fractional counts alone. "Czech has a `many` form" is true of the file and false of every whole
 *   number, which makes `5 dne` the single most likely misfiling in this set; the assertion below
 *   says `5 dní`.
 * - **22** returns to `few` in Polish and Ukrainian. That is the one that separates a correct
 *   modulo rule from a range check that stopped at 4, and it is why the count is not 12 — `12` is
 *   `many` in both, being one of the teens the rule carves back out.
 *
 * Read `gap_days` rather than a longer string because it is four words in every language, so a
 * failure prints as a diff anyone can see rather than as two paragraphs to compare by eye.
 */
@RunWith(AndroidJUnit4::class)
class PluralSelectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Hand-checked against CLDR, once, and then owned by this file — deriving it from the same
     * `strings.xml` the app reads would only assert that a file equals itself.
     */
    private val expected =
        mapOf(
            "en" to mapOf(1 to "1 day", 2 to "2 days", 5 to "5 days", 22 to "22 days"),
            "pl" to mapOf(1 to "1 dzień", 2 to "2 dni", 5 to "5 dni", 22 to "22 dni"),
            "cs" to mapOf(1 to "1 den", 2 to "2 dny", 5 to "5 dní", 22 to "22 dní"),
            "uk" to mapOf(1 to "1 день", 2 to "2 дні", 5 to "5 днів", 22 to "22 дні"),
            "de" to mapOf(1 to "1 Tag", 2 to "2 Tage", 5 to "5 Tage", 22 to "22 Tage"),
            "es" to mapOf(1 to "1 día", 2 to "2 días", 5 to "5 días", 22 to "22 días"),
            "fr" to mapOf(1 to "1 jour", 2 to "2 jours", 5 to "5 jours", 22 to "22 jours"),
            "it" to mapOf(1 to "1 giorno", 2 to "2 giorni", 5 to "5 giorni", 22 to "22 giorni"),
            "pt-BR" to mapOf(1 to "1 dia", 2 to "2 dias", 5 to "5 dias", 22 to "22 dias"),
        )

    @Test
    fun everyShippedLanguagePicksTheFormCldrSaysAtOneTwoFiveAndTwentyTwo() {
        for ((tag, counts) in expected) {
            val resources = resourcesIn(tag)
            for ((count, form) in counts) {
                assertEquals(
                    "$tag renders $count of gap_days wrong — a form is filed under the wrong " +
                        "plural category, or the locale did not resolve at all",
                    form,
                    resources.getQuantityString(R.plurals.gap_days, count, count),
                )
            }
        }
    }

    /**
     * A tenth language must arrive here too. Everywhere else in this repo the locale list is *read*
     * so that adding one is a single line of XML; this one table cannot be, since its whole content
     * is the answer, so the next best thing is to fail loudly when it falls behind.
     */
    @Test
    fun theTableCoversExactlyTheShippedLanguages() {
        assertEquals(
            AppLanguage.entries.map { it.tag }.toSet(),
            expected.keys,
        )
    }

    /** The app's resources as [tag] resolves them, with no app-locale override involved. */
    private fun resourcesIn(tag: String) =
        context
            .createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(tag))
                },
            ).resources
}
