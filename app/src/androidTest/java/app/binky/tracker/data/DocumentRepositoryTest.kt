package app.binky.tracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Documents against a real database and a real [MediaFiles].
 *
 * Instrumented rather than JVM for [PhotoRepositoryTest]'s reason: every claim here is about a row
 * **and the file beside it**, and these are the largest files the app writes — a mocked media helper
 * that wrote nothing would let ADR-0020's ordering break in the one place it costs the most.
 */
@RunWith(AndroidJUnit4::class)
class DocumentRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var database: BunnyDatabase
    private lateinit var media: MediaFiles
    private lateinit var documents: DocumentRepository
    private lateinit var bunnies: BunnyRepository
    private lateinit var visits: VisitRepository

    @Before
    fun open() {
        database = inMemoryDatabase()
        media = temporaryMedia()
        documents = DocumentRepository(database, media)
        bunnies = BunnyRepository(database, FluffleRepository(database), temporaryPreferences(), media)
        visits = VisitRepository(database, WeightRepository(database))
    }

    @After
    fun close() = database.close()

    @Test
    fun scanningWritesEveryPageFileAndStoresRelativePaths() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))

            val id = documents.add(bunnyId, title = "Vaccination card", pages = listOf(page(), page()))

            val pages = documents.pages(id).first()
            assertEquals(2, pages.size)
            assertEquals("pages keep the order they were scanned in", listOf(0, 1), pages.map { it.position })
            pages.forEach { page ->
                assertTrue("expected documents/<uuid>.jpg but was ${page.path}", page.path.matches(PAGE_PATH))
                assertTrue("${page.path} was not written", media.resolve(page.path).exists())
            }
        }

    /**
     * The date on the page is the owner's to type. The pipeline reads a capture instant off every
     * source — it is right for a photo and wrong here, and this is the assertion that says so.
     */
    @Test
    fun aScanIsNotDatedByWhenItWasScanned() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Clover"))

            val id = documents.add(bunnyId, title = "Lab result", pages = listOf(page()))

            assertNull(checkNotNull(documents.documentNow(id)).capturedAt)
        }

    @Test
    fun appendedPagesLandAfterTheOnesAlreadyThere() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Pepper"))
            val id = documents.add(bunnyId, title = "Discharge note", pages = listOf(page()))

            documents.addPages(id, listOf(page(), page()))

            assertEquals(listOf(0, 1, 2), documents.pages(id).first().map { it.position })
        }

    @Test
    fun movingAPageSwapsItWithItsNeighbour() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Pepper"))
            val id = documents.add(bunnyId, title = "X-ray report", pages = listOf(page(), page(), page()))
            val before = documents.pages(id).first().map { it.id }

            documents.movePage(before[2], direction = -1)

            assertEquals(listOf(before[0], before[2], before[1]), documents.pages(id).first().map { it.id })
        }

    /** At the ends there is nothing to swap with, and the call has to be a no-op rather than a throw. */
    @Test
    fun movingPastEitherEndChangesNothing() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Pepper"))
            val id = documents.add(bunnyId, title = "Receipt", pages = listOf(page(), page()))
            val before = documents.pages(id).first().map { it.id }

            documents.movePage(before.first(), direction = -1)
            documents.movePage(before.last(), direction = 1)

            assertEquals(before, documents.pages(id).first().map { it.id })
        }

    /**
     * A document with no pages is still a record — its title, its date and its visit are all things
     * the owner entered, and losing them because a bad scan was deleted is a delete nobody asked for.
     */
    @Test
    fun deletingTheLastPageLeavesTheDocumentStanding() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            val id = documents.add(bunnyId, title = "Vaccination card", pages = listOf(page()))
            val file =
                media.resolve(
                    documents
                        .pages(id)
                        .first()
                        .single()
                        .path,
                )

            documents.deletePage(
                documents
                    .pages(id)
                    .first()
                    .single()
                    .id,
            )

            assertFalse("the page file should go with its row", file.exists())
            assertEquals(emptyList<DocumentPageEntity>(), documents.pages(id).first())
            assertEquals("Vaccination card", checkNotNull(documents.documentNow(id)).title)
        }

    @Test
    fun deletingADocumentTakesEveryPageRowAndFile() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            val doomed = documents.add(bunnyId, title = "Old estimate", pages = listOf(page(), page()))
            val kept = documents.add(bunnyId, title = "Vaccination card", pages = listOf(page()))
            val doomedFiles = documents.pages(doomed).first().map { media.resolve(it.path) }
            val keptFile =
                media.resolve(
                    documents
                        .pages(kept)
                        .first()
                        .single()
                        .path,
                )

            documents.delete(doomed)

            doomedFiles.forEach { assertFalse("${it.name} outlived its document", it.exists()) }
            assertTrue("another document's page was swept up", keptFile.exists())
            assertEquals(1, database.countRows("documents"))
            assertEquals(1, database.countRows("document_pages"))
        }

    /**
     * Two cascades deep: documents go with the bunny, pages go with the document. Room takes the
     * rows and only `BunnyRepository.delete` can take the files — the second bunny proves the sweep
     * is scoped, because a blanket `documents/` wipe would pass the first assertion on its own.
     */
    @Test
    fun deletingABunnyTakesItsDocumentsPagesAndFiles() =
        runTest {
            val thumper = bunnies.add(BunnyEntity(name = "Thumper"))
            val clover = bunnies.add(BunnyEntity(name = "Clover"))
            val doomed = documents.add(thumper, title = "Dental report", pages = listOf(page(), page()))
            val kept = documents.add(clover, title = "Eye drops", pages = listOf(page()))
            val doomedFiles = documents.pages(doomed).first().map { media.resolve(it.path) }
            val keptFile =
                media.resolve(
                    documents
                        .pages(kept)
                        .first()
                        .single()
                        .path,
                )

            bunnies.delete(thumper)

            doomedFiles.forEach { assertFalse("${it.name} outlived its bunny", it.exists()) }
            assertTrue("another bunny's page was swept up", keptFile.exists())
            assertEquals(1, database.countRows("documents"))
            assertEquals(1, database.countRows("document_pages"))
        }

    /**
     * `SET NULL`, not `CASCADE`. The paperwork is the health record and the visit is where it came
     * from — deleting last year's visit must not destroy the vaccination card it produced.
     */
    @Test
    fun deletingAVisitLeavesItsDocumentsWithTheBunny() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            val visitId =
                visits.add(VisitEntity(bunnyId = bunnyId, visitedOn = LocalDate.now(), reason = "Vaccination"))
            val id = documents.add(bunnyId, title = "Vaccination card", pages = listOf(page()), visitId = visitId)
            val file =
                media.resolve(
                    documents
                        .pages(id)
                        .first()
                        .single()
                        .path,
                )

            // `keepWeighing` is about the weight row and irrelevant here; the point is that the
            // visit goes and the paperwork does not.
            visits.delete(visitId, keepWeighing = false)

            val document = checkNotNull(documents.documentNow(id))
            assertNull("the visit is gone, the document is not", document.visitId)
            assertEquals(bunnyId, document.bunnyId)
            assertTrue("the page file must survive its visit", file.exists())
            assertEquals(listOf(id), documents.documents(bunnyId).first().map { it.document.id })
        }

    /** Detaching by hand is the same rule reached deliberately rather than by a delete. */
    @Test
    fun detachingFromAVisitLeavesTheDocumentWithItsBunny() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Clover"))
            val visitId =
                visits.add(VisitEntity(bunnyId = bunnyId, visitedOn = LocalDate.now(), reason = "Molar check"))
            val id = documents.add(bunnyId, title = "X-ray report", pages = listOf(page()), visitId = visitId)

            documents.attachToVisit(id, null)

            assertNull(checkNotNull(documents.documentNow(id)).visitId)
            assertEquals(emptyList<String>(), documents.documentsOfVisit(visitId).first().map { it.document.id })
            assertEquals(listOf(id), documents.unattached(bunnyId).first().map { it.document.id })
        }

    /** A visit's picker may only offer documents no visit has claimed — `visitId` is single-valued. */
    @Test
    fun theAttachPickerOffersOnlyUnclaimedDocuments() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Pepper"))
            val visitId =
                visits.add(VisitEntity(bunnyId = bunnyId, visitedOn = LocalDate.now(), reason = "Check-up"))
            val claimed = documents.add(bunnyId, title = "Claimed", pages = listOf(page()), visitId = visitId)
            val free = documents.add(bunnyId, title = "Free", pages = listOf(page()))

            val offered = documents.unattached(bunnyId).first().map { it.document.id }

            assertEquals(listOf(free), offered)
            assertEquals(listOf(claimed), documents.documentsOfVisit(visitId).first().map { it.document.id })
        }

    /**
     * The list summary is a projection, so the count and the thumbnail have to move with the pages
     * rather than with whatever was true when the row was written.
     */
    @Test
    fun theSummaryCountsPagesAndNamesTheFirstOne() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            val id = documents.add(bunnyId, title = "Discharge note", pages = listOf(page(), page()))
            val firstPage = documents.pages(id).first().first()

            val summary = documents.documents(bunnyId).first().single()

            assertEquals(2, summary.pageCount)
            assertEquals(firstPage.path, summary.firstPagePath)

            documents.deletePage(firstPage.id)

            val after = documents.documents(bunnyId).first().single()
            assertEquals(1, after.pageCount)
            assertEquals(
                documents
                    .pages(id)
                    .first()
                    .single()
                    .path,
                after.firstPagePath,
            )
        }

    /** Documents are sole-owned, so the delete confirmation has to name them (ADR-0004). */
    @Test
    fun documentsCountTowardsWhatDeletingABunnyWouldDestroy() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            documents.add(bunnyId, title = "Vaccination card", pages = listOf(page(), page()))
            documents.add(bunnyId, title = "Lab result", pages = listOf(page()))

            val counts = bunnies.recordCounts(bunnyId)

            // The *documents*, not their pages: the confirmation counts records an owner recognises,
            // and "3 records" for two documents would be the app counting its own storage at them.
            assertEquals(2, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
        }

    // -- fixtures ----------------------------------------------------------------------------

    /** A real JPEG, since the whole point of these tests is that a real file is written. */
    private fun page(): Uri {
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 238, 232))
        val file = File(context.cacheDir, "page-${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    private companion object {
        val PAGE_PATH = Regex("""documents/[0-9a-f-]{36}\.jpg""")
    }
}
