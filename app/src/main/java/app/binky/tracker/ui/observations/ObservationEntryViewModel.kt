package app.binky.tracker.ui.observations

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.ActivityLevel
import app.binky.tracker.data.Appetite
import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.Cecotropes
import app.binky.tracker.data.DroppingsAmount
import app.binky.tracker.data.DroppingsAppearance
import app.binky.tracker.data.DroppingsSize
import app.binky.tracker.data.ExcludedParticipant
import app.binky.tracker.data.FluffleRepository
import app.binky.tracker.data.IndividualFacts
import app.binky.tracker.data.Mood
import app.binky.tracker.data.ObservationFacts
import app.binky.tracker.data.ObservationRepository
import app.binky.tracker.data.ParticipantCandidate
import app.binky.tracker.data.SymptomEntity
import app.binky.tracker.data.SymptomRepository
import app.binky.tracker.data.TrayFacts
import app.binky.tracker.data.WatchRepository
import app.binky.tracker.data.WaterIntake
import app.binky.tracker.data.individualFacts
import app.binky.tracker.data.preSelectParticipants
import app.binky.tracker.media.MediaFiles
import app.binky.tracker.media.MediaKind
import app.binky.tracker.work.WatchNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The observation form, as one immutable data class (house rule).
 *
 * **Every field is optional**, and every graded field starts `null` — which *is* "not checked"
 * (ADR-0001). An untouched form therefore records nothing rather than a silent "normal": a "fine"
 * nobody verified is a false reassurance, and droppings amount is the field where that matters most.
 */
