package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind

/**
 * What a manual export carries (ADR-0005).
 *
 * A scope is **a list of [MediaKind]** plus the two fixed members — the database and the
 * preferences — which is what ADR-0020 gave the enum a `directory` for: the scope table is a list of
 * kinds, never a list of magic strings, so a kind added later joins a scope by appearing in one line
 * here rather than by someone remembering a directory name in four places.
 *
 * **Preferences ride in every scope, from [Essential] upward.** They are a few hundred bytes, Auto
 * Backup already carries them, and their absence does not read as missing data — it reads as bugs: a
 * restored phone showing kilograms when the owner chose grams, landing on the wrong bunny, and
 * defaulting its next export to a scope the owner never picked.
 *
 * Kotlin note: enum entries carry constructor arguments, so this is a small lookup table rather than
 * the bare string constants a JS enum would give you.
 */
enum class BackupScope(
    val mediaKinds: List<MediaKind>,
) {
    /** Database, preferences, and bunny avatars — the smallest thing worth calling a backup. */
    Essential(listOf(MediaKind.Avatar)),

    /**
     * The default. [Essential] plus scanned documents and tray photos: everything the owner may need
     * *again*, which is the distinction ADR-0017 draws between a record and a memory.
     *
     * A tray photo sits here rather than in [Everything] because it is evidence rather than a
     * snapshot (ADR-0029) — the thing an owner hands a vet about a gut problem that has already
     * resolved by the appointment, and the gallery tier is the one an owner may never pick.
     */
    Records(listOf(MediaKind.Avatar, MediaKind.Document, MediaKind.Observation)),

    /**
     * [Records] plus the photo gallery — large, occasional, and **the only place photos are
     * protected at all** (ADR-0005). Auto Backup excludes them, Essential and Records exclude them,
     * so an owner who never picks this scope has no copy of their gallery anywhere. That gap is
     * accepted, and the app says so in words rather than leaving it to be discovered.
     */
    Everything(listOf(MediaKind.Avatar, MediaKind.Document, MediaKind.Observation, MediaKind.Photo)),
    ;

    /** Whether this scope's archive is authoritative about [kind] — see `planMediaMerge`. */
    fun carries(kind: MediaKind): Boolean = kind in mediaKinds

    /**
     * The scope as it appears in an export's filename, which is for **humans only**. Restore reads
     * the manifest inside the zip instead: a filename is the one part of a file an owner can
     * trivially change, and a promise must not be sourced from the outside of the envelope.
     */
    val slug: String get() = name.lowercase()
}
