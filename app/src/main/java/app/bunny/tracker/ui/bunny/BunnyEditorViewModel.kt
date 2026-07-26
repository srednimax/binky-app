package app.bunny.tracker.ui.bunny

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.bunny.tracker.BunnyTrackerApplication
import app.bunny.tracker.data.BunnyDao
import app.bunny.tracker.data.BunnyEntity
import app.bunny.tracker.data.BunnyRepository
import app.bunny.tracker.data.FluffleRepository
import app.bunny.tracker.data.NeuterStatus
import app.bunny.tracker.data.Sex
import app.bunny.tracker.media.MediaFiles
import app.bunny.tracker.media.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** A bunny this one could be living with. Archived members of the current fluffle are included. */
data class HousemateOption(
    val id: String,
    val name: String,
    val archived: Boolean,
)

/**
 * The editor form, as one immutable data class (house rule). `name` is the only required field —
 * trimmed, empty rejected, duplicates allowed (ADR-0016).
 */
data class BunnyEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val nameMissing: Boolean = false,
    /** Relative, `avatars/<uuid>.jpg` — what goes on the row. */
    val avatarPath: String? = null,
    /** The same file, resolved for rendering. */
    val avatar: File? = null,
    val avatarFailed: Boolean = false,
    val birthDate: LocalDate? = null,
    val birthDateApproximate: Boolean = false,
    val sex: Sex = Sex.UNKNOWN,
    val neutered: NeuterStatus = NeuterStatus.UNKNOWN,
    val breed: String = "",
    /**
     * Breeds any bunny already carries — the stored half of the picker's list (ADR-0010's reasoning
     * deliberately *not* applied; see [BunnyDao.breeds]). The built-in half comes from `strings.xml`
     * and is joined on in the composable, because only it can resolve the owner's language.
     */
    val breedSuggestions: List<String> = emptyList(),
    val colour: String = "",
    val housemateId: String? = null,
    val candidates: List<HousemateOption> = emptyList(),
    /** Flipped once the write lands, which is the screen's cue to navigate back. */
    val saved: Boolean = false,
)

/**
 * Add or edit one bunny.
 *
 * The avatar is persisted the moment it is picked, **before** any row mentions it (ADR-0020), so a
 * crash between the two leaves an invisible orphan file rather than a row pointing at nothing —
 * which to the owner looks exactly like losing their bunny's photo.
 */
