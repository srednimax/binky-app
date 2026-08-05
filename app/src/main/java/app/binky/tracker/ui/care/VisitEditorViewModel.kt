package app.binky.tracker.ui.care

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.AppPreferences
import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.DocumentRepository
import app.binky.tracker.data.VetEntity
import app.binky.tracker.data.VetRepository
import app.binky.tracker.data.VisitEntity
import app.binky.tracker.data.VisitRepository
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.media.MediaFiles
import app.binky.tracker.ui.documents.DocumentRow
import app.binky.tracker.ui.documents.ScanNotice
import app.binky.tracker.ui.documents.toRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Short enough for logcat's tag column, specific enough to filter on. */
private const val VISIT_SCAN_LOG_TAG = "BinkyVisitScan"

/**
 * The visit form, as one immutable data class (house rule).
 *
 * [grams] is a `String` for the reason the weight form's is: it holds what the owner has typed so
 * far, which includes "" and "2" on the way to "2495". **Empty means no weighing was taken**, which
 * is the ordinary case for a consultation — not a validation failure.
 */
data class VisitEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val bunnyName: String = "",
    val visitedOn: LocalDate = LocalDate.now(),
    /** Set when the owner tried to save a day they have not reached — stated, never clamped. */
    val inFuture: Boolean = false,
    val reason: String = "",
    val reasonInvalid: Boolean = false,
    val notes: String = "",
    val vetId: String? = null,
    /** The whole directory, live: the inline "add a new vet" writes into it mid-form (ADR-0017). */
    val vets: List<VetEntity> = emptyList(),
    val grams: String = "",
    val gramsInvalid: Boolean = false,
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    /**
     * The paperwork this visit produced (ADR-0017). Empty for a visit not yet saved — there is no
     * `visitId` for a document to point at until the row exists.
     */
    val documents: List<DocumentRow> = emptyList(),
    /** This bunny's documents no visit has claimed, loaded when the picker opens. */
    val attachable: List<DocumentRow> = emptyList(),
    val scanning: Boolean = false,
    val scanNotice: ScanNotice? = null,
    val saved: Boolean = false,
) {
    val vetName: String? get() = vets.firstOrNull { it.id == vetId }?.name

    val parsedGrams: Int? get() = grams.trim().toIntOrNull()?.takeIf { it > 0 }

    /** Typed something that is not a weight, as opposed to having typed nothing at all. */
    val gramsUnparseable: Boolean get() = grams.isNotBlank() && parsedGrams == null
}

/**
 * Add or edit one vet visit, **and the weighing taken at it** (ADR-0017).
 *
 * The weight is the reason this screen is more than a form: a visit and its weighing are written in
 * one transaction by [VisitRepository], so there is no path here that produces two numbers or a
 * weight the visit does not own. Editing the weight edits *that* row; clearing the field deletes it.
 */
