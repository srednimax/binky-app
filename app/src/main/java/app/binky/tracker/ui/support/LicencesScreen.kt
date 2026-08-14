package app.binky.tracker.ui.support

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.GroupedCardItem
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.MessageCard
import app.binky.tracker.ui.common.SectionHeader
import kotlinx.coroutines.launch

/**
 * Open-source licences — every dependency in *this* binary, under the licence it ships beneath.
 *
 * **The list is generated, and that is the feature.** Apache-2.0 §4 travels with each of ~200
 * resolved artifacts, not with the 13 lines of `libs.versions.toml`, so a remembered list is wrong
 * one dependency bump later and nobody notices. `app.cash.licensee` resolves the runtime classpath at
 * build time and fails the build on a licence nobody has allowed; this screen only draws what it
 * wrote (Phase 7.5 §3).
 *
 * **Licence names and licence text are not translated, and that is deliberate rather than an
 * omission.** A licence *is* its English text — a translated Apache-2.0 is not Apache-2.0 — so what
 * the nine languages get is this screen's title, its opening sentence and the two row labels.
 *
 * **A `LazyColumn`, because 200 rows is what this screen is.** [GroupedCardItem] is the same idiom
 * the weight history uses: each row carries the card's surface itself, so the group still reads as
 * one card without composing every row that is off screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    onOpenLicenceText: (spdxId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LicencesViewModel =
        viewModel(factory = LicencesViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.support_no_browser)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licences_title)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding =
                PaddingValues(
                    start = Spacing.base,
                    end = Spacing.base,
                    top = Spacing.tight,
                    bottom = Spacing.section,
                ),
        ) {
            item {
                Text(
                    text = stringResource(R.string.licences_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Only after the load has finished: an empty list mid-parse is not an empty list, and
            // saying so for a frame would be ADR-0001's silence read as an answer.
            if (!state.loading && state.groups.isEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.base))
                    MessageCard(text = stringResource(R.string.licences_unavailable))
                }
            }

            state.groups.forEach { group ->
                licenceSection(
                    group = group,
                    hasBundledText = group.spdxId != null && group.spdxId in state.bundledTexts,
                    onReadText = { onOpenLicenceText(group.spdxId.orEmpty(), group.title) },
                    onOpenUrl = { url ->
                        if (!context.openUrl(url)) {
                            scope.launch { snackbarHostState.showSnackbar(noBrowser) }
                        }
                    },
                )
            }
        }
    }
}

/**
 * One licence and everything shipped beneath it.
 *
 * Kotlin note: an extension on `LazyListScope` rather than a `@Composable` — a lazy list's children
 * are declared, not composed, so a section that wants to be several independent items has to be a
 * plain function that calls `item` several times. Wrapping it in a composable would make the whole
 * group one item again, which is the thing [GroupedCardItem] exists to avoid.
 *
 * The licence's own row comes **first** in the card rather than last: it is the obligation, and the
 * artifacts under it are the list it applies to.
 */
private fun LazyListScope.licenceSection(
    group: LicenceGroup,
    hasBundledText: Boolean,
    onReadText: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    // The licence row exists when there is something behind it: the bundled text, or failing that
    // the terms' own page. A licence with neither gets its artifacts and no dead row.
    val licenceRow: (@Composable () -> Unit)? =
        when {
            hasBundledText -> {
                {
                    ListRow(
                        title = stringResource(R.string.licences_read),
                        onClick = onReadText,
                        trailing = { Chevron() },
                    )
                }
            }
            group.url != null -> {
                {
                    ListRow(
                        title = stringResource(R.string.licences_open_terms),
                        subtitle = group.url,
                        onClick = { onOpenUrl(group.url) },
                        trailing = { Chevron() },
                    )
                }
            }
            else -> null
        }

    val leadingRows = if (licenceRow == null) 0 else 1
    val rowCount = group.artifacts.size + leadingRows

    item(key = "header:${group.title}") {
        Spacer(Modifier.height(Spacing.section))
        SectionHeader(group.title)
        Spacer(Modifier.height(Spacing.tight))
    }

    if (licenceRow != null) {
        item(key = "licence:${group.title}") {
            GroupedCardItem(index = 0, count = rowCount) { licenceRow() }
        }
    }

    group.artifacts.forEachIndexed { index, artifact ->
        item(key = "${group.title}:${artifact.coordinates}") {
            GroupedCardItem(index = index + leadingRows, count = rowCount) {
                ListRow(
                    title = artifact.displayName,
                    // The coordinates rather than a prettier label: a POM's `<name>` is often just
                    // "Activity", and the thing a reader can act on is the artifact it names.
                    subtitle = artifact.coordinates,
                )
            }
        }
    }
}
