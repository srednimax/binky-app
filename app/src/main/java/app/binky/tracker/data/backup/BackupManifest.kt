package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/** The manifest's own entry name inside the zip. */
const val BACKUP_MANIFEST_ENTRY = "manifest.json"

/**
 * The archive layout's version, bumped only if the *shape* of a zip changes — not when the database
 * schema moves, which [BackupManifest.schemaVersion] carries separately. A 1.0 archive has to stay
 * restorable into 1.2, so the two version numbers are deliberately not one number.
 */
const val BACKUP_FORMAT_VERSION = 1

/**
 * What an archive says about itself, and **the authority for what it contains** (ADR-0005).
 *
 * The scope also goes in the filename for humans, but the confirmation dialog reads *this*: a
 * filename is the one part of a file the owner can trivially change, and restore is the most
 * destructive thing the app does — its promise must not be sourced from the outside of the envelope.
 *
 * Kotlin note: `@Serializable` generates the encoder/decoder at compile time rather than reflecting
 * at runtime, so this class is the schema. Enums serialise **by name**, which is the same rule the
 * database's type converters follow, for the same reason: adding a scope must not rewrite what
 * older archives claim to be.
 */
@Serializable
data class BackupManifest(
    /** The archive layout, not the database schema. See [BACKUP_FORMAT_VERSION]. */
    val format: Int = BACKUP_FORMAT_VERSION,
    val scope: BackupScope,
    /** The Room schema the database inside was written at. Restore refuses anything newer. */
    val schemaVersion: Int,
    /**
     * Stored as epoch millis rather than an ISO string: `Instant` has no built-in serialiser here,
     * and a number cannot be ambiguous about its time zone the way a hand-formatted string can.
     */
    val createdAtEpochMilli: Long,
    /**
     * How many files the archive holds per kind, keyed by [MediaKind.name].
     *
     * Keyed by name rather than by the enum so a kind this build does not know about — a Phase-6
     * archive read by 1.0 — decodes as an unknown key instead of failing the whole manifest.
     */
    val mediaCounts: Map<String, Int> = emptyMap(),
) {
    val createdAt: Instant get() = Instant.ofEpochMilli(createdAtEpochMilli)

    fun countFor(kind: MediaKind): Int = mediaCounts[kind.name] ?: 0
}

/**
 * `ignoreUnknownKeys` is the forward-compatibility half: a field added in 1.1 must not make an
 * archive unreadable by the build that is trying to restore it. `encodeDefaults` is the other half —
 * without it [BackupManifest.format] would be omitted whenever it equalled its default, which is
 * every archive this build writes.
 */
private val manifestJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

fun encodeManifest(manifest: BackupManifest): String = manifestJson.encodeToString(manifest)

/**
 * Reads a manifest, or returns null for anything that is not one.
 *
 * Null covers a missing field, a scope name this build has never heard of, and text that is not
 * JSON at all — all of which mean the same thing to the owner: *"this file is not a Binky backup"*.
 * There is nothing useful to say about *which* way it was malformed.
 */
fun decodeManifest(text: String): BackupManifest? =
    try {
        manifestJson.decodeFromString<BackupManifest>(text)
    } catch (e: SerializationException) {
        null
    }
