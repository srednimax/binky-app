package app.binky.tracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Checkpoint 2e's symptom vocabulary: the seed, the reconciliation, and the duplicate check the schema
 * deliberately cannot make.
 *
 * Instrumented because the claims are about SQLite's behaviour — that the unique index on a nullable
 * `key` gives `INSERT OR IGNORE` something to ignore while still permitting unlimited owner rows, and
 * that the join from an observation resolves a symptom that has since been retired.
 */
@RunWith(AndroidJUnit4::class)
class SymptomRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var symptoms: SymptomRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        symptoms = SymptomRepository(database)
    }

    @After
    fun tearDown() = database.close()

    /** The built-in labels as the picker would resolve them — the shape the real caller passes in. */
    private fun builtInLabels(): Map<String, String> =
        mapOf(
            "head_tilt" to "Head tilt",
            "loud_teeth_grinding" to "Loud teeth grinding",
        )

    @Test
    fun theSeedLandsTheBuiltInListOnCreate() =
        runTest {
            assertEquals(BUILT_IN_SYMPTOM_KEYS.size, database.countRows("symptoms"))
            // Keys, not English strings: the label resolves through `strings.xml` so it translates like
            // all other UI text, and history keys off the stable id (ADR-0010, ADR-0013).
            assertEquals(
                BUILT_IN_SYMPTOM_KEYS.toSet(),
                database
                    .symptomDao()
                    .allNow()
                    .mapNotNull { it.key }
                    .toSet(),
            )
            assertEquals(0, database.symptomDao().allNow().count { it.label != null })
        }

    @Test
    fun openingTheDatabaseAgainDoesNotDoubleTheList() =
        runTest {
            // A real file, because this is the one claim in-memory cannot make: the process opening a
            // database that already holds the seed. Without the unique index on `key` the reconciliation
            // would insert the entire built-in list on every launch (ADR-0010).
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "symptom-seed-${UUID.randomUUID()}.db"
            try {
                val first = buildBunnyDatabase(context, name)
                first.openHelper.writableDatabase
                assertEquals(BUILT_IN_SYMPTOM_KEYS.size, first.countRows("symptoms"))
                first.close()

                val second = buildBunnyDatabase(context, name)
                second.openHelper.writableDatabase
                assertEquals(BUILT_IN_SYMPTOM_KEYS.size, second.countRows("symptoms"))
                second.close()
            } finally {
                for (suffix in listOf("", "-wal", "-shm")) {
                    context.getDatabasePath(name + suffix).delete()
                }
            }
        }

    @Test
    fun reconcilingTopsUpAMissingBuiltInWithoutResurrectingAHiddenOne() =
        runTest {
            val seeded = database.symptomDao().allNow()
            val retired = seeded.first()
            val missing = seeded[1]
            symptoms.hide(retired.id)

            // A built-in absent from the table stands in for the two ways that really happens: ADR-0007's
            // wipe, which drops the table and lets Room recreate it empty — `onCreate` does not fire on
            // that path, which is why the seed hangs on `onOpen` — and a build that adds a new key.
            database.openHelper.writableDatabase
                .execSQL("DELETE FROM symptoms WHERE `key` = ?", arrayOf(missing.key))

            builtInSymptomSeedCallback().onOpen(database.openHelper.writableDatabase)

            // Topped up to exactly the built-in list, not the built-in list again on top of itself.
            assertEquals(BUILT_IN_SYMPTOM_KEYS.size, database.countRows("symptoms"))
            // And matching on `key` left the retired one retired: an owner who hid "limping" does not
            // get it back at the next launch.
            assertNotNull(database.symptomDao().symptomNow(retired.id)!!.hiddenAt)
        }

    @Test
    fun addingALabelThatMatchesABuiltInSelectsItRatherThanCreatingARow() =
        runTest {
            val before = database.countRows("symptoms")

            // Nothing in the schema can catch this, because built-in labels are deliberately not stored:
            // there is no column holding "Head tilt" for an index to collide with (ADR-0010).
            val id = symptoms.add("  head TILT ", builtInLabels())

            assertEquals(before, database.countRows("symptoms"))
            assertEquals("head_tilt", database.symptomDao().symptomNow(id)!!.key)
        }

    @Test
    fun addingALabelThatMatchesAnOwnerSymptomSelectsTheExistingOne() =
        runTest {
            val first = symptoms.add("Chin rubbing", builtInLabels())
            val before = database.countRows("symptoms")

            val second = symptoms.add("chin RUBBING", builtInLabels())

            assertEquals(first, second)
            assertEquals(before, database.countRows("symptoms"))
        }

    @Test
    fun addingALabelThatMatchesAHiddenSymptomUnhidesIt() =
        runTest {
            val id = symptoms.add("Chin rubbing", builtInLabels())
            symptoms.hide(id)

            val again = symptoms.add("Chin rubbing", builtInLabels())

            // An owner typing in a symptom they previously retired is asking for it back; the alternative
            // is a duplicate shadow with the history attached to the wrong one of the pair.
            assertEquals(id, again)
            assertNull(database.symptomDao().symptomNow(id)!!.hiddenAt)
        }

    @Test
    fun anOwnerAddedSymptomStoresItsLiteralTextAndNoKey() =
        runTest {
            val id = symptoms.add(" Chin rubbing ", builtInLabels())

            val added = database.symptomDao().symptomNow(id)!!
            assertEquals("Chin rubbing", added.label)
            // `key == null` *is* the built-in/owner distinction — there is deliberately no
            // `ownerCreated` flag to drift out of step with it (ADR-0010).
            assertNull(added.key)
        }

    @Test
    fun aHiddenSymptomStillResolvesOnAnOldObservation() =
        runTest {
            val bunny = BunnyEntity(name = "Bijou")
            database.bunnyDao().insert(bunny)
            val symptomId = symptoms.add("Chin rubbing", builtInLabels())
            val observationId =
                ObservationRepository(database, temporaryMedia())
                    .add(
                        listOf(bunny.id),
                        Instant.parse("2026-03-04T08:30:00Z"),
                        ObservationFacts(individual = IndividualFacts(symptomIds = setOf(symptomId))),
                    ).single()

            symptoms.hide(symptomId)

            // Removing a symptom hides it from the picker and never deletes it from historical
            // observations (ADR-0010) — so the read that renders an old observation must not filter on
            // `hiddenAt`, and the join table has no cascade from the symptom side.
            assertEquals(listOf(symptomId), database.symptomDao().symptomsForNow(observationId).map { it.id })
        }

    @Test
    fun aRetiredSymptomLeavesThePickersList() =
        runTest {
            val id = symptoms.add("Chin rubbing", builtInLabels())
            symptoms.hide(id)

            val visible = database.symptomDao().allNow().filter { it.hiddenAt == null }
            assertEquals(BUILT_IN_SYMPTOM_KEYS.size, visible.size)
        }

    @Test
    fun aBlankLabelIsRefused() =
        runTest {
            val threw =
                runCatching { symptoms.add("   ", builtInLabels()) }
                    .exceptionOrNull()
            assertNotNull(threw)
        }
}
