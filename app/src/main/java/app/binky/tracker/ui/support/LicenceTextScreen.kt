package app.binky.tracker.ui.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One licence, in full, from the copy that ships inside the APK.
 *
 * **This screen is the obligation itself.** Apache-2.0 §4(a) asks that a copy of the licence travel
 * with the work, and a link does not travel — a phone with no signal, or a URL that has moved, would
 * leave the app shipping 195 Apache-licensed artifacts and no licence. So the text is an asset under
 * `assets/licences/`, named by SPDX identifier, and this reads it back.
 *
 * **Rendered verbatim, hard line breaks and all.** The published text is wrapped at about 72 columns
 * and a phone is narrower than that, so paragraphs come out ragged. Re-flowing them would read better
 * and would be editing a legal document to do it; the ragged version is the licence and the tidy one
 * would not be.
 *
 * **No `ViewModel`, for [SupportScreen]'s reason rather than [LicencesViewModel]'s.** There is no
 * state here at all: one immutable file, read once, keyed by the licence. `produceState` is the whole
 * mechanism.
 *
 * [title] arrives on the navigation key rather than being looked up. It is the licence's own English
 * name — *Apache License 2.0* — which is fixed by the licence and cannot go stale behind us, and the
 * alternative is re-parsing the whole ~50 KB list to render one heading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenceTextScreen(
    spdxId: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val missing = stringResource(R.string.licences_text_missing)

    // Kotlin/Compose note: `produceState` is `useEffect` + `useState` in one — it runs the suspend
    // block when its keys change and exposes the result as state. The key is the licence, so opening
    // a different one re-reads rather than showing the previous text.
    val text by produceState(initialValue = "", spdxId, missing) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets
                        .open("$LICENCE_TEXT_DIRECTORY/$spdxId.txt")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrDefault(missing)
            }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
        ) {
            // Selectable, because the one thing anybody does with a licence page is quote from it.
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    // `onSurface`, not the muted variant every other body paragraph in the app
                    // takes: this is a document to be read rather than a caption under something
                    // else, and it is the only text on the screen.
                    color = MaterialTheme.colorScheme.onSurface,
                    // Left, never justified: the text already carries its own line breaks, and
                    // justifying pre-wrapped lines stretches the short ones into gap-toothed rows.
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
