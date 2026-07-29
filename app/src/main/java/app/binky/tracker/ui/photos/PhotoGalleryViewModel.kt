package app.binky.tracker.ui.photos

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
import app.binky.tracker.data.PhotoEntity
import app.binky.tracker.data.PhotoRepository
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
private const val IMPORT_LOG_TAG = "BinkyPhotoImport"

/**
 * One tile in the grid, and one page in the viewer.
 *
 * [file] is already resolved against `filesDir` — screens never see the relative path, which is a
 * storage detail (house rule). It may point at nothing: a restored backup can legitimately be
 * missing pictures, and the tile renders a placeholder for that rather than failing.
 *
 * [takenAt] is the date the gallery orders and labels by, and [dated] says which of the two it came
 * from, so the label can be honest about it — "taken" is a claim only the camera's own metadata can
 * support, and most images carry none.
 */
data class GalleryPhoto(
    val id: String,
    val file: File,
    val caption: String?,
    val takenAt: Instant,
    val dated: Boolean,
)

/** A determinate count, because a twenty-photo import is long enough that a spinner is a lie. */
data class ImportProgress(
    val done: Int,
    val total: Int,
)

/** Reported once, at the end — not once per failure (see [addAll]). */
data class ImportResult(
    val added: Int,
    val unreadable: Int,
)

data class PhotoGalleryUiState(
    val loading: Boolean = true,
    val bunnyName: String = "",
    val photos: List<GalleryPhoto> = emptyList(),
    /** Non-null while an import is running; the grid stays live underneath it. */
    val importing: ImportProgress? = null,
    /** Set when an import has finished and the screen has not yet told the owner. */
    val result: ImportResult? = null,
)

/**
 * A bunny's photo gallery.
 *
 * The list itself comes straight off the DAO's `Flow`, so a photo appears the moment its row lands
 * (house rule: no hand-rolled refresh). Everything the ViewModel knows that the database does not —
 * the bunny's name, an import in flight, a result waiting to be shown — lives in [local] and is
 * combined with it.
 */
class PhotoGalleryViewModel(
    private val bunnyId: String,
    private val photos: PhotoRepository,
    private val bunnies: BunnyRepository,
    private val media: MediaFiles,
) : ViewModel() {
    private data class LocalState(
        val bunnyName: String = "",
        val importing: ImportProgress? = null,
        val result: ImportResult? = null,
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PhotoGalleryUiState> =
        // Kotlin note: `combine` re-runs whenever *either* source emits and hands you the latest of
        // both — RxJS's combineLatest. `stateIn` then turns the cold Flow into a hot StateFlow with
        // a value the screen can read immediately, kept alive 5s past the last collector so a
        // rotation does not re-query.
        combine(photos.photos(bunnyId), local) { rows, local ->
            PhotoGalleryUiState(
                loading = false,
                bunnyName = local.bunnyName,
                photos = rows.map(::toGalleryPhoto),
                importing = local.importing,
                result = local.result,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoGalleryUiState())

    init {
        viewModelScope.launch {
            val name = bunnies.bunnyNow(bunnyId)?.name.orEmpty()
            local.update { it.copy(bunnyName = name) }
        }
    }

    /**
     * Imports a whole selection **one at a time**, committing each photo as it lands.
     *
     * Sequential rather than parallel on purpose: each one decodes a full-resolution bitmap on its
     * way through the media helper, and twenty of those at once is how a photo import turns into an
     * out-of-memory kill. It also makes the progress count mean something.
     *
     * An unreadable file is skipped, counted and reported **once at the end**. Twenty toasts for a
     * camera roll with two bad files is worse than the two bad files.
     */
    fun addAll(sources: List<Uri>) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(importing = ImportProgress(done = 0, total = sources.size), result = null) }
            var added = 0
            var unreadable = 0
            sources.forEachIndexed { index, source ->
                try {
                    photos.add(bunnyId, source)
                    added++
                } catch (cancelled: CancellationException) {
                    // Kotlin note: leaving the screen cancels this coroutine by *throwing* here.
                    // Catching Exception without rethrowing this one would turn "the owner left"
                    // into "that photo could not be read" — and `runCatching` swallows it silently,
                    // which is why this is a hand-written try/catch.
                    throw cancelled
                } catch (failed: Exception) {
                    // Counted for the owner, logged for us. Swallowing it entirely made a real
                    // import failure indistinguishable from a corrupt file — the count says one
                    // photo could not be read, and nothing anywhere says why.
                    Log.w(IMPORT_LOG_TAG, "Could not import $source", failed)
                    unreadable++
                }
                local.update { it.copy(importing = ImportProgress(done = index + 1, total = sources.size)) }
            }
            local.update { it.copy(importing = null, result = ImportResult(added, unreadable)) }
        }
    }

    fun setCaption(
        id: String,
        caption: String,
    ) {
        viewModelScope.launch { photos.setCaption(id, caption) }
    }

    fun delete(id: String) {
        viewModelScope.launch { photos.delete(id) }
    }

    /** The screen has shown the import result; it must not come back on the next recomposition. */
    fun resultShown() {
        local.update { it.copy(result = null) }
    }

    private fun toGalleryPhoto(photo: PhotoEntity) =
        GalleryPhoto(
            id = photo.id,
            file = media.resolve(photo.path),
            caption = photo.caption,
            takenAt = photo.capturedAt ?: photo.createdAt,
            dated = photo.capturedAt != null,
        )

    companion object {
        /** A factory *function*, because the navigation key carries the bunny id. */
        fun factory(bunnyId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    PhotoGalleryViewModel(
                        bunnyId = bunnyId,
                        photos = app.container.photoRepository,
                        bunnies = app.container.bunnyRepository,
                        media = app.container.mediaFiles,
                    )
                }
            }
    }
}