class BunnyEditorViewModel(
    private val bunnyId: String?,
    private val bunnies: BunnyRepository,
    private val fluffles: FluffleRepository,
    private val media: MediaFiles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BunnyEditorUiState(isNew = bunnyId == null))
    val uiState: StateFlow<BunnyEditorUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding. */
    private var existing: BunnyEntity? = null

    /** The avatar the row already points at — the one a replacement supersedes. */
    private var storedAvatarPath: String? = null

    init {
        viewModelScope.launch {
            val bunny = bunnyId?.let { bunnies.bunnyNow(it) }
            existing = bunny
            storedAvatarPath = bunny?.avatarPath

            // Read once rather than collecting: a form fed by a Flow would overwrite the owner's
            // half-typed name every time the row it is editing emitted again.
            val active = bunnies.activeBunnies.first()
            val archived = bunnies.archivedBunnies.first()
            val fluffleId = bunny?.fluffleId
            val candidates =
                (active + archived.filter { fluffleId != null && it.fluffleId == fluffleId })
                    .filter { it.id != bunnyId }
                    .map { HousemateOption(id = it.id, name = it.name, archived = it.archivedAt != null) }

            _uiState.update { state ->
                state.copy(
                    loading = false,
                    name = bunny?.name ?: "",
                    avatarPath = bunny?.avatarPath,
                    avatar = bunny?.avatarPath?.let(media::resolve),
                    birthDate = bunny?.birthDate,
                    birthDateApproximate = bunny?.birthDateApproximate ?: false,
                    sex = bunny?.sex ?: Sex.UNKNOWN,
                    neutered = bunny?.neutered ?: NeuterStatus.UNKNOWN,
                    breed = bunny?.breed ?: "",
                    breedSuggestions = bunnies.breeds.first(),
                    colour = bunny?.colour ?: "",
                    // Any current member will do: joining one member of a trio joins the trio.
                    housemateId =
                        (active + archived)
                            .firstOrNull { it.id != bunnyId && fluffleId != null && it.fluffleId == fluffleId }
                            ?.id,
                    candidates = candidates,
                )
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameMissing = false) }
    }

    fun onBirthDateChanged(date: LocalDate?) {
        // Clearing the date clears the flag with it: "approximately no birthday" means nothing.
        _uiState.update { state ->
            state.copy(birthDate = date, birthDateApproximate = date != null && state.birthDateApproximate)
        }
    }

    fun onBirthDateApproximateChanged(approximate: Boolean) {
        _uiState.update { it.copy(birthDateApproximate = approximate) }
    }

    fun onSexChanged(sex: Sex) {
        _uiState.update { it.copy(sex = sex) }
    }

    fun onNeuteredChanged(neutered: NeuterStatus) {
        _uiState.update { it.copy(neutered = neutered) }
    }

    fun onBreedChanged(breed: String) {
        _uiState.update { it.copy(breed = breed) }
    }

    fun onColourChanged(colour: String) {
        _uiState.update { it.copy(colour = colour) }
    }

    /** Null means "lives alone". Any member of an existing group joins that group (ADR-0008). */
    fun onHousemateChanged(housemateId: String?) {
        _uiState.update { it.copy(housemateId = housemateId) }
    }

    /**
     * Downsamples and re-encodes through the media helper — never straight onto the row — so a
     * 12 MP camera shot becomes a 512² JPEG with its orientation baked in and its GPS tag gone.
     */
    fun onAvatarPicked(source: Uri) {
        viewModelScope.launch {
            val previous = _uiState.value.avatarPath
            val path =
                try {
                    media.persist(source, MediaKind.Avatar)
                } catch (e: IOException) {
                    _uiState.update { it.copy(avatarFailed = true) }
                    return@launch
                } catch (e: SecurityException) {
                    // A Uri whose grant expired — the owner picked, rotated, and came back.
                    _uiState.update { it.copy(avatarFailed = true) }
                    return@launch
                }
            discardIfUnreferenced(previous)
            _uiState.update { it.copy(avatarPath = path, avatar = media.resolve(path), avatarFailed = false) }
        }
    }

    fun onAvatarRemoved() {
        discardIfUnreferenced(_uiState.value.avatarPath)
        _uiState.update { it.copy(avatarPath = null, avatar = null, avatarFailed = false) }
    }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(nameMissing = true) }
            return
        }

        viewModelScope.launch {
            val previous = existing
            val bunny =
                (previous ?: BunnyEntity(name = name)).copy(
                    name = name,
                    avatarPath = state.avatarPath,
                    birthDate = state.birthDate,
                    birthDateApproximate = state.birthDate != null && state.birthDateApproximate,
                    sex = state.sex,
                    neutered = state.neutered,
                    breed = state.breed.trim().ifEmpty { null },
                    colour = state.colour.trim().ifEmpty { null },
                )
            if (previous == null) bunnies.add(bunny) else bunnies.update(bunny)

            // "Lives with" is a symmetric join, and both directions run through the repository's
            // one dissolve predicate — including the case where leaving reduces a pair to a solo
            // bunny on each side (ADR-0008). A no-op when the group is already the current one.
            when {
                state.housemateId != null -> fluffles.livesWith(bunny.id, state.housemateId)
                previous?.fluffleId != null -> fluffles.leaveFluffle(bunny.id)
            }

            // The row no longer points at the old file, so nothing does.
            storedAvatarPath?.takeIf { it != state.avatarPath }?.let(media::delete)
            _uiState.update { it.copy(saved = true) }
        }
    }

    /**
     * Drops an avatar that was persisted during this edit and then replaced or removed before
     * saving: no row has ever pointed at it. The one already on the row is left alone until the
     * save succeeds.
     */
    private fun discardIfUnreferenced(path: String?) {
        if (path != null && path != storedAvatarPath) media.delete(path)
    }

    companion object {
        /**
         * Manual DI, not Hilt (house rule). A factory *function* because this ViewModel takes an
         * argument the navigation key carries — `viewModel(factory = …)` cannot pass one otherwise.
         */
        fun factory(bunnyId: String?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BunnyTrackerApplication
                    BunnyEditorViewModel(
                        bunnyId = bunnyId,
                        bunnies = app.container.bunnyRepository,
                        fluffles = app.container.fluffleRepository,
                        media = app.container.mediaFiles,
                    )
                }
            }
    }
}
