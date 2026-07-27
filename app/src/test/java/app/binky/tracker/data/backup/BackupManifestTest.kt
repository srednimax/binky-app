package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The manifest is **the authority for what an archive contains** (ADR-0005), so what it survives
 * matters as much as what it says: it is read on the far side by a build that may be older or newer
 * than the one that wrote it.
 */
class BackupManifestTest {
    private val manifest =
        BackupManifest(
            scope = BackupScope.Records,
            schemaVersion = 4,
            createdAtEpochMilli = 1_800_000_000_000,
            mediaCounts = mapOf(MediaKind.Avatar.name to 2, MediaKind.Document.name to 7),
        )

    @Test
    fun aManifestSurvivesTheRoundTrip() {
        assertEquals(manifest, decodeManifest(encodeManifest(manifest)))
    }

    /** Written by name, never by ordinal — the same rule the database's converters follow. */
    @Test
    fun theScopeIsWrittenByName() {
        assertEquals(true, encodeManifest(manifest).contains("\"Records\""))
    }

    /**
     * `format` equals its default in every archive this build writes, and without `encodeDefaults`
     * it would be omitted from all of them — leaving a future build unable to tell an old archive
     * from a malformed one.
     */
    @Test
    fun theFormatVersionIsAlwaysWritten() {
        assertEquals(true, encodeManifest(manifest).contains("\"format\""))
    }

    /** A field added in a later version must not make the archive unreadable by this one. */
    @Test
    fun anUnknownFieldIsIgnored() {
        val withExtra =
            """
            { "format": 1, "scope": "Essential", "schemaVersion": 4,
              "createdAtEpochMilli": 1, "mediaCounts": {}, "somethingFrom1_4": true }
            """.trimIndent()

        assertEquals(BackupScope.Essential, decodeManifest(withExtra)?.scope)
    }

    /**
     * A scope this build has never heard of is not a manifest it can make a promise about — and
     * "this file is not a Binky backup" is the only honest thing to say about it.
     */
    @Test
    fun anUnknownScopeIsNotAManifest() {
        val future = """{ "format": 1, "scope": "Photos", "schemaVersion": 4, "createdAtEpochMilli": 1 }"""
        assertNull(decodeManifest(future))
    }

    @Test
    fun textThatIsNotJsonIsNotAManifest() {
        assertNull(decodeManifest("not a backup"))
        assertNull(decodeManifest(""))
    }

    @Test
    fun aMissingCountReadsAsZeroRatherThanFailing() {
        assertEquals(0, manifest.countFor(MediaKind.Photo))
        assertEquals(7, manifest.countFor(MediaKind.Document))
    }
}