class VisitEditorViewModel(
    private val bunnyId: String,
    private val visitId: String?,
    private val visits: VisitRepository,
    private val vets: VetRepository,
    private val bunnies: BunnyRepository,
    private val documents: DocumentRepository,
    private val media: MediaFiles,
    preferences: AppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitEditorUiState(isNew = visitId == null))
    val uiState: StateFlow<VisitEditorUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding — `createdAt` has to survive an edit. */
    private var existing: VisitEntity? = null

    init {
        viewModelScope.launch {
            // Read once rather than collecting, so an emission cannot overwrite a half-typed form.
            val details = visitId?.let { visits.visit(it).first() }
            existing = details?.visit
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    bunnyName = bunnies.bunnyNow(bunnyId)?.name.orEmpty(),
                    unit = preferences.weightUnit.first(),
                    visitedOn = details?.visit?.visitedOn ?: state.visitedOn,
                    reason = details?.visit?.reason.orEmpty(),
                    notes = details?.visit?.notes.orEmpty(),
                    vetId = details?.visit?.vetId,
                    grams = details?.weightGrams?.toString() ?: "",
                )
            }
        }
        // The directory *is* collected, unlike everything else here: adding a vet inline has to make
        // it appear in the picker without closing the form.
        viewModelScope.launch {
            vets.vets.collect { directory -> _uiState.update { it.copy(vets = directory) } }
        }
        // So is the attached paperwork, for the same reason: scanning or attaching one has to show
        // up without leaving the form. Only for a visit that exists — a document points at a
        // `visitId`, and an unsaved visit has none.
        if (visitId != null) {
            viewModelScope.launch {
                documents.documentsOfVisit(visitId).collect { rows ->
                    _uiState.update { state -> state.copy(documents = rows.map { it.toRow(media) }) }
                }
            }
        }
    }

    /** Fills the attach picker. A one-shot read: the list is only looked at while it is open. */
    fun loadAttachable() {
        viewModelScope.launch {
            val rows = documents.unattached(bunnyId).first().map { it.toRow(media) }
            _uiState.update { it.copy(attachable = rows) }
        }
    }

    fun attachDocument(documentId: String) {
        val visit = visitId ?: return
        viewModelScope.launch { documents.attachToVisit(documentId, visit) }
    }

    /** Detaching leaves the document with its bunny — it is the health record (ADR-0017). */
    fun detachDocument(documentId: String) {
        viewModelScope.launch { documents.attachToVisit(documentId, null) }
    }

    /**
     * Records a scan straight onto this visit, which is the path the plan asked for: paperwork comes
     * *from* a visit, and making the owner scan it elsewhere and then come back to attach it would
     * be two screens for one act.
     */
    fun scanInto(
        title: String,
        pages: List<Uri>,
        guided: Boolean,
    ) {
        val visit = visitId ?: return
        if (pages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true, scanNotice = null) }
            try {
                documents.add(bunnyId = bunnyId, title = title, pages = pages, visitId = visit)
                _uiState.update {
                    it.copy(scanning = false, scanNotice = if (guided) null else ScanNotice.FellBackToCamera)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                Log.w(VISIT_SCAN_LOG_TAG, "Could not store a scan for visit $visitId", failed)
                _uiState.update { it.copy(scanning = false, scanNotice = ScanNotice.Failed) }
            }
        }
    }

    fun scanNoticeShown() {
        _uiState.update { it.copy(scanNotice = null) }
    }

    fun onVisitedOnChanged(date: LocalDate) {
        _uiState.update { it.copy(visitedOn = date, inFuture = false) }
    }

    fun onReasonChanged(reason: String) {
        _uiState.update { it.copy(reason = reason, reasonInvalid = false) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onGramsChanged(grams: String) {
        // Digits only: the field is grams, and a stray separator would parse as a different number.
        _uiState.update { it.copy(grams = grams.filter(Char::isDigit), gramsInvalid = false) }
    }

    fun onVetChanged(vetId: String?) {
        _uiState.update { it.copy(vetId = vetId) }
    }

    /**
     * The picker's **add-your-own** row (ADR-0017): the moment an owner needs a vet record is the
     * moment they are typing a visit, so a name alone makes an entry and the rest can be filled in
     * from the directory later. Selecting it is part of adding it — nobody types a name in order not
     * to use it.
     */
    fun addVet(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = vets.add(VetEntity(name = name))
            _uiState.update { it.copy(vetId = id) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.reason.isBlank()) {
            _uiState.update { it.copy(reasonInvalid = true) }
            return
        }
        if (state.gramsUnparseable) {
            _uiState.update { it.copy(gramsInvalid = true) }
            return
        }
        // **Rejected with the reason stated, never silently clamped** — the same rule every other
        // entry in the app follows, and the repository refuses it a second time on its own.
        if (state.visitedOn.isAfter(LocalDate.now())) {
            _uiState.update { it.copy(inFuture = true) }
            return
        }

        viewModelScope.launch {
            val row =
                (existing ?: VisitEntity(bunnyId = bunnyId, visitedOn = state.visitedOn, reason = state.reason))
                    .copy(
                        vetId = state.vetId,
                        visitedOn = state.visitedOn,
                        reason = state.reason,
                        notes = state.notes.ifBlank { null },
                    )
            if (existing == null) {
                visits.add(row, grams = state.parsedGrams)
            } else {
                visits.update(row, grams = state.parsedGrams)
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        /** A factory *function*, because the navigation key carries arguments (as in the bunny editor). */
        fun factory(
            bunnyId: String,
            visitId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    VisitEditorViewModel(
                        bunnyId = bunnyId,
                        visitId = visitId,
                        visits = app.container.visitRepository,
                        vets = app.container.vetRepository,
                        bunnies = app.container.bunnyRepository,
                        documents = app.container.documentRepository,
                        media = app.container.mediaFiles,
                        preferences = app.container.preferences,
                    )
                }
            }
    }
}
