package app.binky.tracker.ui.bunny

import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.DeleteConfirmation
import app.binky.tracker.data.RecordCounts
import app.binky.tracker.data.deleteConfirmationFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The confirmation currently on screen, if any. */
sealed interface BunnyDialog {
    val bunnyId: String
    val name: String

    /**
     * Archiving asks **once**, stating that the records are kept (ADR-0004). It destroys nothing,
     * so it does not warrant deletion's ceremony — but it removes a bunny from the switcher, and an
     * owner who has not met the archive/delete distinction reads that as loss.
     */
    data class Archive(
        override val bunnyId: String,
        override val name: String,
    ) : BunnyDialog

    /**
     * Deleting asks once, and a second time when there is **history** to destroy — weights,
     * observations, photos, documents. An avatar and the profile fields deliberately do not count
     * (ADR-0004), so through Phase 1 every deletion is a single confirmation as a consequence of
     * the rule rather than as a special case for the phase.
     */
    data class Delete(
        override val bunnyId: String,
        override val name: String,
        val hasAvatar: Boolean,
        val counts: RecordCounts,
        val confirmedOnce: Boolean = false,
    ) : BunnyDialog {
        val twoStage: Boolean get() = deleteConfirmationFor(counts) == DeleteConfirmation.TWO_STAGE
    }
}

/**
 * The archive, unarchive and delete ceremonies, shared by Home and the archived list because both
 * offer them. One owner rather than a copy per screen: a second copy is a second chance to get
 * ADR-0004's two-stage rule wrong, and they would drift the moment Phase 2 makes the counts real.
 *
 * Kotlin note: this is a plain class, not a ViewModel — it borrows the calling ViewModel's
 * [scope], so its coroutines are cancelled with that screen and it needs no lifecycle of its own.
 */
class BunnyActions(
    private val bunnies: BunnyRepository,
    private val scope: CoroutineScope,
) {
    private val _dialog = MutableStateFlow<BunnyDialog?>(null)
    val dialog: StateFlow<BunnyDialog?> = _dialog.asStateFlow()

    fun requestArchive(profile: BunnyProfile) {
        _dialog.value = BunnyDialog.Archive(profile.id, profile.name)
    }

    /**
     * Reads the counts *before* opening the dialog, so the first confirmation already knows whether
     * a second one follows and what it will say.
     */
    fun requestDelete(profile: BunnyProfile) {
        scope.launch {
            _dialog.value =
                BunnyDialog.Delete(
                    bunnyId = profile.id,
                    name = profile.name,
                    hasAvatar = profile.avatar != null,
                    counts = bunnies.recordCounts(profile.id),
                )
        }
    }

    /** Unarchiving asks nothing; it only ever restores (ADR-0004). */
    fun unarchive(bunnyId: String) {
        scope.launch { bunnies.unarchive(bunnyId) }
    }

    fun dismiss() {
        _dialog.value = null
    }

    fun confirm() {
        when (val dialog = _dialog.value) {
            null -> Unit
            is BunnyDialog.Archive -> {
                _dialog.value = null
                scope.launch { bunnies.archive(dialog.bunnyId) }
            }

            is BunnyDialog.Delete ->
                if (dialog.twoStage && !dialog.confirmedOnce) {
                    // Second stage: same dialog, now stating what is actually destroyed.
                    _dialog.value = dialog.copy(confirmedOnce = true)
                } else {
                    _dialog.value = null
                    scope.launch { bunnies.delete(dialog.bunnyId) }
                }
        }
    }
}
