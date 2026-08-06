package app.binky.tracker.ui.support

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.BuildConfig
import app.binky.tracker.R
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.support_intro), style = MaterialTheme.typography.bodyMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mail(SupportRequest.BUG, bugSubject) }) {
                    Text(stringResource(R.string.support_bug_button))
                }
                // Said **before** the button is tapped, not after: this is the first outgoing data
                // in the app, and ADR-0001's rule against silence pointed at it.
                Text(
                    text = stringResource(R.string.support_diagnostics_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.support_diagnostics_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = { mail(SupportRequest.FEATURE, featureSubject) }) {
                Text(stringResource(R.string.support_feature_button))
            }

            // Rendered whether or not a mail app exists, because it *is* the fallback: a snackbar
            // saying "no mail app" is a dead end unless there is something on screen to copy.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.support_email_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(text = SUPPORT_EMAIL, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { if (!context.openPlayListing()) say(noStoreApp) }) {
                    Text(stringResource(R.string.support_rate_button))
                }
                Text(
                    text = stringResource(R.string.support_rate_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = { if (!context.openUrl(PRIVACY_POLICY_URL)) say(noBrowser) }) {
                Text(stringResource(R.string.support_privacy_button))
            }

            HorizontalDivider()

            // Here rather than in Settings: the version exists for the bug report above, and a
            // number kept away from the screen that needs it is how it goes stale unnoticed. In a
            // debug build the code is a git commit count, and is meant to be.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.support_version_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
