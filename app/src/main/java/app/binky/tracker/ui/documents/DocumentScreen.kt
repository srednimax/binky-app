package app.binky.tracker.ui.documents

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.InstantDatePickerDialog
import app.binky.tracker.ui.common.MessageCard
import app.binky.tracker.ui.weight.instantDateLabel
import java.time.Instant

/**
 * One document: its pages full-screen and zoomable, what it is, and where it came from.
 *
 * **The viewer and the detail screen are one screen, not two.** A document *is* its pages — a list
 * row that opens a metadata card which opens a viewer would put two taps between the owner and the
 * only thing they came for, which is reading the page.
 *
 * [readOnly] is the archived scope (ADR-0004): every write action is **absent** rather than present
 * and refusing.
 *
 * ## Phase 7, against `10b`
 *
 * Drawn with the **menu open**, because five of this route's six actions live in it — and that is
 * the whole layout argument. The page area stays **full-bleed on its own surface** rather than
 * moving into a card: a scan of a sheet is the content of the screen, and a rounded container round
 * it would only make it smaller. It is the one place in the sweep where "put it in a card" is the
 * wrong answer.
 *
 * *Delete document* takes `onSurfaceVariant` **inside the menu** — the same quieting every
 * destructive action gets when it shares a surface with ordinary ones (`1d`, `5a`, `4f`). It is not
 * red: red in this palette means a field is wrong, and spending it on a menu item that has not
 * happened yet would leave nothing to say that with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    documentId: String,
    readOnly: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenVisit: (bunnyId: String, visitId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DocumentViewModel =
        viewModel(
            key = "document-$documentId",
            factory = DocumentViewModel.factory(documentId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var renaming by rememberSaveable { mutableStateOf(false) }
    var datingPage by rememberSaveable { mutableStateOf(false) }
    var attaching by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var managingPages by rememberSaveable { mutableStateOf(false) }

    val addPages =
        rememberDocumentScan(scanner = rememberDocumentScanner()) { result ->
            viewModel.addPages(pages = result.pages, guided = result.guided)
        }

    ScanNoticeHost(
        notice = state.notice,
        onShown = viewModel::noticeShown,
        snackbarHostState = snackbarHostState,
    )

    // Deleted here, or with its bunny from another screen — either way there is nothing left to
    // show. Guarded on `loading` so the null a query has before its first emission cannot close it.
    val leave by rememberUpdatedState(onBack)
    LaunchedEffect(state.gone, state.loading) {
        if (state.gone && !state.loading) leave()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            actions = {
                if (!readOnly) {
                    IconButton(onClick = addPages, enabled = !state.saving) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.document_add_page),
                        )
                    }
                    DocumentMenu(
                        hasPages = state.pages.isNotEmpty(),
                        hasDate = state.capturedAt != null,
                        onRename = { renaming = true },
                        onSetDate = { datingPage = true },
                        onClearDate = { viewModel.setCapturedAt(null) },
                        onAttach = {
                            viewModel.loadVisitChoices()
                            attaching = true
                        },
                        onManagePages = { managingPages = true },
                        onDelete = { confirmingDelete = true },
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.loading) return@Column

        if (state.pages.isEmpty()) {
            EmptyDocument(readOnly = readOnly, modifier = Modifier.weight(1f))
        } else {
            PagePager(
                pages = state.pages,
                title = state.title,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        HorizontalDivider()
        DocumentFacts(
            capturedAt = state.capturedAt,
            createdAt = state.createdAt,
            visit = state.visit,
            onOpenVisit = { visitId -> state.bunnyId?.let { onOpenVisit(it, visitId) } },
        )
    }

    if (renaming) {
        TitleDialog(
            initial = state.title,
            onSave = {
                renaming = false
                viewModel.setTitle(it)
            },
            onDismiss = { renaming = false },
        )
    }

    if (datingPage) {
        InstantDatePickerDialog(
            initial = state.capturedAt,
            titleRes = R.string.document_date_label,
            onPicked = {
                datingPage = false
                viewModel.setCapturedAt(it)
            },
            onDismiss = { datingPage = false },
        )
    }

    if (attaching) {
        AttachVisitDialog(
            choices = state.visitChoices,
            attachedId = state.visit?.id,
            onPick = {
                attaching = false
                viewModel.attachToVisit(it)
            },
            onDismiss = { attaching = false },
        )
    }

    if (managingPages) {
        ManagePagesDialog(
            pages = state.pages,
            onMove = viewModel::movePage,
            onDelete = viewModel::deletePage,
            onDismiss = { managingPages = false },
        )
    }

    if (confirmingDelete) {
        BinkyDialog(
            title = stringResource(R.string.document_delete),
            onDismiss = { confirmingDelete = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            Text(stringResource(R.string.document_delete_body, state.title))
        }
    }
}

/** One "…" rather than five icons in an app bar that already carries two. */
@Composable
private fun DocumentMenu(
    hasPages: Boolean,
    hasDate: Boolean,
    onRename: () -> Unit,
    onSetDate: () -> Unit,
    onClearDate: () -> Unit,
    onAttach: () -> Unit,
    onManagePages: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.document_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_set_date)) },
                onClick = {
                    expanded = false
                    onSetDate()
                },
            )
            // Only when there is a date to clear. "I don't know what date is on this page" is a real
            // answer and has to be reachable again after a mistyped one (ADR-0001), but an always-on
            // *Clear date* is a control that does nothing most of the time it is read.
            if (hasDate) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.document_clear_date)) },
                    onClick = {
                        expanded = false
                        onClearDate()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_attach_visit)) },
                onClick = {
                    expanded = false
                    onAttach()
                },
            )
            if (hasPages) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.document_manage_pages)) },
                    onClick = {
                        expanded = false
                        onManagePages()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                // Last, and quieter than the five above it — `10b`'s rule for a destructive action
                // that has to share a surface with ordinary ones.
                colors =
                    MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun PagePager(
    pages: List<DocumentPageView>,
    title: String,
    modifier: Modifier = Modifier,
) {
    // The trailing lambda is the page *count*, re-read on recomposition, so deleting a page shortens
    // the pager rather than leaving a blank behind.
    val pagerState = rememberPagerState { pages.size }
    val missing = rememberVectorPainter(Icons.Filled.Info)

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            key = { pages[it].id },
            // `surfaceVariant` under the pages, edge to edge. It gives a page that does not fill the
            // frame — a portrait scan in a landscape gap, a short receipt — a surface to sit on
            // instead of floating on the background, and it makes the boundary between "the
            // document" and "what the app says about it" a change of ground rather than a rule.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) { index ->
            val page = pages[index]
            ZoomablePage(
                model = remember(page.file) { Uri.fromFile(page.file) },
                contentDescription = stringResource(R.string.document_page_description, index + 1, title),
                placeholder = missing,
            )
        }
        // Which page of how many. A pager with no counter makes a three-page document look like a
        // one-page one until somebody swipes by accident.
        if (pages.size > 1) {
            Text(
                text = stringResource(R.string.document_page_position, pagerState.currentPage + 1, pages.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base, vertical = Spacing.hair),
            )
        }
    }
}

