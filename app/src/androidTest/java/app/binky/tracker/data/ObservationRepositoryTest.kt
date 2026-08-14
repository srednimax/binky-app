package app.binky.tracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Checkpoint 2e's observation data layer: the shared write, the tray/individual split, and the
 * distinction ADR-0008 turns on — **deleting a bunny preserves history, correcting the participants
 * amends it**.
 *
 * Instrumented because most of these are claims about SQLite: a cascade the ORM is only *asked* for is
 * not a cascade, and `recordCounts`' survivorship buckets are a correlated `EXISTS` that only the real
 * engine can settle. The healthy-day field set is a JVM test, since it is arithmetic-free data.
 */
@RunWith(AndroidJUnit4::class)
class ObservationRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var media: MediaFiles
    private lateinit var observations: ObservationRepository

    private val noticed: Instant = Instant.parse("2026-03-04T08:30:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        media = temporaryMedia()
        observations = ObservationRepository(database, media)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addBunny(
        name: String,
        archived: Boolean = false,
    ): String {
        val bunny = BunnyEntity(name = name, archivedAt = if (archived) noticed else null)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    /**
     * A tray the owner actually looked at, so an assertion on it cannot pass by everything being null.
     *
     * **Two appearance values**, which is the case ADR-0029 exists for and the one that would pass
     * silently with a single-valued field: round pellets *and* soft ones is the commonest early sign
     * of a gut going wrong, and before schema 7 the owner had to pick one and file the rest as prose.
     */
    private val worryingTray =
        TrayFacts(
            droppingsAmount = DroppingsAmount.FEW,
            droppingsSizes = setOf(DroppingsSize.SMALL),
            droppingsAppearance = setOf(DroppingsAppearance.ROUND, DroppingsAppearance.SOFT),
            cecotropes = Cecotropes.LEFT_UNEATEN,
        )

    /** Any seeded built-in will do — which symptom it is never matters to these tests, only its id. */
    private suspend fun anySymptomId(): String =
        database
            .symptomDao()
            .allNow()
            .first()
            .id

    @Test
    fun aSharedWriteLandsOneGroupIdAndIdenticalTrayFactsOnEveryParticipant() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")

            val ids = observations.add(listOf(bijou, nugget), noticed, ObservationFacts(tray = worryingTray))

            assertEquals(2, ids.size)
            val rows = ids.mapNotNull { observations.observationNow(it) }
            // One group id, shared by both rows — not one each, which would make every row solo.
            assertEquals(1, rows.mapNotNull { it.groupId }.distinct().size)
            assertEquals(2, rows.count { it.groupId != null })
            // Identical tray facts by construction: one tray, one real-world fact (ADR-0008) —
            // and since schema 7 that includes the join rows, which are written per participant
            // rather than carried by a `copy()` (ADR-0029).
            assertEquals(setOf(worryingTray), rows.map { observations.trayFactsNow(it.id) }.toSet())
            assertEquals(setOf(bijou, nugget), rows.map { it.bunnyId }.toSet())
        }

    @Test
    fun aSoloWriteHasNoGroupIdAtAll() =
        runTest {
            val bijou = addBunny("Bijou")

            val id = observations.add(listOf(bijou), noticed).single()

            // Sharedness *is* `groupId IS NOT NULL`, so a solo observation must not carry one —
            // otherwise it reads as "observed together" with nobody (ADR-0008).
            assertNull(observations.observationNow(id)!!.groupId)
        }

    @Test
    fun editingATrayFactMovesEveryRowAndEditingAMoodMovesOne() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (bijouRow, nuggetRow) =
                observations.add(listOf(bijou, nugget), noticed, ObservationFacts(tray = worryingTray))

            observations.updateTray(bijouRow, worryingTray.copy(droppingsAmount = DroppingsAmount.NONE))
            observations.updateIndividual(bijouRow, IndividualFacts(mood = Mood.WITHDRAWN))

            // The tray fact propagated: letting it drift would have bunny A reading "none" while bunny
            // B reads "few" for the same tray — the false attribution arriving through editing.
            assertEquals(DroppingsAmount.NONE, observations.observationNow(bijouRow)!!.droppingsAmount)
            assertEquals(DroppingsAmount.NONE, observations.observationNow(nuggetRow)!!.droppingsAmount)
            // The mood did not: one hunched while the other is bouncing around is a real state.
            assertEquals(Mood.WITHDRAWN, observations.observationNow(bijouRow)!!.mood)
            assertNull(observations.observationNow(nuggetRow)!!.mood)
        }

    @Test
    fun addingAParticipantToASoloObservationMintsAGroupIdAndInheritsTheTray() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val solo = observations.add(listOf(bijou), noticed, ObservationFacts(tray = worryingTray)).single()

            observations.addParticipant(solo, nugget)

            val groupId = observations.observationNow(solo)!!.groupId
            // Back-filled onto the row that already existed, inside the same transaction (ADR-0008).
            assertNotNull(groupId)
            val group = database.observationDao().groupNow(groupId!!)
            assertEquals(setOf(bijou, nugget), group.map { it.bunnyId }.toSet())
            // The new row inherits the tray facts — the tray was always about both bunnies, which is
            // the reason the correction is being made — and starts with blank individual fields.
            val added = group.single { it.bunnyId == nugget }
            assertEquals(worryingTray, observations.trayFactsNow(added.id))
            assertEquals(IndividualFacts(), added.individualFacts())
        }

    @Test
    fun addingAParticipantTwiceDoesNotDuplicateTheRow() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val solo = observations.add(listOf(bijou), noticed).single()

            observations.addParticipant(solo, nugget)
            observations.addParticipant(solo, nugget)

            assertEquals(2, database.countRows("observations"))
        }

    @Test
    fun deletingABunnyLeavesTheSurvivorStillMarkedObservedTogether() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (bijouRow, _) = observations.add(listOf(bijou, nugget), noticed, ObservationFacts(tray = worryingTray))

            database.bunnyDao().deleteById(nugget)

            // Deletion *preserves* history: they were observed together and one of them is gone, so the
            // survivor keeps its group id and is never silently downgraded to a solo observation.
            assertEquals(1, database.countRows("observations"))
            assertNotNull(observations.observationNow(bijouRow)!!.groupId)
        }

    @Test
    fun correctingTheParticipantsDownToOneClearsTheMarker() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (bijouRow, _) = observations.add(listOf(bijou, nugget), noticed, ObservationFacts(tray = worryingTray))

            observations.removeParticipant(bijouRow, nugget)

            // Correction *amends* history: the owner is saying it never covered Nugget, so Bijou's row
            // must not be left asserting a shared observation with nobody. This assertion and the one
            // above are the same situation with different meanings — the pair *is* the distinction.
            assertEquals(1, database.countRows("observations"))
            assertNull(observations.observationNow(bijouRow)!!.groupId)
        }

    @Test
    fun correctingAThreeWayObservationDownToTwoKeepsItShared() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val clover = addBunny("Clover")
            val bijouRow = observations.add(listOf(bijou, nugget, clover), noticed).first()

            observations.removeParticipant(bijouRow, clover)

            // The clear only fires at one survivor: two bunnies still share a tray.
            assertEquals(2, database.countRows("observations"))
            assertNotNull(observations.observationNow(bijouRow)!!.groupId)
        }

    @Test
    fun deletingABunnyCascadesItsObservationsAndSymptomLinksButNoSymptom() =
        runTest {
            val bijou = addBunny("Bijou")
            val symptomId = anySymptomId()
            val seededSymptoms = database.countRows("symptoms")
            observations.add(
                listOf(bijou),
                noticed,
                ObservationFacts(individual = IndividualFacts(symptomIds = setOf(symptomId))),
            )
            assertEquals(1, database.countRows("observation_symptoms"))

            database.bunnyDao().deleteById(bijou)

            assertEquals(0, database.countRows("observations"))
            assertEquals(0, database.countRows("observation_symptoms"))
            // The vocabulary is not history: retiring a symptom hides it and deleting a bunny must not
            // touch it at all (ADR-0010).
            assertEquals(seededSymptoms, database.countRows("symptoms"))
        }

    /**
     * A stand-in for a photographed tray. `MediaFiles`' own encoding is proven in its own test; what
     * matters here is that a real file exists for the delete rules to be right or wrong about.
     */
    private fun writeTrayPhoto(): Pair<String, File> {
        val relativePath = "observations/${UUID.randomUUID()}.jpg"
        val file = media.resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return relativePath to file
    }

    @Test
    fun theMultiValuedDroppingsFieldsRoundTripPerParticipant() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")

            val ids = observations.add(listOf(bijou, nugget), noticed, ObservationFacts(tray = worryingTray))

            // Both values on both rows. A join table keyed on `observationId` (there is no group
            // table — ADR-0008 forbids a group id on a solo row) means "identical across the group"
            // is something the repository has to *write* rather than something a column gives free.
            for (id in ids) {
                assertEquals(
                    setOf(DroppingsAppearance.ROUND, DroppingsAppearance.SOFT),
                    observations.trayFactsNow(id)!!.droppingsAppearance,
                )
            }
            assertEquals(4, database.countRows("observation_droppings_appearance"))
            assertEquals(2, database.countRows("observation_droppings_sizes"))
        }

    @Test
    fun editingTheTrayReplacesTheValuesRatherThanMergingThem() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (bijouRow, nuggetRow) =
                observations.add(listOf(bijou, nugget), noticed, ObservationFacts(tray = worryingTray))

            observations.updateTray(
                bijouRow,
                worryingTray.copy(droppingsAppearance = setOf(DroppingsAppearance.DIARRHOEA)),
            )

            // Replaced, not merged, on the same grounds as the symptom links: the picker's state is
            // the whole answer, so a value the owner unticked has to disappear — from every
            // participant's row, because it is one tray.
            for (id in listOf(bijouRow, nuggetRow)) {
                assertEquals(
                    setOf(DroppingsAppearance.DIARRHOEA),
                    observations.trayFactsNow(id)!!.droppingsAppearance,
                )
            }
            assertEquals(2, database.countRows("observation_droppings_appearance"))
        }

    @Test
    fun anUntouchedTrayRecordsNoDroppingsValuesAtAll() =
        runTest {
            val bijou = addBunny("Bijou")

            val id = observations.add(listOf(bijou), noticed).single()

            // Zero rows, never a value nobody chose. An empty set is this field's spelling of "not
            // checked", and the app must never read it as a healthy tray (ADR-0001).
            assertEquals(0, database.countRows("observation_droppings_appearance"))
            assertTrue(observations.trayFactsNow(id)!!.droppingsAppearance.isEmpty())
        }

    @Test
    fun theDroppingsValuesGoWithTheirObservation() =
        runTest {
            val bijou = addBunny("Bijou")
            val id = observations.add(listOf(bijou), noticed, ObservationFacts(tray = worryingTray)).single()

            observations.delete(id)

            // Through the cascade, which is the whole reason the foreign key is there — asserted
            // because a cascade the ORM is only *asked* for is not a cascade.
            assertEquals(0, database.countRows("observation_droppings_appearance"))
            assertEquals(0, database.countRows("observation_droppings_sizes"))
        }

    @Test
    fun aTrayPhotoSurvivesTheCorrectionThatRemovesOneParticipant() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (path, file) = writeTrayPhoto()
            val (bijouRow, _) =
                observations.add(
                    listOf(bijou, nugget),
                    noticed,
                    ObservationFacts(tray = worryingTray.copy(trayPhotoPath = path)),
                )

            observations.removeParticipant(bijouRow, nugget)

            // The path is duplicated onto every row, so removing one leaves the survivor pointing at
            // a file that must still be there. **The file goes only when no other row references
            // it** — ADR-0029's one new rule, and the reason a duplicated path was acceptable at all
            // instead of a group table.
            assertTrue("the survivor's photo must not go with the correction", file.exists())
            assertEquals(path, observations.observationNow(bijouRow)!!.trayPhotoPath)
        }

    @Test
    fun deletingTheWholeObservationTakesTheTrayPhotoWithIt() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (path, file) = writeTrayPhoto()
            val (bijouRow, _) =
                observations.add(
                    listOf(bijou, nugget),
                    noticed,
                    ObservationFacts(tray = worryingTray.copy(trayPhotoPath = path)),
                )

            observations.delete(bijouRow)

            // The other half of the same rule: with the last referencing row gone, keeping the file
            // would leave an orphan nobody can ever reach or delete.
            assertFalse("the last reference going should take the file", file.exists())
        }

    @Test
    fun replacingTheTrayPhotoRemovesTheOneItReplaced() =
        runTest {
            val bijou = addBunny("Bijou")
            val (firstPath, firstFile) = writeTrayPhoto()
            val (secondPath, secondFile) = writeTrayPhoto()
            val id =
                observations
                    .add(
                        listOf(bijou),
                        noticed,
                        ObservationFacts(tray = worryingTray.copy(trayPhotoPath = firstPath)),
                    ).single()

            observations.updateTray(id, worryingTray.copy(trayPhotoPath = secondPath))

            assertFalse("the replaced photo is referenced by nothing and should go", firstFile.exists())
            assertTrue(secondFile.exists())
        }

    @Test
    fun aSymptomLinkImpliesTheOwnerLooked() =
        runTest {
            val bijou = addBunny("Bijou")
            val symptomId = anySymptomId()

            val id =
                observations
                    .add(
                        listOf(bijou),
                        noticed,
                        // Deliberately contradictory input: the repository normalises rather than trusting it.
                        ObservationFacts(
                            individual = IndividualFacts(symptomsChecked = false, symptomIds = setOf(symptomId)),
                        ),
                    ).single()

            assertTrue(observations.observationNow(id)!!.symptomsChecked)
            assertEquals(setOf(symptomId), observations.symptomIdsNow(id))
        }

    @Test
    fun editingSymptomsReplacesTheLinksRatherThanMergingThem() =
        runTest {
            val bijou = addBunny("Bijou")
            val symptoms = database.symptomDao().allNow().map { it.id }
            val id =
                observations
                    .add(
                        listOf(bijou),
                        noticed,
                        ObservationFacts(individual = IndividualFacts(symptomIds = setOf(symptoms[0], symptoms[1]))),
                    ).single()

            observations.updateIndividual(id, IndividualFacts(symptomIds = setOf(symptoms[1])))

            // The picker's state is the whole answer, so an unticked symptom has to disappear.
            assertEquals(setOf(symptoms[1]), observations.symptomIdsNow(id))
        }

    @Test
    fun deletingASharedObservationRemovesEveryParticipantsRow() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            val (bijouRow, _) = observations.add(listOf(bijou, nugget), noticed)

            observations.delete(bijouRow)

            // "That observation never happened" is one event about one moment, however many bunnies it
            // covered. The narrower "this bunny wasn't in it" is `removeParticipant`.
            assertEquals(0, database.countRows("observations"))
        }

    @Test
    fun theLastSurvivingParticipantsObservationsCountAsSoleOwned() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            observations.add(listOf(bijou, nugget), noticed)

            val whileShared = database.bunnyDao().recordCounts(bijou)!!
            assertEquals(0, whileShared.soleOwnedRecords)
            assertEquals(1, whileShared.sharedRecords)

            database.bunnyDao().deleteById(nugget)

            // Bucketed by **survivorship, not provenance** (ADR-0004): this observation was created
            // shared, but deleting Bijou now destroys it outright, so calling it "shared" would promise
            // a survivor that does not exist.
            val whenLast = database.bunnyDao().recordCounts(bijou)!!
            assertEquals(1, whenLast.soleOwnedRecords)
            assertEquals(0, whenLast.sharedRecords)
            assertEquals(DeleteConfirmation.TWO_STAGE, deleteConfirmationFor(whenLast))
        }

    @Test
    fun anArchivedHousemateKeepsTheObservationCountedAsShared() =
        runTest {
            val bijou = addBunny("Bijou")
            val hazel = addBunny("Hazel", archived = true)
            observations.add(listOf(bijou, hazel), noticed)

            // An archived bunny is a survivor: its copy stays readable in its own read-only scope, so
            // the count must not quietly promote the observation into the destroyed bucket (ADR-0004).
            val counts = database.bunnyDao().recordCounts(bijou)!!
            assertEquals(0, counts.soleOwnedRecords)
            assertEquals(1, counts.sharedRecords)
        }

    @Test
    fun weighingsAndSoloObservationsShareTheSoleOwnedBucket() =
        runTest {
            val bijou = addBunny("Bijou")
            WeightRepository(database).add(WeightEntity(bunnyId = bijou, grams = 2400, recordedAt = noticed))
            observations.add(listOf(bijou), noticed)

            val counts = database.bunnyDao().recordCounts(bijou)!!
            assertEquals(2, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
        }

    @Test
    fun theCombinedTimelineLeavesOutArchivedBunnies() =
        runTest {
            val bijou = addBunny("Bijou")
            val hazel = addBunny("Hazel", archived = true)
            observations.add(listOf(bijou), noticed)
            observations.add(listOf(hazel), noticed)

            // "All bunnies" means the active ones (ADR-0015); an archived bunny is reached only through
            // its own scope (ADR-0004).
            val combined = observations.forActiveBunnies.first()
            assertEquals(listOf(bijou), combined.map { it.bunnyId })
        }
}
