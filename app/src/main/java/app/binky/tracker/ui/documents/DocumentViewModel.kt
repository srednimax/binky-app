package app.binky.tracker.ui.documents

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.DocumentRepository
import app.binky.tracker.data.VisitDetails
import app.binky.tracker.data.VisitRepository
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * One page, ready to render: the file it lives in and where it sits in the order.
 *
 * [file] may point at nothing — a restore may legitimately lack images (ADR-0005) — and the viewer
 * draws a placeholder for that rather than crashing (house rule).
 */
data class DocumentPageView(
    val id: String,
    val file: File,
)

/** One visit the attach picker may offer, flattened to what a row needs. */
data class VisitChoice(
    val id: String,
    val visitedOn: LocalDate,
    val reason: String,
)

data class DocumentUiState(
    val loading: Boolean = true,
    val title: String = "",
    /** The date printed on the page, when the owner has said what it is. */
    val capturedAt: Instant? = null,
    /** When it was scanned. Always known, and never presented as the document's own date. */
    val createdAt: Instant? = null,
    val pages: List<DocumentPageView> = emptyList(),
    /**
     * Whose paperwork this is. Read off the row rather than carried on the navigation key, because
     * the row is the one copy that cannot be wrong — and under "All bunnies" the shell's selection
     * names nobody, which is exactly when the visit link would otherwise go nowhere.
     */
    val bunnyId: String? = null,
    val visit: VisitChoice? = null,
    /** The bunny's visits, loaded when the picker opens rather than kept warm. */
    val visitChoices: List<VisitChoice> = emptyList(),
    /** True while page images are being written. */
    val saving: Boolean = false,
    val notice: ScanNotice? = null,
    /** The document is gone — deleted here, or with its bunny. The screen leaves. */
    val gone: Boolean = false,
)

/**
 * One document: its pages, what it is, and which visit it came from.
 *
 * Kotlin note: `flatMapLatest` swaps to a **new inner Flow** whenever the outer one emits and
 * cancels the previous — so when the document's `visitId` changes, the visit lookup behind it is
 * replaced rather than both staying subscribed. Closest JS analogue is `switchMap`.
 */
class DocumentViewModel(
    private val documentId: String,
    private val documents: DocumentRepository,
    private val visits: VisitRepository,
    private val media: MediaFiles,
) : ViewModel() {
    private data class LocalState(
        val visitChoices: List<VisitChoice> = emptyList(),
        val saving: Boolean = false,
        val notice: ScanNotice? = null,
    )

    private val local = MutableStateFlow(LocalState())

    private val document = documents.document(documentId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val attachedVisit: Flow<VisitChoice?> =
        document
            .map { it?.visitId }
            // Only re-subscribe when the id itself changes — retitling the document would otherwise
            // tear down and rebuild the visit query for no reason.
            .distinctUntilChanged()
            .flatMapLatest { visitId ->
                if (visitId == null) flowOf(null) else visits.visit(visitId).map { it?.toChoice() }
            }

    val uiState: StateFlow<DocumentUiState> =
        combine(
            document,
            documents.pages(documentId),
            attachedVisit,
            local,
        ) { row, pages, visit, local ->
            DocumentUiState(
                loading = false,
                title = row?.title.orEmpty(),
                capturedAt = row?.capturedAt,
                createdAt = row?.createdAt,
                pages = pages.map { DocumentPageView(id = it.id, file = media.resolve(it.path)) },
                bunnyId = row?.bunnyId,
                visit = visit,
                visitChoices = local.visitChoices,
                saving = local.saving,
                notice = local.notice,
                // Only after the first emission has actually arrived, so the screen is not closed by
                // the null a query has before it comes back.
                gone = row == null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentUiState())

    fun setTitle(title: String) {
        viewModelScope.launch { documents.setTitle(documentId, title) }
    }

    /** Null clears it — "I do not know what date is on this page" is a real answer (ADR-0001). */
    fun setCapturedAt(capturedAt: Instant?) {
        viewModelScope.launch { documents.setCapturedAt(documentId, capturedAt) }
    }

    /** Fills the picker. A one-shot read: the list is only looked at while the dialog is open. */
    fun loadVisitChoices() {
        viewModelScope.launch {
            val bunnyId = documents.documentNow(documentId)?.bunnyId ?: return@launch
            val choices = visits.visits(bunnyId).first().map { it.toChoice() }
            local.update { it.copy(visitChoices = choices) }
        }
    }

    /** Detaching passes null, and the document stays with its bunny (ADR-0017). */
    fun attachToVisit(visitId: String?) {
        viewModelScope.launch { documents.attachToVisit(documentId, visitId) }
    }

    fun addPages(
        pages: List<Uri>,
        guided: Boolean,
    ) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(saving = true, notice = null) }
            try {
                documents.addPages(documentId, pages)
                local.update {
                    it.copy(saving = false, notice = if (guided) null else ScanNotice.FellBackToCamera)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                Log.w(SCAN_LOG_TAG_PAGES, "Could not add ${pages.size} page(s) to $documentId", failed)
                local.update { it.copy(saving = false, notice = ScanNotice.Failed) }
            }
        }
    }

    fun movePage(
        pageId: String,
        direction: Int,
    ) {
        viewModelScope.launch { documents.movePage(pageId, direction) }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch { documents.deletePage(pageId) }
    }

    fun delete() {
        viewModelScope.launch { documents.delete(documentId) }
    }

    fun noticeShown() {
        local.update { it.copy(notice = null) }
    }

    companion object {
        fun factory(documentId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    DocumentViewModel(
                        documentId = documentId,
                        documents = app.container.documentRepository,
                        visits = app.container.visitRepository,
                        media = app.container.mediaFiles,
                    )
                }
            }
    }
}

private const val SCAN_LOG_TAG_PAGES = "BinkyDocumentPages"

/** Room's `@Embedded` projection flattened to what a picker row needs; see [VisitDetails]. */
private fun VisitDetails.toChoice() = VisitChoice(id = visit.id, visitedOn = visit.visitedOn, reason = visit.reason)
