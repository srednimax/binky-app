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
import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.DocumentRepository
import app.binky.tracker.data.DocumentSummary
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

/** Short enough for logcat's tag column, specific enough to filter on. */
private const val SCAN_LOG_TAG = "BinkyDocumentScan"

/**
 * One row in the document list.
 *
 * [thumbnail] is already resolved against `filesDir` — screens never see the relative path, which is
 * a storage detail (house rule) — and may point at nothing, or be null for a document whose pages
 * have all been deleted. Both render the placeholder rather than failing.
 *
 * [dated] is the date the list orders and labels by, and [hasDate] says which of the two it came
 * from, so the label can be honest: only a date the owner read off the page can be presented as the
 * document's date. Everything else is when it was scanned.
 */
data class DocumentRow(
    val id: String,
    val title: String,
    val pageCount: Int,
    val thumbnail: File?,
    val dated: Instant,
    val hasDate: Boolean,
)

/** What the screen has to say once, after a scan that took the fallback or failed outright. */
enum class ScanNotice { FellBackToCamera, Failed }

data class DocumentsUiState(
    val loading: Boolean = true,
    val bunnyName: String = "",
    val documents: List<DocumentRow> = emptyList(),
    /** True while page images are being written; the list stays live underneath it. */
    val saving: Boolean = false,
    /** Set when a scan has finished and the screen has not yet told the owner. */
    val notice: ScanNotice? = null,
    /** The document a finished scan produced, for the screen to open. Cleared once it has. */
    val opened: String? = null,
)

/**
 * A bunny's scanned paperwork.
 *
 * The list comes straight off the DAO's `Flow`, so a document appears the moment its rows land
 * (house rule: no hand-rolled refresh). Everything the ViewModel knows that the database does not —
 * the bunny's name, a save in flight, a notice owed — lives in [local] and is combined with it.
 */
class DocumentsViewModel(
    private val bunnyId: String,
    private val documents: DocumentRepository,
    private val bunnies: BunnyRepository,
    private val media: MediaFiles,
) : ViewModel() {
    private data class LocalState(
        val bunnyName: String = "",
        val saving: Boolean = false,
        val notice: ScanNotice? = null,
        val opened: String? = null,
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<DocumentsUiState> =
        combine(documents.documents(bunnyId), local) { rows, local ->
            DocumentsUiState(
                loading = false,
                bunnyName = local.bunnyName,
                documents = rows.map { it.toRow(media) },
                saving = local.saving,
                notice = local.notice,
                opened = local.opened,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

    init {
        viewModelScope.launch {
            val name = bunnies.bunnyNow(bunnyId)?.name.orEmpty()
            local.update { it.copy(bunnyName = name) }
        }
    }

    /**
     * Records a finished scan.
     *
     * [guided] false means ADR-0009's fallback engaged, which the owner is told **after** the fact
     * and once: the scan already worked, and what they lost is auto-crop and page detection rather
     * than the feature.
     */
    fun save(
        title: String,
        pages: List<Uri>,
        guided: Boolean,
    ) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(saving = true, notice = null) }
            try {
                val id = documents.add(bunnyId = bunnyId, title = title, pages = pages)
                local.update {
                    it.copy(
                        saving = false,
                        notice = if (guided) null else ScanNotice.FellBackToCamera,
                        opened = id,
                    )
                }
            } catch (cancelled: CancellationException) {
                // Kotlin note: leaving the screen cancels this coroutine by *throwing* here.
                // Catching Exception without rethrowing would turn "the owner left" into "that scan
                // could not be read", and `runCatching` swallows it silently.
                throw cancelled
            } catch (failed: Exception) {
                // Reported to the owner, logged for us: an unreadable scan and a full disk look the
                // same on screen, and only one of them is in logcat.
                Log.w(SCAN_LOG_TAG, "Could not store a scan of ${pages.size} page(s)", failed)
                local.update { it.copy(saving = false, notice = ScanNotice.Failed) }
            }
        }
    }

    /** The screen has said it; it must not come back on the next recomposition. */
    fun noticeShown() {
        local.update { it.copy(notice = null) }
    }

    /** The screen has opened the new document; the id must not push it a second time. */
    fun openedHandled() {
        local.update { it.copy(opened = null) }
    }

    companion object {
        /** A factory *function*, because the navigation key carries the bunny id. */
        fun factory(bunnyId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    DocumentsViewModel(
                        bunnyId = bunnyId,
                        documents = app.container.documentRepository,
                        bunnies = app.container.bunnyRepository,
                        media = app.container.mediaFiles,
                    )
                }
            }
    }
}

/** Shared with the visit editor's attached list, so both render a document the same way. */
internal fun DocumentSummary.toRow(media: MediaFiles) =
    DocumentRow(
        id = document.id,
        title = document.title,
        pageCount = pageCount,
        thumbnail = firstPagePath?.let(media::resolve),
        dated = document.capturedAt ?: document.createdAt,
        hasDate = document.capturedAt != null,
    )
