package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The launch decision as a truth table** — the test that did not exist when it mattered.
 *
 * Every proof this project had of `MIGRATION_6_7` opened the database *directly*: the
 * `MigrationTestHelper` runs, the release-shaped open, the three committed backup archives. All of
 * them are true and none of them touches the gate in front of Room, which is where an owner updating
 * from Play actually arrives — and where a mismatch was being treated as a refusal even when a
 * registered migration could walk it. Watched on the phone at 7.5: a real schema-6 database, a
 * release-shaped build over the top, and a screen saying the records could not be read.
 */
class SchemaGateTest {
    /** What this build really ships, so the table below cannot drift from the registered set. */
    private val shipped = BUNNY_MIGRATION_STEPS

    @Test
    fun `the shipped migrations walk every schema a released build ever wrote`() {
        // 1.0.1 wrote 4, 1.1.0 wrote 5, 1.2.0 through 1.4.0 wrote 6, 1.5.0 onwards wrote 7. Each
        // has to reach 8, and the oldest of them by walking four migrations in a row.
        assertTrue("4 → 8", migrationPathExists(4, 8, shipped))
        assertTrue("5 → 8", migrationPathExists(5, 8, shipped))
        assertTrue("6 → 8", migrationPathExists(6, 8, shipped))
        assertTrue("7 → 8", migrationPathExists(7, 8, shipped))
    }

    @Test
    fun `a version nothing was ever written from is not reachable`() {
        // The disposable era: 1, 2 and 3 were wiped rather than migrated, and no migration starts there.
        assertFalse("3 → 8", migrationPathExists(3, 8, shipped))
    }

    @Test
    fun `no path runs backwards, however many steps exist forwards`() {
        assertFalse("a downgrade", migrationPathExists(8, 7, shipped))
        assertFalse("nowhere to go", migrationPathExists(8, 8, shipped))
    }

    @Test
    fun `a step that skips a version counts, because Room would take it`() {
        assertTrue("4 → 6 in one jump", migrationPathExists(4, 6, listOf(4 to 6)))
        // …but never past the target: a 4 → 8 migration cannot land a file on a build that is at 7.
        assertFalse("overshooting", migrationPathExists(4, 8, listOf(4 to 9)))
    }

    @Test
    fun `an ordinary launch opens, with or without migrations`() {
        assertEquals(
            "a fresh install has no file",
            SchemaGate.Open,
            schemaGateDecision(onDiskVersion = 0, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
        assertEquals(
            "already at this build's shape",
            SchemaGate.Open,
            schemaGateDecision(onDiskVersion = 8, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
    }

    @Test
    fun `an owner updating across a schema bump is let in, and Room migrates`() {
        assertEquals(
            "1.8.0's database under 1.9 — the upgrade every existing owner is about to take",
            SchemaGate.Open,
            schemaGateDecision(onDiskVersion = 7, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
        assertEquals(
            "the case that shipped broken: 1.4.0's database, now two bumps behind",
            SchemaGate.Open,
            schemaGateDecision(onDiskVersion = 6, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
        assertEquals(
            "and the skipped-version upgrade, from a phone that never took 1.1 or 1.2",
            SchemaGate.Open,
            schemaGateDecision(onDiskVersion = 4, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
    }

    @Test
    fun `a release build still refuses what it genuinely cannot read`() {
        assertEquals(
            "no migration starts at 3",
            SchemaGate.Refuse,
            schemaGateDecision(onDiskVersion = 3, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
        assertEquals(
            "a file from a newer Binky, which no migration runs backwards to reach",
            SchemaGate.Refuse,
            schemaGateDecision(onDiskVersion = 9, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false),
        )
    }

    /**
     * The same decision is what background entry points ask before touching anything
     * (`schemaBlocksBackgroundWork`), so the three answers are worth stating in those terms.
     *
     * `Open` is the one that changed. A worker or receiver used to sit out *any* mismatch, including
     * an upgrade the next database open was about to perform anyway — which is what left the first
     * dose after an update unposted and the alarm chain unarmed.
     */
    @Test
    fun `background work is blocked by exactly the decisions that are not Open`() {
        val migratableUpgrade =
            schemaGateDecision(onDiskVersion = 6, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false)
        val debugWipe =
            schemaGateDecision(onDiskVersion = 6, appSchemaVersion = 8, steps = shipped, destructiveAllowed = true)
        val unreadable =
            schemaGateDecision(onDiskVersion = 3, appSchemaVersion = 8, steps = shipped, destructiveAllowed = false)

        assertEquals("a worker may run through an upgrade it can migrate", SchemaGate.Open, migratableUpgrade)
        assertTrue("but never through a wipe with nobody looking", debugWipe != SchemaGate.Open)
        assertTrue("and never over a file it cannot read", unreadable != SchemaGate.Open)
    }

    /**
     * The branch order in `schemaGateDecision`, stated as its own claim.
     *
     * A debug build registers **no** migrations — it takes the destructive fallback instead, and Room
     * prefers a registered migration over the fallback, so a build cannot have both (ADR-0023's
     * Phase 4b amendment). A gate that asked "is there a path?" before "does this build wipe?" would
     * send a debug build straight past the consent screen and let Room empty the file in silence.
     */
    @Test
    fun `a debug build still consents before it wipes, even where a migration exists on paper`() {
        assertEquals(
            SchemaGate.Consent,
            schemaGateDecision(onDiskVersion = 6, appSchemaVersion = 8, steps = shipped, destructiveAllowed = true),
        )
        assertEquals(
            "and where one does not",
            SchemaGate.Consent,
            schemaGateDecision(onDiskVersion = 3, appSchemaVersion = 8, steps = shipped, destructiveAllowed = true),
        )
        assertEquals(
            "but an untouched file is still just a launch",
            SchemaGate.Open,
            schemaGateDecision(onDiskVersion = 8, appSchemaVersion = 8, steps = shipped, destructiveAllowed = true),
        )
    }
}
