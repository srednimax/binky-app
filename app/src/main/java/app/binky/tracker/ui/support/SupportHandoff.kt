package app.binky.tracker.ui.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/*
 * **Support** — four hand-offs to other apps, and the two mail drafts that go with them.
 *
 * Named to echo `ui/care/CalendarHandoff.kt`, and for the same reason: both hand a draft to another
 * app and own nothing afterwards. The app itself transmits nothing here — the mail client does, and
 * only after the owner reads the draft and taps send. That is what keeps a support inbox on the right
 * side of *no backend, ever*.
 *
 * The split in this file is deliberate. The two builders below are **pure over strings** — they never
 * see a `Context` — so the subject and body can be asserted on the JVM with an English description and
 * a Polish one, in `src/test`, with no Android framework under them. The `Context` extensions at the
 * bottom are the framework half and are verified by hand on the phone (Phase 6c). Same division as
 * `careRrule` / `careCalendarBeginMillis` versus `addCareToCalendar`.
 */

/**
 * Which of the two mails, and the inbox filter token that tags it.
 *
 * **The tag is a Kotlin constant and not a string resource — a deliberate ADR-0013 exception.** It is
 * addressed to the maintainer, not to the sender: the inbox is read in one language whatever language
 * the report was written in. A localised tag would need one filter rule per locale, and the failure
 * when a new language ships without its rule is invisible — it looks exactly like nobody reporting
 * anything.
 *
 * The rule that actually works is `subject:bug`, **not** `subject:#bug`: Gmail's search index does not
 * recognise hash marks. So the token doing the work is the English *word*, which is precisely the part
 * a Polish description (`Zgłoszenie błędu`) does not contain. The `#` stays because it is glanceable in
 * an inbox list, and because Gmail matches whole tokens — so `subject:bug` cannot be tripped by the
 * `-debug` suffix below, which tokenises separately.
 *
 * (Kotlin note: an enum constant can carry data. `SupportRequest.BUG.tag` is `"#bug"` — closer to a
 * frozen object per member than to a bare string union.)
 */
enum class SupportRequest(
    val tag: String,
) {
    BUG("#bug"),
    FEATURE("#feature"),
}

/**
 * The six facts a bug report carries, read at the call site and passed in as data.
 *
 * That is what keeps the builders JVM-testable: every one of these comes from `BuildConfig`, `Build`
 * or the resolved configuration, none of which exists in a plain unit test. Reading them in the
 * composable and handing them down as a record moves the untestable part to one line.
 *
 * No bunny data, no identifier, no path — asserted by golden-string equality in the test rather than
 * by `contains`, because equality is the only assertion that proves nothing *else* is in there.
 */
data class SupportDiagnostics(
    /** `BuildConfig.VERSION_NAME`. */
    val versionName: String,
    /** `BuildConfig.VERSION_CODE`. In a debug build this is a git commit count, and is meant to be. */
    val versionCode: Int,
    /** `BuildConfig.DEBUG` — see [versionLabel]. */
    val isDebugBuild: Boolean,
    /** `Build.VERSION.RELEASE`, e.g. `"15"`. */
    val androidRelease: String,
    /** `Build.VERSION.SDK_INT`. */
    val apiLevel: Int,
    /** `"${Build.MANUFACTURER} ${Build.MODEL}"`. */
    val device: String,
    /**
     * The **resolved** app locale — `LocalResources.current.configuration.locales[0].toLanguageTag()`,
     * not `currentAppLanguage()`. That one returns null for "follow the phone", which is the ordinary
     * state and would put a blank here for most senders. What the report needs is the locale the
     * strings were actually drawn from.
     */
    val appLocale: String,
)

/**
 * `Binky 1.3.0 (214)`, or `Binky 1.3.0-debug (214)` from a debug build.
 *
 * **The `-debug` marker is the whole point.** `applicationIdSuffix = ".debug"` (ADR-0023) never reaches
 * `versionName`, so without this a report from the developer's own phone is byte-identical to a real
 * one — and the debug build's `versionCode` is a live git commit count, so it does not even collide
 * usefully.
 *
 * Subject and body both go through this, so the two can never disagree about which build sent them.
 */
