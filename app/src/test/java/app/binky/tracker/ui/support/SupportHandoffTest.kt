package app.binky.tracker.ui.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

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

    /**
     * A faithful stand-in for `Uri.encode`, which is framework and throws in a JVM test.
     *
     * `URLEncoder` is HTML-form encoding, which differs from RFC 3986 in exactly one place that
     * matters here: it writes a space as `+`. Mail clients that take that literally put a `+` in the
     * subject, so it is corrected to `%20` — which is what `Uri.encode` emits.
     */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    @Test
    fun `the subject and body travel in the query string, where Gmail reads them`() {
        // The regression guard for 6c's finding: Gmail **silently ignores** EXTRA_SUBJECT and
        // EXTRA_TEXT for ACTION_SENDTO, so an implementation that relies on the extras opens a draft
        // with the recipient filled and both other fields empty. Nothing on the screen says so.
        val uri = supportMailtoUri("Subject here", "Body here", ::encode)
        assertEquals("mailto:binky.support@gmail.com?subject=Subject%20here&body=Body%20here", uri)
    }

    @Test
    fun `the tag's hash is escaped rather than parsed as a fragment`() {
        // `#` is the URI fragment delimiter: unescaped, `?subject=#bug` makes `#bug` the fragment and
        // the subject arrives empty — the same broken draft as the extras bug, by a different route.
        val subject = supportSubject(SupportRequest.BUG, bugEn, release)
        val uri = supportMailtoUri(subject, "", ::encode)
        assertTrue(uri.contains("subject=%23bug"))
        assertFalse("a bare # would start a fragment and truncate the subject", uri.contains("#"))
    }

    @Test
    fun `a body's newlines and a Polish subject survive encoding`() {
        // The diagnostics block is six lines, and a line break is not a query-string character. A
        // Polish subject additionally carries diacritics and two em-dashes.
        val uri =
            supportMailtoUri(
                supportSubject(SupportRequest.BUG, bugPl, debug),
                supportBody(SupportRequest.BUG, promptEn, release),
                ::encode,
            )
        assertTrue("newlines must be escaped, not dropped", uri.contains("%0A"))
        assertTrue("ł must be UTF-8 percent-escaped", uri.contains("%C5%82"))
        // One `&` and one `?` only — anything else means a value leaked out of its parameter.
        assertEquals(1, uri.count { it == '?' })
        assertEquals(1, uri.count { it == '&' })
    }

    @Test
    fun `a feature request still produces a well-formed uri with an empty body`() {
        val uri = supportMailtoUri(supportSubject(SupportRequest.FEATURE, "Feature request", release), "", ::encode)
        assertTrue(uri.endsWith("&body="))
        assertTrue(uri.startsWith("mailto:binky.support@gmail.com?subject=%23feature"))
    }

    @Test
    fun `the inbox and the privacy policy are the ones the listing names`() {
        // Gmail ignores dots, so `binkysupport@` and `binky.support@` are one mailbox — this pins
        // the spelling the Play Console's per-app contact email is set to.
        assertEquals("binky.support@gmail.com", SUPPORT_EMAIL)
        assertEquals("https://srednimax.github.io/binky-app/privacy-policy.html", PRIVACY_POLICY_URL)
    }
}
