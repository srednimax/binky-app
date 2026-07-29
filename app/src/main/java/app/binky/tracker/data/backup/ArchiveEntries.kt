package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind

/** The database's entry name inside the zip. Fixed — restore matches it exactly. */
const val BACKUP_DATABASE_ENTRY = "database/bunny.db"

/**
 * The preferences file's entry name inside the zip.
 *
 * DataStore writes a protobuf, not JSON, and the `.preferences_pb` name is DataStore's own — kept
 * verbatim so the file that comes out of an archive is the file that goes back into place, with
 * nothing in between reinterpreting it.
 */
const val BACKUP_PREFERENCES_ENTRY = "preferences/bunny_preferences.preferences_pb"

/**
 * Where that same file lives on disk, relative to `filesDir`. `preferencesDataStore(name = …)` puts
 * its file under a `datastore/` directory it owns, so this mirrors DataStore's own layout rather
 * than choosing one.
 */
const val PREFERENCES_FILE_PATH = "datastore/bunny_preferences.preferences_pb"

/**
 * One entry a restore is willing to read. Everything else in an archive is ignored.
 *
 * Kotlin note: a `sealed interface` is this language's discriminated union — the compiler knows every
 * implementation, so a `when` over it needs no `else` and adding a member turns incomplete branches
 * into compile errors rather than silent fallthrough.
 */
sealed interface ArchiveEntry {
    data object Manifest : ArchiveEntry

    data object Database : ArchiveEntry

    data object Preferences : ArchiveEntry

    /**
     * A media file, with its kind already resolved and its relative path already proven to be
     * `<kind.directory>/<uuid>.jpg`. [relativePath] is safe to join onto `filesDir` because it was
     * *matched*, not sanitised.
     */
    data class Media(
        val kind: MediaKind,
        val relativePath: String,
    ) : ArchiveEntry
}

/**
 * A media filename as this app writes them: a canonical UUID and nothing else.
 *
 * Deliberately stricter than `UUID.fromString`, which happily parses `1-1-1-1-1` and would let a
 * hand-edited archive introduce filenames this app would never have produced.
 */
private val MEDIA_FILE_NAME =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg")

/**
 * Whether [name] is trying to climb out of the directory it would be joined onto.
 *
 * Separate from [archiveEntryFor] because the two answers are different in kind. An unrecognised
 * name is **skipped** — an archive from a later version carrying entries this build has never heard
 * of must still restore — whereas a traversal means the file is hostile, and a hostile file is
 * refused whole.
 *
 * This exists because the refusal was, until now, **borrowed from the platform**. From Android 14
 * `ZipInputStream.getNextEntry` validates entry names itself and throws, which is what made the
 * archive refusable. Below 14 there is no such validator: the name came through, the allowlist above
 * skipped it, and the restore carried on and applied the rest of the archive. Nothing escaped —
 * that part was never in doubt, and is what the allowlist is for — but "restored, minus the part we
 * quietly dropped" is the wrong answer to a file that tried this, and it was the answer on API 26
 * through 33, which is most of the range this app supports.
 *
 * Backslashes are folded first. Android does not treat `\` as a separator, so `..\x` is a harmless
 * one-segment filename here — but it costs nothing to refuse, and a rule that reads "no `..`
 * anywhere" is one the next reader can hold in their head.
 */
fun isPathTraversal(name: String): Boolean {
    val separated = name.replace('\\', '/')
    return separated.startsWith("/") || separated.split('/').any { it == ".." }
}

/**
 * The entry [name] describes, or null for anything a restore will not touch.
 *
 * **Restore never builds a path out of archive input** (ADR-0005). This is an allowlist: three exact
 * names, plus `<kind.directory>/<uuid>.jpg` with *both* halves validated against values this app
 * itself produces. A `../` traversal, an absolute path, a nested directory and a filename that is
 * not a uuid all fail to match, so they are defeated **by construction** rather than by sanitising
 * after the fact — there is no code path here that concatenates an attacker-chosen string onto a
 * directory and hopes.
 *
 * The threat is mild, since the file is normally the owner's own. But backups travel by mail and
 * messenger, and an arbitrary write into app-private storage is not something to leave open in an
 * app holding an animal's medical history.
 */
fun archiveEntryFor(name: String): ArchiveEntry? {
    when (name) {
        BACKUP_MANIFEST_ENTRY -> return ArchiveEntry.Manifest
        BACKUP_DATABASE_ENTRY -> return ArchiveEntry.Database
        BACKUP_PREFERENCES_ENTRY -> return ArchiveEntry.Preferences
    }

    // Exactly two segments, or it is not one of ours. This is what rejects "../secrets",
    // "/avatars/x.jpg" (whose leading slash makes an empty first segment) and "avatars/a/b.jpg"
    // without any of them being special-cased.
    val segments = name.split('/')
    if (segments.size != 2) return null

    val (directory, fileName) = segments
    val kind = MediaKind.entries.firstOrNull { it.directory == directory } ?: return null
    if (!MEDIA_FILE_NAME.matches(fileName)) return null

    return ArchiveEntry.Media(kind = kind, relativePath = name)
}