private fun SupportDiagnostics.versionLabel(): String =
    "Binky $versionName${if (isDebugBuild) "-debug" else ""} ($versionCode)"

/**
 * `#bug — Bug report — Binky 1.3.0 (214)`, with [description] the only localised part.
 *
 * The split is the point: constant tag, localised description. A Polish sender sees
 * `#bug — Zgłoszenie błędu — Binky 1.3.0 (214)` and one filter rule still catches it.
 *
 * The subject travels in the `mailto:` query string, percent-encoded — see [supportMailtoUri], where
 * both that and the `#` it has to survive are explained.
 */
fun supportSubject(
    request: SupportRequest,
    description: String,
    diagnostics: SupportDiagnostics,
): String = "${request.tag} — $description — ${diagnostics.versionLabel()}"

/**
 * The mail body: the localised [prompt], a blank line, then the diagnostics block — or exactly `""`
 * for a feature request.
 *
 * **An idea does not need a build number**, and prefilled text is friction in the way of the sentence
 * the sender came to write. So a feature request gets the empty string, not a prompt with nothing
 * under it.
 *
 * **The block is not localised**, for the same reason the tag is not: every line in it is a number or
 * an identifier addressed to the same reader, and translating `Android 15 (API 35)` would be
 * translating a fact.
 *
 * **The separator is a blank line and must never become `-- `.** That is the RFC 3676 signature
 * delimiter, and Gmail collapses everything below it behind a `…`. A block written that way is
 * present, correct, and unread.
 */
fun supportBody(
    request: SupportRequest,
    prompt: String,
    diagnostics: SupportDiagnostics,
): String =
    when (request) {
        SupportRequest.FEATURE -> ""
        SupportRequest.BUG ->
            listOf(
                prompt,
                "",
                diagnostics.versionLabel(),
                "Android ${diagnostics.androidRelease} (API ${diagnostics.apiLevel})",
                diagnostics.device,
                "locale ${diagnostics.appLocale}",
            ).joinToString("\n")
    }

/** Confirmed live before anything named it — everything here hardcodes it. */
const val SUPPORT_EMAIL = "binky.support@gmail.com"

/**
 * The **release** `applicationId`, written out rather than derived.
 *
 * Never `packageName` and never `BuildConfig.APPLICATION_ID`: the debug build carries ADR-0023's
 * `.debug` suffix, so a derived link opens *item not found* on the developer's own phone — the one
 * device that will ever test it. A unit test asserts the built URLs do not end in `.debug`.
 */
const val PLAY_PACKAGE = "binky.bunny.and.rabbit.tracker"

/**
 * Play, pinned by package — see [openPlayListing].
 *
 * `market://` is not a Play-only scheme: on the test phone Xiaomi's GetApps (`com.xiaomi.mipicks`)
 * answers it first, and its catalogue does not contain Binky.
 */
const val PLAY_STORE_PACKAGE = "com.android.vending"

/** The same page the Play listing links, so the two cannot drift. */
const val PRIVACY_POLICY_URL = "https://srednimax.github.io/binky-app/privacy-policy.html"

/** The Play Store app's own scheme. */
fun playMarketUri(): String = "market://details?id=$PLAY_PACKAGE"

/** The browser fallback, and the link that works on a phone with no Play Store. */
fun playWebUrl(): String = "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE"

