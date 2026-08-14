package app.binky.tracker.ui.support

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.binky.tracker.BuildConfig
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.FactRow
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SingleLineRowHeight
import kotlinx.coroutines.launch

/**
 * Support — the way to reach a person, rate the app, read the privacy policy, and see which build
 * this is. Reached from More, which is where the app's last "coming soon" used to be.
 *
 * **No `ViewModel`, and that is an exception with a reason.** The house rule is one per screen
 * because a screen normally owns a `Flow` from a DAO and state that has to survive rotation. This
 * one reads three compile-time constants and the resolved configuration; its only mutable state is a
 * `SnackbarHostState`, `remember`ed exactly as `CareReminderScreen` does it. A `ViewModel` here would
 * be an empty class whose `uiState` never changes — ceremony that makes the next reader go looking
 * for the data it implies.
 *
 * **Nothing here has a pre-check.** Every button is always live, and the failure is reported *after*
 * the attempt, because on API 30+ `resolveActivity` answers "no" on a phone that has the app —
 * see `SupportHandoff.kt`. The attempt is the only honest test.
 *
 * ## Phase 7, against `9c` / `9d`
 *
 * **Every string is the app's own**, including the address and the version in full, and the order is
 * unchanged. What the drawing fixes is *weight*:
 *
 * - **One filled button per screen, and it is the one that matters most.** *Report a bug* takes the
 *   full-width primary shape ([RecordButtonHeight]); *Request a feature* and *Rate Binky* are
 *   outlined and wrap their own labels. The before set already had this right by accident — `9c`
 *   makes it the app-wide rule.
 * - **The filled button keeps its helper directly beneath it.** "What a bug report carries" sits
 *   between the button and the explanation rather than after both, because this is the first
 *   outgoing data anywhere in Binky and naming that *before* the press is ADR-0001's rule against
 *   silence pointed at the one screen that sends something.
 * - **Privacy policy becomes a chevron row and pairs with Version in one card** — a link and a fact,
 *   both single-line, previously spread over 90dp each with a rule between them. It opens something
 *   so it gets a chevron; Version does not, so it does not. That is the row grammar, and this card
 *   is the first in the app to draw both halves of it side by side ([SingleLineRowHeight]).
 *
 * Phase 7.5 §3 adds a third row to that card — *Open-source licences* — and it needed no new
 * grammar, which is the argument for putting attribution here rather than on a screen of its own:
 * it is a link, it goes with the other link, and the card was already the shape for it.
 *
 * **Two of the drawing's measurements are declined**, both for the reason `2b`'s surface level was:
 * they have no home in the system and adopting them here alone would make this screen the odd one
 * out. Card interiors are drawn at 20px and built at [Spacing.base]; the outlined buttons are drawn
 * at 44dp and built at M3's 40dp default, which every other outlined button in the app already
 * takes. The weighting survives either way — the filled button is full-width and 52dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The **resolved** locale, not `currentAppLanguage()`: that one is null for "follow the phone",
    // which is the ordinary state and would put a blank in the block for most senders. What a report
    // needs is the locale the strings on screen were actually drawn from.
    val locales = LocalResources.current.configuration.locales
    val diagnostics =
        SupportDiagnostics(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            isDebugBuild = BuildConfig.DEBUG,
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            appLocale = locales[0].toLanguageTag(),
        )

    // Resolved up here because the launches happen in ordinary lambdas, where `stringResource` is
    // not callable — the same shape as `CareReminderScreen`'s `calendarMissing`.
    val bugSubject = stringResource(R.string.support_bug_subject)
    val bugPrompt = stringResource(R.string.support_bug_prompt)
    val featureSubject = stringResource(R.string.support_feature_subject)
    val noMailApp = stringResource(R.string.support_no_mail_app)
    val noStoreApp = stringResource(R.string.support_no_store_app)
    val noBrowser = stringResource(R.string.support_no_browser)

    fun say(message: String) = scope.launch { snackbarHostState.showSnackbar(message) }

    fun mail(
        request: SupportRequest,
        description: String,
    ) {
        val sent =
            context.sendSupportMail(
                subject = supportSubject(request, description, diagnostics),
                body = supportBody(request, bugPrompt, diagnostics),
            )
        if (!sent) say(noMailApp)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                // The shell's Scaffold has already padded past the status bar.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { insets ->
        // Explicit `Spacer`s rather than one `Arrangement.spacedBy`, because the gaps are not all
        // the same and `9c` is deliberate about which: the intro is a lead paragraph belonging to
        // the card under it (16dp), where the cards are sections and stand 24dp apart.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
        ) {
            Text(
                text = stringResource(R.string.support_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.base))

            ContactCard(
                onBug = { mail(SupportRequest.BUG, bugSubject) },
                onFeature = { mail(SupportRequest.FEATURE, featureSubject) },
            )
            Spacer(Modifier.height(Spacing.section))

            RateCard(onRate = { if (!context.openPlayListing()) say(noStoreApp) })
            Spacer(Modifier.height(Spacing.section))

            // Here rather than in Settings: the version exists for the bug report above, and a
            // number kept away from the screen that needs it is how it goes stale unnoticed. In a
            // debug build the code is a git commit count, and is meant to be.
            GroupedCard {
                ListRow(
                    title = stringResource(R.string.support_privacy_button),
                    onClick = { if (!context.openUrl(PRIVACY_POLICY_URL)) say(noBrowser) },
                    trailing = { Chevron() },
                )
                RowDivider()
                // Attribution's home, because Support is the app's only About-shaped screen and
                // this is the row grammar it already draws: it opens something, so it gets a
                // chevron (Phase 7.5 §3).
                ListRow(
                    title = stringResource(R.string.support_licences_button),
                    onClick = onOpenLicences,
                    trailing = { Chevron() },
                )
                RowDivider()
                FactRow(
                    label = stringResource(R.string.support_version_label),
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    // Lifted to the row above it: a fact row's own 48dp floor is for a *column* of
                    // facts, and next to a 56dp navigable row it reads as a rendering fault.
                    modifier = Modifier.heightIn(min = SingleLineRowHeight),
                )
            }
        }
    }
}

/**
 * Reaching a person: the two things worth writing, and the address to write to if neither button
 * finds a mail app.
 *
 * The divider is a plain [HorizontalDivider] rather than a [RowDivider] — the card has already inset
 * its contents, so a second inset on top would leave the line hanging away from the text either side
 * of it. `RowDivider`'s start padding is for a card whose rows draw edge to edge.
 */