data class ObservationEntryUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** Whose individual facts this form edits. Under a shared observation the others are untouched. */
    val subjectName: String = "",
    val candidates: List<ParticipantCandidate> = emptyList(),
    /** Fluffle members deliberately not offered, each with a reason to state (ADR-0008). */
    val excluded: List<ExcludedParticipant> = emptyList(),
    val selectedParticipants: Set<String> = emptySet(),
    val noParticipants: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES),
    /** Set when the owner tried to save a timestamp in the future — stated, never silently clamped. */
    val inFuture: Boolean = false,
    val tray: TrayFacts = TrayFacts(),
    /**
     * The tray photo as a file to draw, resolved from [TrayFacts.trayPhotoPath] — the path on the row
     * is relative and only [MediaFiles] knows what it is relative to (house rule).
     *
     * A file that has gone missing still resolves; it draws as a placeholder rather than crashing,
     * which is what a restore lacking its media has to do.
     */
    val trayPhoto: File? = null,
    /** Set when the picked image could not be read. Stated once, and the form keeps whatever it had. */
    val trayPhotoUnreadable: Boolean = false,
    val individual: IndividualFacts = IndividualFacts(),
    /** Every symptom, retired ones included — the picker filters, so a ticked-then-retired one stays untickable-away. */
    val symptoms: List<SymptomEntity> = emptyList(),
    val saved: Boolean = false,
) {
    /** Minute granularity, because that is what the pickers offer. */
    val recordedAt: Instant
        get() =
            date
                .atTime(time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .truncatedTo(ChronoUnit.MINUTES)

    /** The picker's rows: everything still offered, plus anything already ticked so it can be unticked. */
    val pickableSymptoms: List<SymptomEntity>
        get() = symptoms.filter { it.hiddenAt == null || it.id in individual.symptomIds }

    /** Shown only when there is somebody to share with — a solo bunny needs no participant row. */
    val offersParticipants: Boolean get() = candidates.size > 1 || excluded.isNotEmpty()
}

/**
 * Add or edit one observation.
 *
 * The write paths respect ADR-0008's tray/individual split rather than re-deciding it: tray facts
 * and the timestamp move every participant's row, individual facts move exactly one. That is why
 * saving an edit is several repository calls and not one — each is a different claim about who the
 * change is about.
 */
class ObservationEntryViewModel(
    private val bunnyId: String,
    private val observationId: String?,
    private val observations: ObservationRepository,
    private val symptoms: SymptomRepository,
    private val bunnies: BunnyRepository,
    private val fluffles: FluffleRepository,
    private val watches: WatchRepository,
    private val watchNotifier: WatchNotifier,
    private val media: MediaFiles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservationEntryUiState(isNew = observationId == null))
    val uiState: StateFlow<ObservationEntryUiState> = _uiState.asStateFlow()

    /** Who the observation covered when the form opened — the baseline a correction is diffed against. */
    private var storedParticipants: Set<String> = emptySet()

    /**
     * The tray photo the observation already had when the form opened, or null.
     *
     * It is the line between a file some row still points at and one only this form has ever seen: a
     * shot the owner takes here and then retakes is referenced by nothing, so it can be deleted on the
     * spot rather than becoming an orphan nobody will ever find. The *stored* one is never deleted
     * here — that is [ObservationRepository.updateTray]'s job, after the write commits, and only once
     * no row references it (ADR-0029).
     */
    private var storedTrayPhotoPath: String? = null

    init {
        viewModelScope.launch {
            val subject = bunnies.bunnyNow(bunnyId)
            val active = bunnies.activeBunnies.first()
            val archived = bunnies.archivedBunnies.first()
            val everyBunny = active + archived
            val members = subject?.fluffleId?.let { fluffles.members(it).first() }.orEmpty()

            // The current fluffle pre-selects; it never *defines* who an existing observation
            // covered. That is stamped at creation and read back below (ADR-0008). A housemate
            // under a running watch is left out with the reason shown — one predicate, exactly
            // where 2f built the road for it.
            val preSelection = subject?.let { preSelectParticipants(it, members, watches.activelyWatchedIdsNow()) }

            val existing = observationId?.let { observations.observationNow(it) }
            val storedGroup =
                if (existing == null) {
                    emptySet()
                } else {
                    observations.participantsNow(existing.id).ifEmpty { listOf(existing.bunnyId) }.toSet()
                }
            storedParticipants = storedGroup

            // A participant who has since left the fluffle is still a participant, so the chips are
            // the pre-selection *plus* whoever this observation already covers.
            val candidates =
                buildList {
                    preSelection?.candidates?.let { addAll(it) }
                    for (id in storedGroup) {
                        if (none { it.bunnyId == id }) {
                            val name = everyBunny.firstOrNull { it.id == id }?.name ?: continue
                            add(ParticipantCandidate(id, name))
                        }
                    }
                }

            val recordedAt = existing?.recordedAt?.atZone(ZoneId.systemDefault())
            val storedTray = existing?.let { observations.trayFactsNow(it.id) } ?: TrayFacts()
            storedTrayPhotoPath = storedTray.trayPhotoPath
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    subjectName = subject?.name.orEmpty(),
                    candidates = candidates,
                    excluded = preSelection?.excluded.orEmpty(),
                    selectedParticipants = storedGroup.ifEmpty { preSelection?.bunnyIds.orEmpty().toSet() },
                    date = recordedAt?.toLocalDate() ?: state.date,
                    time = recordedAt?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES) ?: state.time,
                    // The whole tray fact in one read: the two sets are join rows now, and a form
                    // that assembled them separately could open with one of them silently missing.
                    tray = storedTray,
                    trayPhoto = storedTray.trayPhotoPath?.let(media::resolve),
                    individual =
                        existing
                            ?.individualFacts()
                            ?.copy(symptomIds = observations.symptomIdsNow(existing.id))
                            ?: IndividualFacts(),
                    symptoms = symptoms.allSymptoms.first(),
                )
            }
        }
    }

    fun toggleParticipant(bunnyId: String) {
        _uiState.update { state ->
            val selected = state.selectedParticipants
            state.copy(
                selectedParticipants = if (bunnyId in selected) selected - bunnyId else selected + bunnyId,
                noParticipants = false,
            )
        }
    }

    fun onDateChanged(date: LocalDate) {
        _uiState.update { it.copy(date = date, inFuture = false) }
    }

    fun onTimeChanged(time: LocalTime) {
        _uiState.update { it.copy(time = time.truncatedTo(ChronoUnit.MINUTES), inFuture = false) }
    }

    // Tray-level. Every one of these lands identically on every participant (ADR-0008).
    fun onDroppingsAmountChanged(value: DroppingsAmount?) = updateTray { it.copy(droppingsAmount = value) }

    /*
     * The two multi-valued ones toggle rather than select, and an empty set is a legitimate resting
     * state — it is what "not checked" looks like when the fact is a set (ADR-0029).
     */

    fun toggleDroppingsSize(value: DroppingsSize) =
        updateTray { it.copy(droppingsSizes = it.droppingsSizes.toggle(value)) }

    fun toggleDroppingsAppearance(value: DroppingsAppearance) =
        updateTray { it.copy(droppingsAppearance = it.droppingsAppearance.toggle(value)) }

    fun onCecotropesChanged(value: Cecotropes?) = updateTray { it.copy(cecotropes = value) }

    /**
     * Stores a tray photo through the media helper and hangs it on the tray facts.
     *
     * **Through [MediaKind.Observation], and from the ordinary camera or the photo picker — never the
     * document scanner** (ADR-0029). ML Kit accepts a tray and then clips the highlights and
     * edge-enhances it, which destroys pellet outlines exactly where the light was good. The plain
     * capture path is not a fallback here, it is the correct instrument.
     *
     * The file is written now rather than at save, because the form has to draw it. An abandoned form
     * therefore leaves an unreferenced file, which is ADR-0020's chosen failure: an invisible orphan
     * beats a row whose photo is missing.
     */
    fun onTrayPhotoPicked(source: Uri) {
        viewModelScope.launch {
            val stored = runCatching { media.persist(source, MediaKind.Observation) }.getOrNull()
            if (stored == null) {
                _uiState.update { it.copy(trayPhotoUnreadable = true) }
                return@launch
            }
            replaceTrayPhoto(stored.path)
        }
    }

    fun onTrayPhotoCleared() = replaceTrayPhoto(null)

    fun trayPhotoMessageShown() {
        _uiState.update { it.copy(trayPhotoUnreadable = false) }
    }

    private fun replaceTrayPhoto(path: String?) {
        val previous = _uiState.value.tray.trayPhotoPath
        // Only ever a file this form minted: see [storedTrayPhotoPath] for why the stored one is
        // somebody else's to delete.
        if (previous != null && previous != storedTrayPhotoPath) media.delete(previous)
        _uiState.update {
            it.copy(
                tray = it.tray.copy(trayPhotoPath = path),
                trayPhoto = path?.let(media::resolve),
                trayPhotoUnreadable = false,
            )
        }
    }

    // Individual. These legitimately differ between two rabbits sharing a tray.
    fun onAppetiteChanged(value: Appetite?) = updateIndividual { it.copy(appetite = value) }

    fun onMoodChanged(value: Mood?) = updateIndividual { it.copy(mood = value) }

    fun onActivityChanged(value: ActivityLevel?) = updateIndividual { it.copy(activity = value) }

    fun onWaterChanged(value: WaterIntake?) = updateIndividual { it.copy(water = value) }

    fun onNoteChanged(note: String) = updateIndividual { it.copy(note = note) }

    /**
     * The explicit **"none seen"** tick — mutually exclusive with having selections, because a
     * ticked symptom already means the owner looked (ADR-0010).
     *
     * Unticking it while symptoms are selected would be incoherent, so selections win: the state is
     * normalised again in the repository, and this only keeps the form from showing a contradiction.
     */
    fun onSymptomsCheckedChanged(checked: Boolean) {
        _uiState.update { state ->
            state.copy(
                individual =
                    state.individual.copy(
                        symptomsChecked = checked || state.individual.symptomIds.isNotEmpty(),
                        symptomIds = if (checked) state.individual.symptomIds else emptySet(),
                    ),
            )
        }
    }

    fun toggleSymptom(symptomId: String) {
        _uiState.update { state ->
            val ticked = state.individual.symptomIds
            val next = if (symptomId in ticked) ticked - symptomId else ticked + symptomId
            // Any tick implies the owner looked. Enforced again in the repository; set here so the
            // form cannot show a ticked symptom beside an unticked "I looked".
            state.copy(
                individual =
                    state.individual.copy(
                        symptomIds = next,
                        symptomsChecked =
                            next.isNotEmpty() || state.individual.symptomsChecked,
                    ),
            )
        }
    }

    /**
     * Adds an owner's symptom and ticks it, or ticks the existing one it duplicates.
     *
     * [builtInLabels] comes from the screen because the duplicate check has to compare against the
     * labels the owner can currently see, in their language — which needs resources the data layer
     * deliberately does not have (ADR-0010).
     */
    fun addSymptom(
        label: String,
        builtInLabels: Map<String, String>,
    ) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = symptoms.add(trimmed, builtInLabels)
            _uiState.update { state ->
                state.copy(
                    symptoms = symptoms.allSymptoms.first(),
                    individual =
                        state.individual.copy(
                            symptomIds = state.individual.symptomIds + id,
                            symptomsChecked = true,
                        ),
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.selectedParticipants.isEmpty()) {
            _uiState.update { it.copy(noParticipants = true) }
            return
        }
        // Rejected with the reason stated, never silently clamped — the same rule as weight, because
        // it is the same promise: the timestamp stored is the one the owner chose.
        if (state.recordedAt.isAfter(Instant.now())) {
            _uiState.update { it.copy(inFuture = true) }
            return
        }

        viewModelScope.launch {
            val existingId = observationId
            if (existingId == null) {
                val ids =
                    observations.add(
                        // The subject first, so a solo write reads as being about them rather than
                        // about whoever sorts first — and so the subject's row is `ids.first()`.
                        participants = listOf(bunnyId) + (state.selectedParticipants - bunnyId),
                        recordedAt = state.recordedAt,
                        // Tray facts only. `add` applies whatever individual facts it is given to
                        // *every* participant, which is right for the one-tap healthy day and wrong
                        // here: this form only ever showed the subject's individual fields, so the
                        // owner made no claim at all about a housemate's mood. Writing one would
                        // manufacture a record nobody made (ADR-0001, ADR-0008).
                        facts = ObservationFacts(tray = state.tray),
                    )
                // The individual half, on the subject's row alone — the same split the edit path
                // takes, so a new observation and an edited one store the same shape.
                observations.updateIndividual(ids.first(), state.individual)
            } else {
                applyEdit(existingId, state)
            }
            // Whoever this covers has now been looked at, so this morning's nag is answered — and a
            // question still in the shade after it has been answered is the only copy of that
            // staleness left anywhere (the argument `CareNotifier.cancel` makes for a completion).
            // Tapping the nag itself needs no help: the notification is `setAutoCancel(true)`.
            state.selectedParticipants.forEach(watchNotifier::cancel)
            _uiState.update { it.copy(saved = true) }
        }
    }

    /**
     * The order below is the tray/individual split expressed as a sequence, and every step depends
     * on the one before it:
     *
     * 1. **The timestamp and the tray facts first**, group-wide — so that
     * 2. **a newly added participant inherits the corrected values**, not the ones on screen a moment
     *    ago. `addParticipant` copies what is *stored*.
     * 3. **Individual facts** touch this row only, whatever else moved.
     * 4. **Adding before removing**, so that correcting a solo observation into "it was the other
     *    one, not this one" mints the group and then leaves the survivor solo — rather than removing
     *    from a group that does not exist yet and silently doing nothing.
     */
    private suspend fun applyEdit(
        existingId: String,
        state: ObservationEntryUiState,
    ) {
        observations.updateRecordedAt(existingId, state.recordedAt)
        observations.updateTray(existingId, state.tray)
        observations.updateIndividual(existingId, state.individual)

        for (added in state.selectedParticipants - storedParticipants) {
            observations.addParticipant(existingId, added)
        }
        for (removed in storedParticipants - state.selectedParticipants) {
            // A correction, deliberately not the same event as deleting a bunny: dropping the group
            // to one clears the survivor's group id and the observation becomes solo again
            // (ADR-0008). Deleting the whole observation is a different action entirely.
            observations.removeParticipant(existingId, removed)
        }
    }

    /**
     * Kotlin note: `Set` is read-only here, so a toggle returns a **new** set rather than mutating
     * one — the same reason the UI state is a data class and every change is a `copy()`. Compose
     * recomposes on a new value, not on a mutation it cannot see.
     */
    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    private fun updateTray(transform: (TrayFacts) -> TrayFacts) {
        _uiState.update { it.copy(tray = transform(it.tray)) }
    }

    private fun updateIndividual(transform: (IndividualFacts) -> IndividualFacts) {
        _uiState.update { it.copy(individual = transform(it.individual)) }
    }

    companion object {
        /** A factory *function*, because the navigation key carries arguments (as in the bunny editor). */
        fun factory(
            bunnyId: String,
            observationId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    ObservationEntryViewModel(
                        bunnyId = bunnyId,
                        observationId = observationId,
                        observations = app.container.observationRepository,
                        symptoms = app.container.symptomRepository,
                        bunnies = app.container.bunnyRepository,
                        fluffles = app.container.fluffleRepository,
                        watches = app.container.watchRepository,
                        watchNotifier = app.container.watchNotifier,
                        media = app.container.mediaFiles,
                    )
                }
            }
    }
}