/**
 * `mailto:binky.support@gmail.com?subject=…&body=…`, with both values percent-encoded by [encode].
 *
 * **The subject and body have to travel in the query string, and this was the reverse of the original
 * design.** That version put them in `EXTRA_SUBJECT` / `EXTRA_TEXT` to sidestep the `#` fragment trap
 * described below — and **Gmail silently ignores both extras for `ACTION_SENDTO`**. Proved on the
 * device at 6c: the draft opened with the recipient filled and the subject and body **empty**, the
 * same result whether the chooser was involved or the intent was pinned straight at
 * `com.google.android.gm`. It is the worst shape of failure available here, because the screen looks
 * like it worked and only the arriving mail is wrong.
 *
 * The trap that argument was avoiding is real: `#` is the URI *fragment* delimiter, so a hand-written
 * `mailto:…?subject=#bug` parses `#bug` as the fragment and the subject arrives empty anyway. The
 * answer is not to hand-write `%23bug` — that is exactly the escaping a later edit unescapes without
 * knowing why — but to let the platform escape it. [encode] is `Uri.encode`, whose default safe set
 * leaves only the unreserved characters alone: `#` becomes `%23`, the em-dashes and Polish diacritics
 * become UTF-8 escapes, the body's newlines become `%0A`, and spaces become `%20` rather than `+`.
 *
 * [encode] is a parameter so this stays a pure function: `Uri` is framework and throws in a JVM test,
 * and the part worth testing is the **assembly** — that the two values land in the right query
 * parameters, and that `#` does not survive into the URI unescaped.
 */
fun supportMailtoUri(
    subject: String,
    body: String,
    encode: (String) -> String,
): String = "mailto:$SUPPORT_EMAIL?subject=${encode(subject)}&body=${encode(body)}"

/**
 * Opens a mail app with the recipient, subject and body filled. False if nothing can open it.
 *
 * **`ACTION_SENDTO` with a `mailto:` Uri, never `ACTION_SEND`.** `ACTION_SEND` with `text/plain` opens
 * the full share sheet — Drive, WhatsApp, Bluetooth — and a bug report that lands in a WhatsApp draft
 * never arrives. `SENDTO` + `mailto` resolves to a far narrower set, though not to mail apps only:
 * PayPal registers a deep link on the scheme on the test phone. The platform's own *just once /
 * always* dialog settles that, which is better than forcing `createChooser` and overriding a default
 * the owner set deliberately.
 *
 * **The extras are still set, and they are belt-and-braces rather than the mechanism.** The query
 * string is what Gmail reads (see [supportMailtoUri]); some other clients read the extras instead, and
 * a client that reads both gets the same two strings from either. Setting both costs two lines and
 * removes a class of "works on my mail app" from the report path.
 *
 * **No `resolveActivity` pre-check anywhere**, here or in the two below: package visibility on API 30+
 * makes it return null on a phone that has a mail app, so the pre-check would answer *no* everywhere.
 * The attempt is the only honest test; the caller reports the false.
 */
fun Context.sendSupportMail(
    subject: String,
    body: String,
): Boolean {
    val intent =
        Intent(Intent.ACTION_SENDTO)
            .setData(supportMailtoUri(subject, body) { Uri.encode(it) }.toUri())
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            // Launched from a screen, but the receiver is another app's task.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * Opens Binky's Play listing: the Store app first, the browser second, false if neither exists.
 *
 * **`setPackage` is what makes the fallback reachable.** Unpinned, `market://` resolves to GetApps on
 * this phone and `ActivityNotFoundException` never throws, because something always answers — so the
 * `https` branch would be dead code and 6c's Play-disabled step would prove nothing. Pinning makes the
 * intent resolve to Play or to nothing, and "or to nothing" is exactly what the catch is for.
 *
 * `setPackage` needs no `<queries>` entry to *launch*: visibility has never blocked `startActivity`.
 */
fun Context.openPlayListing(): Boolean {
    val store =
        Intent(Intent.ACTION_VIEW, playMarketUri().toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(store)
        true
    } catch (e: ActivityNotFoundException) {
        openUrl(playWebUrl())
    }
}

/** Opens a page in whatever the phone browses with. False if it browses with nothing. */
fun Context.openUrl(url: String): Boolean {
    val intent =
        Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