@Composable
private fun ContactCard(
    onBug: () -> Unit,
    onFeature: () -> Unit,
) {
    GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
        // The one action this screen exists for, so it takes the full-width primary shape.
        Button(
            onClick = onBug,
            modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
            shape = RoundedCornerShape(RecordButtonRadius),
        ) {
            Text(stringResource(R.string.support_bug_button))
        }

        // Said **before** the button is tapped, not after: this is the first outgoing data in the
        // app, and ADR-0001's rule against silence pointed at it. The heading sits between the
        // button and the sentence so the pair reads as that button's own footnote.
        Spacer(Modifier.height(Spacing.snug))
        Text(
            text = stringResource(R.string.support_diagnostics_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(Spacing.hair))
        Text(
            text = stringResource(R.string.support_diagnostics_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.base))
        OutlinedButton(onClick = onFeature) {
            Text(stringResource(R.string.support_feature_button))
        }

        Spacer(Modifier.height(Spacing.base))
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.base))

        // Rendered whether or not a mail app exists, because it *is* the fallback: a snackbar
        // saying "no mail app" is a dead end unless there is something on screen to copy. In
        // `primary`, which is the one place this screen spends the colour — it marks the only text
        // on it the owner is meant to take away.
        Text(
            text = stringResource(R.string.support_email_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.hair))
        SelectionContainer {
            Text(
                text = SUPPORT_EMAIL,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The only thing a free, ad-free app asks for, and the sentence saying so. */
@Composable
private fun RateCard(onRate: () -> Unit) {
    GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
        OutlinedButton(onClick = onRate) {
            Text(stringResource(R.string.support_rate_button))
        }
        Spacer(Modifier.height(Spacing.tight))
        Text(
            text = stringResource(R.string.support_rate_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