/**
 * A document whose pages have all been deleted.
 *
 * It still exists on purpose (see `DocumentRepository.deletePage`): the title, the date and the
 * visit are records in their own right, and destroying them because a bad scan was removed would be
 * a delete nobody asked for. Both ways out are offered — add a page, or delete the document from
 * the menu.
 */
@Composable
private fun EmptyDocument(
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.base)) {
        MessageCard(
            title = if (readOnly) null else stringResource(R.string.document_no_pages),
            text =
                stringResource(
                    if (readOnly) R.string.document_no_pages else R.string.document_no_pages_help,
                ),
        )
    }
}

/**
 * The two dates and the visit.
 *
 * **"Dated" and "Scanned" are deliberately different words.** Only a date the owner read off the
 * page can be presented as the document's own; the scan date is when it arrived here, and calling
 * that "dated" would put this afternoon on a two-year-old vaccination card.
 */
@Composable
private fun DocumentFacts(
    capturedAt: Instant?,
    createdAt: Instant?,
    visit: VisitChoice?,
    onOpenVisit: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.snug),
        verticalArrangement = Arrangement.spacedBy(Spacing.hair),
    ) {
        Text(
            text =
                if (capturedAt != null) {
                    stringResource(R.string.document_dated_on, instantDateLabel(capturedAt))
                } else {
                    stringResource(R.string.document_no_date)
                },
            // The one line of the three the owner is meant to read first — `10b` draws it a step
            // above the two beneath it, which are the provenance rather than the fact.
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (capturedAt != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        createdAt?.let {
            Text(
                text = stringResource(R.string.document_scanned_on, instantDateLabel(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visit != null) {
            Text(
                text = stringResource(R.string.document_from_visit, dateLabel(visit.visitedOn), visit.reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpenVisit(visit.id) },
            )
        }
    }
}

@Composable
private fun TitleDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }

    BinkyDialog(
        title = stringResource(R.string.document_rename),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                // Blank is refused here rather than silently ignored by the repository: a document
                // with no title is a row nobody can find again.
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.document_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Which visit this paperwork came from.
 *
 * **Detaching leaves the document with its bunny** (ADR-0017) — "not from a visit" is the first row
 * rather than a separate clear button, so choosing it reads as an answer to the question the dialog
 * asked instead of as a destructive action.
 */
@Composable
private fun AttachVisitDialog(
    choices: List<VisitChoice>,
    attachedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    BinkyDialog(
        title = stringResource(R.string.document_attach_visit),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    ) {
        if (choices.isEmpty()) {
            Text(stringResource(R.string.document_attach_no_visits))
        } else {
            // A plain `Column`, not a `LazyColumn`: [BinkyDialog] already scrolls its own content,
            // and a lazy list nested in a scrolling parent gets an unbounded height to measure
            // against — it would try to compose every row and lose the only thing it is for. The
            // list is one bunny's visits, so it is tens of rows at the very most.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                DialogRow(
                    label = stringResource(R.string.document_attach_none),
                    selected = attachedId == null,
                    onClick = { onPick(null) },
                )
                choices.forEach { choice ->
                    DialogRow(
                        label = "${dateLabel(choice.visitedOn)} — ${choice.reason}",
                        selected = choice.id == attachedId,
                        onClick = { onPick(choice.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.snug),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Reordering and removing pages.
 *
 * A dialog rather than drag-and-drop on the pager: reordering is something a scanner's output
 * genuinely needs and something an owner does once per document, and up/down on a numbered list is
 * both reachable one-handed and possible to undo by eye.
 */
@Composable
private fun ManagePagesDialog(
    pages: List<DocumentPageView>,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BinkyDialog(
        title = stringResource(R.string.document_manage_pages),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    ) {
        // Plain rather than lazy for the same reason as [AttachVisitDialog]: a scanned document is
        // a handful of pages, and the dialog does the scrolling.
        Column {
            pages.forEachIndexed { index, page ->
                ManagePageRow(
                    number = index + 1,
                    canMoveUp = index > 0,
                    canMoveDown = index < pages.size - 1,
                    onUp = { onMove(page.id, -1) },
                    onDown = { onMove(page.id, 1) },
                    onDelete = { onDelete(page.id) },
                )
            }
        }
    }
}

@Composable
private fun ManagePageRow(
    number: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.hair),
    ) {
        Text(
            text = stringResource(R.string.document_page_number, number),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUp, enabled = canMoveUp) {
            Text(stringResource(R.string.document_page_up))
        }
        TextButton(onClick = onDown, enabled = canMoveDown) {
            Text(stringResource(R.string.document_page_down))
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
    }
}
