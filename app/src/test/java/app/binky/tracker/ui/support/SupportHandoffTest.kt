package app.binky.tracker.ui.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The support hand-off's pure halves.
 *
 * None of this is checkable by looking at the app. The subject is read by a Gmail filter rule, the
 * body is read in an inbox, and the store URL fails on exactly one device — the developer's own debug
 * build, whose `applicationId` ends in `.debug`. Every one of those is invisible from the screen that
 * produced it.
 *
 * The intent construction itself is framework — including `setPackage` on the `market://` one, which
 * no JVM test can observe — and is verified by hand on the phone at 6c.
 */
class SupportHandoffTest {
    /** A release-shaped build, on an English phone. */
    private val release =
        SupportDiagnostics(
            versionName = "1.3.0",
            versionCode = 214,
            isDebugBuild = false,
            androidRelease = "15",
            apiLevel = 35,
            device = "Xiaomi 2312DRA50G",
            appLocale = "en",
        )

    /** The same phone running the debug build, whose `versionCode` is a git commit count. */
    private val debug = release.copy(versionCode = 1487, isDebugBuild = true, appLocale = "pl")

    // The descriptions as they are drafted in phase-6.md's string table, resolved at the call site.
    private val bugEn = "Bug report"
    private val bugPl = "Zgłoszenie błędu"
    private val promptEn = "(describe what happened here)"

    @Test
    fun `a bug subject starts with the tag the filter looks for`() {
        // `startsWith`, not `contains`: the tag has to be the first thing in an inbox list.
        assertTrue(supportSubject(SupportRequest.BUG, bugEn, release).startsWith("#bug"))
        assertTrue(supportSubject(SupportRequest.FEATURE, "Feature request", release).startsWith("#feature"))
    }

    @Test
    fun `a Polish report still starts with the English tag`() {
        // The case a frozen whole-subject implementation passes and the filter rule fails on. The
        // token doing the work is `bug`, which is exactly what the Polish description does not contain.
        val subject = supportSubject(SupportRequest.BUG, bugPl, debug)
        assertTrue(subject.startsWith("#bug"))
        assertFalse(bugPl.contains("bug"))
    }

    @Test
    fun `the description after the tag does change with the locale`() {
        // The half that rots silently if someone "simplifies" the constant-plus-resource split into
        // one localised subject: the test above would still pass, and every Polish report would
        // stop matching the rule.
        assertEquals("#bug — Bug report — Binky 1.3.0 (214)", supportSubject(SupportRequest.BUG, bugEn, release))
        assertEquals("#bug — Zgłoszenie błędu — Binky 1.3.0 (214)", supportSubject(SupportRequest.BUG, bugPl, release))
    }

    @Test
    fun `a debug build says so and a release build does not`() {
        // ADR-0023's `.debug` suffix never reaches `versionName`, so without this marker a report
        // from the developer's own phone is byte-identical to a real one.
        assertEquals("#bug — Bug report — Binky 1.3.0-debug (1487)", supportSubject(SupportRequest.BUG, bugEn, debug))
        assertFalse(supportSubject(SupportRequest.BUG, bugEn, release).contains("-debug"))
    }

    @Test
    fun `a feature request body is exactly empty`() {
        // Not a prompt with nothing under it, and not the diagnostics block either: an idea does not
        // need a build number, and prefilled text is friction in the way of the sentence they came
        // to write.
        assertEquals("", supportBody(SupportRequest.FEATURE, promptEn, release))
    }

    @Test
    fun `the bug body is the prompt, a blank line, and six facts`() {
        // Golden-string **equality**, not `contains`. Equality is the only assertion that proves
        // nothing else is in there — no bunny name, no id, no file path.
        val expected =
            """
            (describe what happened here)

            Binky 1.3.0 (214)
            Android 15 (API 35)
            Xiaomi 2312DRA50G
            locale en
            """.trimIndent()
        assertEquals(expected, supportBody(SupportRequest.BUG, promptEn, release))
    }

    @Test
    fun `the bug body reports the app's locale and the build that sent it`() {
        val expected =
            """
            (opisz tutaj, co się stało)

            Binky 1.3.0-debug (1487)
            Android 15 (API 35)
            Xiaomi 2312DRA50G
            locale pl
            """.trimIndent()
        assertEquals(expected, supportBody(SupportRequest.BUG, "(opisz tutaj, co się stało)", debug))
    }

    @Test
    fun `the block is never separated by the signature delimiter`() {
        // `-- ` on its own line is RFC 3676's signature delimiter and Gmail collapses everything
        // below it behind a `…`. A block written that way is present, correct, and unread.
        assertFalse(supportBody(SupportRequest.BUG, promptEn, release).contains("\n-- "))
    }

    @Test
    fun `both store URLs name the release package and neither ends in debug`() {
        // The test that catches a `packageName`-derived implementation on the one phone that ever
        // runs it: the debug build's own id is `binky.bunny.and.rabbit.tracker.debug`, and a link
        // built from it opens *item not found*.
        assertEquals("market://details?id=binky.bunny.and.rabbit.tracker", playMarketUri())
        assertEquals("https://play.google.com/store/apps/details?id=binky.bunny.and.rabbit.tracker", playWebUrl())
        assertFalse(playMarketUri().endsWith(".debug"))
        assertFalse(playWebUrl().endsWith(".debug"))
        assertTrue(playWebUrl().startsWith("https://"))
    }

    @Test
    fun `the inbox and the privacy policy are the ones the listing names`() {
        // Gmail ignores dots, so `binkysupport@` and `binky.support@` are one mailbox — this pins
        // the spelling the Play Console's per-app contact email is set to.
        assertEquals("binky.support@gmail.com", SUPPORT_EMAIL)
        assertEquals("https://srednimax.github.io/binky-app/privacy-policy.html", PRIVACY_POLICY_URL)
    }
}
