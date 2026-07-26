package app.bunny.tracker.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * How a kind of image is reduced before it is written.
 *
 * Kotlin note: a `sealed interface` is this language's discriminated union — the compiler knows the
 * full set of implementations, so a `when` over it needs no `else` branch and adding a variant turns
 * every incomplete `when` into a compile error rather than a silent runtime fallthrough.
 */
sealed interface DownsampleSpec {
    /** JPEG quality, 0-100. */
    val quality: Int

    /**
     * Crop the largest centred square, then cap its edge at [size]. Blind — there is no
     * crop-and-zoom UI; re-picking the photo is the workaround (ADR-0020).
     */
    data class CentreCropSquare(
        val size: Int,
        override val quality: Int,
    ) : DownsampleSpec

    /** Keep the aspect ratio and cap the longer edge at [maxEdge]. */
    data class LongEdge(
        val maxEdge: Int,
        override val quality: Int,
    ) : DownsampleSpec
}

/**
 * What is being stored. The kind selects **both** the subdirectory and the downsample spec, because
 * the three have genuinely different needs — an avatar wants a small square, a gallery photo a large
 * long-edge cap, and a document downsampled to gallery dimensions can make small print unreadable
 * (ADR-0020).
 *
 * The directory doubles as the export scope: ADR-0005's backup scopes are a list of [MediaKind],
 * not a list of magic strings.
 *
 * Phase 1 exercises [Avatar] only. [Photo] and [Document] have their spec here so Phase 3 and
 * Phase 5 extend this table rather than fork the pipeline, but their numbers are unverified until
 * the phase that ships them.
 *
 * Kotlin note: enum entries can carry constructor arguments, so this is a small lookup table rather
 * than the bare string constants a JS enum would give you.
 */
enum class MediaKind(
    val directory: String,
    val spec: DownsampleSpec,
) {
    /** 512² JPEG q85 — renders small and circular everywhere, so it is cropped once at write time. */
    Avatar("avatars", DownsampleSpec.CentreCropSquare(size = 512, quality = 85)),

    /** Phase 3. Large enough to fill a phone screen in the full-screen pager. */
    Photo("photos", DownsampleSpec.LongEdge(maxEdge = 2048, quality = 85)),

    /** Phase 5. Kept big and near-lossless: a vet may need to read small print off it (ADR-0017). */
    Document("documents", DownsampleSpec.LongEdge(maxEdge = 3000, quality = 92)),
}

/**
 * The single path for persisting images (house rule). Every write goes through [persist], which
 * downsamples and re-encodes per kind — bypassing it puts full-resolution bitmaps in memory and
 * blows up the photo grid.
 *
 * Paths returned are **relative** (`avatars/<uuid>.jpg`) and resolved against [root] at read time.
 * Absolute paths change across installs and break restored backups.
 *
 * @param root where the kind directories live. Defaults to `filesDir`; tests point it at a temp dir.
 */
