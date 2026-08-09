package app.binky.tracker.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.AppPreferences
import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.TrendDrop
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchRepository
import app.binky.tracker.data.WeightEntity
import app.binky.tracker.data.WeightRepository
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.evaluateTrend
import app.binky.tracker.data.replaceable
import app.binky.tracker.data.toAcknowledgment
import app.binky.tracker.data.toWeighing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The entry form, as one immutable data class (house rule).
 *
 * [grams] is a `String` rather than an `Int` because it is what the owner has typed so far, which
 * includes "" and "2" on the way to "2495" — a form field that could not hold an incomplete number
 * would fight the keyboard.
 */
data class WeightEntryUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val bunnyName: String = "",
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    val grams: String = "",
    val gramsInvalid: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES),
    /** Set when the owner tried to save a timestamp in the future — stated, never silently clamped. */
    val inFuture: Boolean = false,
    /** Non-empty while the replace-or-add-a-second prompt is up (ADR-0021). */
    val collision: List<WeightEntity> = emptyList(),
    /**
     * Set when the row being edited was recorded at a visit (ADR-0017): the form then **reads** and
     * does not write, because the visit owns that number and re-derives its timestamp from its own
     * date. The screen offers the visit instead of a Save.
     */
    val visitId: String? = null,
    /** Set after the write when the flag is visible and unacknowledged: the dialog host. */
    val flagDrop: TrendDrop? = null,
    /** True while the delete confirmation is up. **One** confirmation, not ADR-0004's two. */
    val confirmingDelete: Boolean = false,
    /**
     * The weighing **as it stands on disk**, for the confirmation to name — deliberately not what
     * is currently in the fields, which may be a half-typed number the owner never saved.
     */
    val storedGrams: Int? = null,
    val storedRecordedAt: Instant? = null,
    /** Flipped once the write has landed *and* been reported, which is the screen's cue to leave. */
    val saved: Boolean = false,
) {
    /**
     * Minute granularity, because that is what the pickers offer — and because ADR-0021's collision
     * rule is an **exact** match, so the two have to agree on what "exact" means.
     */
    val recordedAt: Instant
        get() =
            date
                .atTime(time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .truncatedTo(ChronoUnit.MINUTES)

    val parsedGrams: Int? get() = grams.trim().toIntOrNull()?.takeIf { it > 0 }

    /**
     * The visit a *clashing* row belongs to, if one of them does (ADR-0021's amendment).
     *
     * When this is set the prompt drops *replace* entirely and offers to open the visit instead —
     * the destructive option is **absent** rather than merely not the default, because replacing
     * would leave the visit displaying a figure the vet never recorded.
     */
    val collisionVisitId: String? get() = collision.firstNotNullOfOrNull { it.visitId }
}

/**
 * Add or edit one weighing.
 *
 * **Entry is in grams** whatever the display preference says — that is what a scale reads out
 * (house rule) — and back-dating is normal: weighing in the morning and logging in the evening is
 * the ordinary case, so the timestamp defaults to now and is editable in both directions except
 * forward past now.
 */
class WeightEntryViewModel(
    private val bunnyId: String,
    private val weightId: String?,
    private val weights: WeightRepository,
    private val bunnies: BunnyRepository,
    private val watches: WatchRepository,
    preferences: AppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WeightEntryUiState(isNew = weightId == null))
    val uiState: StateFlow<WeightEntryUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding. */
    private var existing: WeightEntity? = null

    init {
        viewModelScope.launch {
            // Read once rather than collecting: a form fed by a Flow would overwrite the owner's
            // half-typed number every time the row it is editing emitted again.
            val weight = weightId?.let { id -> weights.series(bunnyId).first().firstOrNull { it.id == id } }
            existing = weight
            val recordedAt = weight?.recordedAt?.atZone(ZoneId.systemDefault())
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    bunnyName = bunnies.bunnyNow(bunnyId)?.name.orEmpty(),
                    unit = preferences.weightUnit.first(),
                    grams = weight?.grams?.toString() ?: "",
                    date = recordedAt?.toLocalDate() ?: state.date,
                    time = recordedAt?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES) ?: state.time,
                    visitId = weight?.visitId,
                    storedGrams = weight?.grams,
                    storedRecordedAt = weight?.recordedAt,
                )
            }
        }
    }

    fun onGramsChanged(grams: String) {
        // Digits only: the field is grams, and a stray separator would parse as a different number.
        _uiState.update { it.copy(grams = grams.filter(Char::isDigit), gramsInvalid = false) }
    }

    fun onDateChanged(date: LocalDate) {
        _uiState.update { it.copy(date = date, inFuture = false) }
    }

    fun onTimeChanged(time: LocalTime) {
        _uiState.update { it.copy(time = time.truncatedTo(ChronoUnit.MINUTES), inFuture = false) }
    }

    fun save() {
        val state = _uiState.value
        // A visit-recorded weighing is read-only here (ADR-0017). The screen renders no Save at all,
        // so this is the belt to that pair of braces rather than a path anyone can reach.
        if (state.visitId != null) return
        if (state.parsedGrams == null) {
            _uiState.update { it.copy(gramsInvalid = true) }
            return
        }
        // **Rejected with the reason stated, never silently clamped** — a clamp would store a
        // timestamp the owner did not choose, in the one series the app makes a safety claim about.
        if (state.recordedAt.isAfter(Instant.now())) {
            _uiState.update { it.copy(inFuture = true) }
            return
        }

        viewModelScope.launch {
            val clash = weights.existingAt(bunnyId, state.recordedAt).filter { it.id != weightId }
            if (clash.isEmpty()) {
                write(replacing = emptyList())
            } else {
                _uiState.update { it.copy(collision = clash) }
            }
        }
    }

    /**
     * The collision prompt's default (ADR-0021). Re-typing `2500` over a fat-fingered `250` at the
     * same minute must not leave *both* rows: the typo would become a prior and **displace** a real
     * weighing out of the three-wide baseline window, silently shortening effective history.
     */
    fun replaceExisting() {
        // Filtered a second time, and not defensively: this is the one function that could still
        // overwrite a visit's weighing, and the rule lives in `replaceable()` rather than in the
        // dialog that currently happens not to offer the button (ADR-0021's amendment).
        val clash = _uiState.value.collision.replaceable()
        _uiState.update { it.copy(collision = emptyList()) }
        viewModelScope.launch { write(replacing = clash) }
    }

    /** A genuine second weighing at the same minute. The total order exists to handle exactly this. */
    fun addSecond() {
        _uiState.update { it.copy(collision = emptyList()) }
        viewModelScope.launch { write(replacing = emptyList()) }
    }

    fun cancelCollision() {
        _uiState.update { it.copy(collision = emptyList()) }
    }

    fun requestDelete() {
        _uiState.update { it.copy(confirmingDelete = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    /**
     * Delete this weighing — **one** confirmation, not two. ADR-0004's two-stage ceremony is
     * calibrated to destroying a bunny's whole history; a single weighing is a correction.
     *
     * It moved here from the history list with Phase 7's redraw, which is also what puts the flag
     * a delete can raise on the right screen: it ends in [raiseFlagOrLeave], exactly as a save
     * does, so a delete that *deepens* a drop reports it before the route closes (ADR-0001).
     *
     * A visit-recorded weighing is not deletable here (ADR-0017) — the visit owns it, and clearing
     * its field is what removes the row. The screen offers no button; this is the belt to that.
     */
    fun confirmDelete() {
        val id = weightId ?: return
        if (_uiState.value.visitId != null) return
        _uiState.update { it.copy(confirmingDelete = false) }
        viewModelScope.launch {
            weights.delete(id)
            raiseFlagOrLeave()
        }
    }

    fun acknowledge() {
        _uiState.update { it.copy(flagDrop = null, saved = true) }
        viewModelScope.launch { weights.acknowledgeTrend(bunnyId) }
    }

    /** Dismissing is explicitly **not** acknowledging (ADR-0001). The screen still closes. */
    fun dismissFlag() {
        _uiState.update { it.copy(flagDrop = null, saved = true) }
    }

    /**
     * *Start a watch*, from the flag dialog raised by this write — **offered, never automatic**
     * (ADR-0001), and deliberately **not** an acknowledgment: starting a watch is the owner
     * deciding to look harder, which is the opposite of saying they have seen enough.
     */
    fun startWatch(duration: WatchDuration) {
        _uiState.update { it.copy(flagDrop = null, saved = true) }
        viewModelScope.launch { watches.start(bunnyId, duration) }
    }

    private suspend fun write(replacing: List<WeightEntity>) {
        val state = _uiState.value
        val grams = state.parsedGrams ?: return
        val recordedAt = state.recordedAt
        val row = existing

        when {
            // Adding onto an occupied timestamp: **update the row already there** rather than
            // inserting a second. That routes the commonest correction through an update, which
            // ADR-0001's unconditional discard rule already handles cleanly (ADR-0021).
            row == null && replacing.isNotEmpty() ->
                weights.update(replacing.first().copy(grams = grams, recordedAt = recordedAt))

            row == null -> weights.add(WeightEntity(bunnyId = bunnyId, grams = grams, recordedAt = recordedAt))

            else -> {
                weights.update(row.copy(grams = grams, recordedAt = recordedAt))
                // Editing an existing weighing *onto* another one's timestamp: ours carries the
                // value the owner just typed, so the one it replaces goes.
                replacing.forEach { weights.delete(it.id) }
            }
        }

        raiseFlagOrLeave()
    }

    /**
     * Every write ends here. The flag is re-evaluated on **edits and deletes as well as inserts**,
     * because correcting a *baseline* weight can deepen the real drop while leaving the current
     * reading untouched (ADR-0001).
     */
    private suspend fun raiseFlagOrLeave() {
        val evaluation =
            evaluateTrend(
                series = weights.series(bunnyId).first().map { it.toWeighing() },
                acknowledgment = weights.acknowledgment(bunnyId).first()?.toAcknowledgment(),
            )
        val drop = (evaluation.flag as? TrendFlag.WorthACloserLook)?.drop
        // Visible *and* unacknowledged, or the screen simply closes: an acknowledged episode is
        // standing information the banner already carries, not something to interrupt with again.
        _uiState.update { it.copy(flagDrop = drop, saved = drop == null) }
    }

    companion object {
        /** A factory *function*, because the navigation key carries arguments (as in the bunny editor). */
        fun factory(
            bunnyId: String,
            weightId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    WeightEntryViewModel(
                        bunnyId = bunnyId,
                        weightId = weightId,
                        weights = app.container.weightRepository,
                        bunnies = app.container.bunnyRepository,
                        watches = app.container.watchRepository,
                        preferences = app.container.preferences,
                    )
                }
            }
    }
}