class MediaFiles(
    context: Context,
    private val root: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context) : this(context, context.applicationContext.filesDir)

    private val appContext = context.applicationContext

    /**
     * Read [source], bake in its EXIF orientation, downsample per [kind], strip all metadata, and
     * write it. Returns the relative path to store on the row.
     *
     * **The file is written before the row** (ADR-0020) — that is the caller's half of the deal:
     * call this first, then write the path. A crash in between leaves an invisible orphan file;
     * the other order leaves a dangling path, which to the owner looks exactly like losing their
     * bunny's photo.
     *
     * Kotlin note: `suspend` is not `async` — calling this does not start anything in the
     * background and there is no promise to await. It runs on the caller's coroutine, and the
     * `withContext` below is what moves the decode and the disk write off the main thread.
     */
    suspend fun persist(
        source: Uri,
        kind: MediaKind,
    ): String =
        withContext(io) {
            val decoded = decodeUpright(source, kind.spec)
            val reduced = kind.spec.reduce(decoded)
            if (reduced !== decoded) decoded.recycle()

            val relativePath = "${kind.directory}/${UUID.randomUUID()}.jpg"
            val target = File(root, relativePath)
            target.parentFile?.mkdirs()
            try {
                target.outputStream().use { out ->
                    // Bitmap.compress writes pixels only, so the EXIF we just applied — along with
                    // the GPS coordinates a camera stamps on every shot — does not survive into the
                    // file. That is deliberate: this app has no business copying the owner's home
                    // location into a backup that leaves the device (ADR-0020).
                    if (!reduced.compress(Bitmap.CompressFormat.JPEG, kind.spec.quality, out)) {
                        throw IOException("Could not encode $source as JPEG")
                    }
                }
            } catch (e: IOException) {
                // A half-written file would render as a broken image if a row ever pointed at it.
                target.delete()
                throw e
            } finally {
                reduced.recycle()
            }
            relativePath
        }

    /** Absolute location of a stored relative path. The file may legitimately be missing. */
    fun resolve(relativePath: String): File = File(root, relativePath)

    /**
     * Best-effort removal, for the delete-old half of a replace and for cascading a deleted row
     * (ADR-0020). Failure is not an error worth surfacing: the record is already gone, which is
     * what the owner asked for.
     */
    fun delete(relativePath: String): Boolean = File(root, relativePath).delete()

    /** The directory holding one kind — ADR-0005's export scopes are built from these. */
    fun directoryFor(kind: MediaKind): File = File(root, kind.directory)

    /**
     * Decode at roughly the size we need, with the camera's orientation tag applied to the pixels.
     *
     * Cameras commonly leave pixels unrotated and record an orientation tag instead. Coil honours
     * that tag on an untouched file, but re-encoding discards the tag and keeps the pixels
     * sideways — so camera-taken avatars come out rotated while album-picked ones do not, unless
     * the rotation is baked in here (ADR-0020).
     */
    private fun decodeUpright(
        source: Uri,
        spec: DownsampleSpec,
    ): Bitmap {
        val bounds =
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                openStream(source).use { BitmapFactory.decodeStream(it, null, options) }
            }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("$source is not a decodable image")
        }

        val orientation =
            openStream(source).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, spec)
            }
        val decoded =
            openStream(source).use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("Could not decode $source")

        val upright = decoded.oriented(orientation)
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private fun openStream(uri: Uri): InputStream =
        appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
}

/**
 * The power-of-two subsample that gets us closest to the target without going under it —
 * decoding a 12 MP camera shot at full size just to shrink it is how you run out of memory.
 *
 * Rotation is irrelevant here: it swaps width and height, and both `min` and `max` are unchanged
 * by that swap.
 */
private fun sampleSizeFor(
    width: Int,
    height: Int,
    spec: DownsampleSpec,
): Int {
    val (available, wanted) =
        when (spec) {
            is DownsampleSpec.CentreCropSquare -> minOf(width, height) to spec.size
            is DownsampleSpec.LongEdge -> maxOf(width, height) to spec.maxEdge
        }
    var sample = 1
    while (available / (sample * 2) >= wanted) sample *= 2
    return sample
}

/**
 * Crop and scale per the spec. Never upscales — a 300 px album thumbnail is stored at 300², not
 * blown up to 512² with invented pixels and the file size to match.
 */
private fun DownsampleSpec.reduce(source: Bitmap): Bitmap =
    when (this) {
        is DownsampleSpec.CentreCropSquare -> {
            val edge = minOf(source.width, source.height)
            val square =
                Bitmap.createBitmap(
                    source,
                    (source.width - edge) / 2,
                    (source.height - edge) / 2,
                    edge,
                    edge,
                )
            val target = minOf(size, edge)
            if (target == edge) {
                square
            } else {
                square.scale(target, target).also {
                    if (it !== square) square.recycle()
                }
            }
        }

        is DownsampleSpec.LongEdge -> {
            val longest = maxOf(source.width, source.height)
            if (longest <= maxEdge) {
                source
            } else {
                val ratio = maxEdge.toDouble() / longest
                source.scale(
                    (source.width * ratio).toInt().coerceAtLeast(1),
                    (source.height * ratio).toInt().coerceAtLeast(1),
                )
            }
        }
    }

/** Apply an EXIF orientation tag to the pixels. Returns the receiver when there is nothing to do. */
private fun Bitmap.oriented(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
