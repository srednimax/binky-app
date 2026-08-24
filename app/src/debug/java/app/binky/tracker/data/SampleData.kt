package app.binky.tracker.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * The debug-only sample-data action, behind `BuildConfig.DEBUG` at its call site.
 *
 * It writes **through the repositories**, never through the DAOs, so it cannot seed rows the app
 * itself could not produce — and so a year of weighings exercises 2a's insert-time acknowledgment
 * discard a few hundred times on a real device rather than in a test.
 *
 * It exists because 2d's chart review needs a year of uneven, back-dated history and hand-typing
 * that through a date picker is the toil that gets skimmed. The fixture is **deterministic** — a
 * fixed seed and fixed offsets — because 2d and 2f are meant to be reviewing the same chart.
 */
private const val SAMPLE_SEED = 20260726L

/** Debug fixture names, deliberately not in `strings.xml`: no owner ever sees this action. */
private const val SAMPLE_BUNNY = "Lily"
private const val SAMPLE_HOUSEMATE = "Sznycel"

/** Every weighing is stamped at the same clock time, so a tie has to be made deliberately. */
private val WEIGHING_TIME: LocalTime = LocalTime.of(8, 30)

/**
 * Observations land in the evening, well clear of [WEIGHING_TIME].
 *
 * Not cosmetic: it keeps a seeded observation and a seeded weighing on the same day from sharing a
 * minute, so anything that reads "the latest record on this day" cannot pass by coincidence.
 */
private val OBSERVATION_TIME: LocalTime = LocalTime.of(18, 0)

/**
 * Seeds the fixture, or returns false if it is already there.
 *
 * Not idempotent by merging — it simply declines to run twice, because "duplicates allowed" is a
 * real rule for real bunnies (ADR-0016) and the seeder must not be the thing that quietly weakens it.
 */
suspend fun seedSampleData(
    bunnies: BunnyRepository,
    fluffles: FluffleRepository,
    weights: WeightRepository,
    observations: ObservationRepository,
    symptoms: SymptomRepository,
    photos: PhotoRepository,
    care: CareRepository,
    watches: WatchRepository,
    vets: VetRepository,
    visits: VisitRepository,
    medications: MedicationRepository,
    documents: DocumentRepository,
    cacheDir: File,
    now: Instant = Instant.now(),
): Boolean {
    val existing = bunnies.activeBunnies.first()
    if (existing.any { it.name == SAMPLE_BUNNY || it.name == SAMPLE_HOUSEMATE }) return false

    val lily = bunnies.add(BunnyEntity(name = SAMPLE_BUNNY, sex = Sex.FEMALE, neutered = NeuterStatus.YES))
    val sznycel = bunnies.add(BunnyEntity(name = SAMPLE_HOUSEMATE, sex = Sex.MALE, neutered = NeuterStatus.YES))
    // A bonded pair, so the shared observation below has somewhere to land (ADR-0008).
    fluffles.livesWith(lily, sznycel)

    for ((daysAgo, grams) in lilySeries()) {
        weights.add(WeightEntity(bunnyId = lily, grams = grams, recordedAt = now.daysAgo(daysAgo)))
    }
    // Sznycel stays steady, so the two cards on Home show a flagged bunny beside an unflagged one.
    for ((daysAgo, grams) in listOf(28L to 1780, 21L to 1795, 14L to 1785, 7L to 1790, 1L to 1788)) {
        weights.add(WeightEntity(bunnyId = sznycel, grams = grams, recordedAt = now.daysAgo(daysAgo)))
    }

    seedObservations(observations, symptoms, lily, sznycel, now)
    seedPhotos(photos, cacheDir, lily, sznycel, now)
    seedCare(care, lily, sznycel, now)
    seedWatches(watches, lily, sznycel, now)
    val seededVisits = seedVisits(vets, visits, lily, sznycel, now)
    seedMedications(medications, lily, now)
    seedDocuments(documents, cacheDir, lily, sznycel, seededVisits, now)
    return true
}

/**
 * The medication half: **three courses, all on Lily**, chosen so every state 5e draws is on screen
 * without waiting a week for one — and so 5f has something real to arm tonight.
 *
 * - **An open twice-daily course with a partial history**, which is the one that has to be looked at
 *   hardest: it puts unanswered slots in today's list, answered ones behind it, and a deliberate
 *   *skip* among them, so "skipped is a recorded fact, not an absence" is visible rather than
 *   argued from a test. It is also the only course here with reminders on, which makes it the one
 *   the delivery line is gated on and the one 5f's alarm will be rebuilt from.
 * - **A course that ended last week**, so the active-then-ended ordering has something to order and
 *   the *Ended* row can be read beside a running one.
 * - **A course with no schedule at all**, because scheduling is optional (ADR-0002): no times means
 *   no slots ever, no reminder switch in the editor, and an ad-hoc dose as the only way to record
 *   anything — three branches nothing else here exercises.
 *
 * **Sznycel deliberately gets none**, which puts the medications empty state on screen in the same
 * fixture. The per-bunny scoping is already proven by the visits above.
 *
 * Written **through the repository**, like everything else here. The past days go in as hand-built
 * [DueDose] keys — a slot is a local day and a local time and nothing else, so this writes exactly
 * what the screen writes when the owner answers on the day — while **today's** slots are read back
 * through [MedicationRepository.scheduleNow] and answered for real, because a fixture whose current
 * day was assembled by hand would prove nothing about the derivation it is meant to demonstrate.
 */
private suspend fun seedMedications(
    medications: MedicationRepository,
    lily: String,
    now: Instant,
) {
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()
    val morning = LocalTime.of(8, 0)
    val evening = LocalTime.of(20, 0)

    val metacam =
        medications.add(
            MedicationCourseEntity(
                bunnyId = lily,
                name = "Metacam",
                doseAmount = "0.3 ml",
                startOn = today.minusDays(6),
                notes = "After food. Back to the vet if she stops eating.",
            ),
            times = listOf(morning, evening),
        )

    for (daysAgo in 5L downTo 1L) {
        val day = today.minusDays(daysAgo)
        for (time in listOf(morning, evening)) {
            // One deliberate skip in the middle of an otherwise compliant week: a skip is a
            // recorded decision, and the screen has to show it in the same voice as a dose given.
            val skipped = daysAgo == 3L && time == evening
            medications.answer(
                slot =
                    DueDose(
                        courseId = metacam,
                        scheduledOn = day,
                        scheduledTime = time,
                        at = day.atTime(time).atZone(zone).toInstant(),
                    ),
                status = if (skipped) DoseStatus.SKIPPED else DoseStatus.GIVEN,
                note = if (skipped) "Spat it straight out. Tried again in the morning." else null,
                // A few minutes after the slot, which is what a real answer looks like and keeps the
                // history's clock times from being suspiciously exact.
                recordedAt =
                    day
                        .atTime(time)
                        .plusMinutes(4)
                        .atZone(zone)
                        .toInstant(),
            )
        }
    }

    // Today's earliest slot answered and the rest left open — so the tab opens on one of each, and
    // 5f has an unanswered slot to arm tonight.
    medications
        .scheduleNow(lily, zone = zone, today = today)
        .firstOrNull { it.course.id == metacam }
        ?.let { medications.answer(slot = it.due, status = DoseStatus.GIVEN, recordedAt = now) }

    val panacur =
        medications.add(
            MedicationCourseEntity(
                bunnyId = lily,
                name = "Panacur",
                doseAmount = "1 ml",
                startOn = today.minusDays(20),
                endOn = today.minusDays(7),
                notes = "Nine days for E. cuniculi. Finished.",
                // Off, because the course is over — and because a course with reminders off is a
                // state the editor's switch has to be able to show.
                remindersEnabled = false,
            ),
            times = listOf(LocalTime.of(9, 0)),
        )
    for (daysAgo in listOf(9L, 8L)) {
        val day = today.minusDays(daysAgo)
        medications.answer(
            slot =
                DueDose(
                    courseId = panacur,
                    scheduledOn = day,
                    scheduledTime = LocalTime.of(9, 0),
                    at = day.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant(),
                ),
            status = DoseStatus.GIVEN,
            recordedAt = day.atTime(LocalTime.of(9, 5)).atZone(zone).toInstant(),
        )
    }

    val recovery =
        medications.add(
            MedicationCourseEntity(
                bunnyId = lily,
                name = "Recovery food",
                // The case the free-text amount exists for: nobody was given a number to type.
                doseAmount = "one syringe, as often as she will take it",
                startOn = today.minusDays(2),
            ),
        )
    medications.recordAdHoc(
        courseId = recovery,
        status = DoseStatus.GIVEN,
        note = "Took most of it.",
        recordedAt = now.daysAgo(1, LocalTime.of(22, 30)),
        now = now,
    )
}

/**
 * The vet half: **two directory entries and three visits**, chosen so every branch 5c draws is on
 * screen without waiting for a real appointment.
 *
 * - **A visit with a weighing**, which is the one that has to be looked at hardest: it puts a
 *   visit-tagged row in Lily's weight history, where *Edit* and *Delete* are replaced by *Open the
 *   visit*, and it is the row the entry form refuses to overwrite (ADR-0021's amendment).
 * - **A visit with no vet**, because `vetId` is nullable and a visit that names nobody still has to
 *   render — an owner who saw an out-of-hours locum has no directory entry for them.
 * - **A visit on the housemate**, so deleting one bunny can be seen not to take the other's, and so
 *   the second directory entry is in use rather than decorative.
 *
 * Written **through the repositories**, like everything else here, so the weighing lands through the
 * same one transaction an owner's does rather than as two rows a seeder arranged by hand.
 */
private suspend fun seedVisits(
    vets: VetRepository,
    visits: VisitRepository,
    lily: String,
    sznycel: String,
    now: Instant,
): SeededVisits {
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()

    val kowalska =
        vets.add(
            VetEntity(
                name = "Dr Kowalska",
                clinic = "Klinika Ada",
                phone = "+48 22 000 00 00",
                notes = "Exotics on Tuesdays and Thursdays.",
            ),
        )
    val nowak = vets.add(VetEntity(name = "Dr Nowak", clinic = "Przychodnia Mokotów"))

    // The weight is deliberately a little under the series around it, so the visit's number is
    // recognisable in the chart rather than lost among the seeded weighings.
    val molars =
        visits.add(
            VisitEntity(
                bunnyId = lily,
                vetId = kowalska,
                visitedOn = today.minusDays(9),
                reason = "Molar check",
                notes = "Spurs filed. Back in six months unless she goes off her food.",
            ),
            grams = 2380,
            now = now,
            zone = zone,
        )
    val vaccination =
        visits.add(
            VisitEntity(
                bunnyId = lily,
                visitedOn = today.minusMonths(7),
                reason = "Vaccination",
            ),
            now = now,
            zone = zone,
        )
    val eye =
        visits.add(
            VisitEntity(
                bunnyId = sznycel,
                vetId = nowak,
                visitedOn = today.minusDays(40),
                reason = "Scratched eye",
                notes = "Drops for five days.",
            ),
            now = now,
            zone = zone,
        )
    return SeededVisits(molars = molars, vaccination = vaccination, sznycelEye = eye)
}

/**
 * The visit ids the document half attaches to.
 *
 * Returned rather than re-queried, because "the vaccination visit" is a thing this fixture *knows*
 * and a lookup by reason string would be the seeder asserting its own data back to itself.
 */
private data class SeededVisits(
    val molars: String,
    val vaccination: String,
    val sznycelEye: String,
)

/**
 * The watch half: **one running and one already run out**, which is both of the states 4d has to
 * show and neither of which can otherwise be looked at without waiting days for it.
 *
 * - **Lily's is running**, four days in of seven, so Home's card carries *"Watch active · 3 days
 *   left"* with close-early beside it, the flag stops offering *Start a watch*, and Lily is the
 *   bunny the next morning's sweep nags about.
 * - **Sznycel's ran out yesterday**, so the auto-expiry prompt is on screen the moment the fixture
 *   lands rather than a week later — and because Sznycel's series is deliberately steady, it is also
 *   the prompt's *no live flag* branch, where it has to report the record without letting the
 *   absence of a flag read as reassurance (ADR-0001).
 *
 * That pairing is the point: it puts Lily under a running watch and Sznycel under an expired one, so
 * the healthy day excludes exactly one of them, with the reason shown, and the difference between
 * "active" and "expired" is visible in the same tap rather than argued about from a test.
 */
private suspend fun seedWatches(
    watches: WatchRepository,
    lily: String,
    sznycel: String,
    now: Instant,
) {
    watches.start(lily, WatchDuration.DAYS_7, now.daysAgo(4))
    watches.start(sznycel, WatchDuration.DAYS_3, now.daysAgo(4))
}

/**
 * The care half: one of each state the Care screen has to render, so 4f reviews a full list and 4g
 * has something genuinely overdue to look at.
 *
 * - **An overdue nail trim** — a preset whose anchor is in the past and which has never been
 *   completed, which is the one path where `firstDueOn` is returned unmodified. It is also what the
 *   sweep posts about on the first run after seeding.
 * - **A vaccination due in months**, so a yearly interval and the `FREQ=YEARLY` hand-off can be seen
 *   without waiting a year, and so the list has something that is plainly *not* urgent.
 * - **A weigh-in with a completion history**, on the bunny who already has a year of weighings —
 *   which is the two-source completion (ADR-0018's amendment) with both sources non-empty, the only
 *   arrangement where the `max` of the two can be seen to be doing anything.
 * - **A custom reminder on the housemate**, because the free-text path has no type, no icon of its
 *   own and no preset interval, and every one of those is a branch nothing else here exercises.
 */
private suspend fun seedCare(
    care: CareRepository,
    lily: String,
    sznycel: String,
    now: Instant,
) {
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()

    care.add(
        CareReminderEntity(
            bunnyId = lily,
            type = CareType.NAIL_TRIM,
            intervalCount = 6,
            intervalUnit = CareIntervalUnit.WEEK,
            firstDueOn = today.minusDays(11),
        ),
    )

    care.add(
        CareReminderEntity(
            bunnyId = lily,
            type = CareType.VACCINATION,
            intervalCount = 1,
            intervalUnit = CareIntervalUnit.YEAR,
            firstDueOn = today.plusMonths(4),
        ),
    )

    val weighIn =
        care.add(
            CareReminderEntity(
                bunnyId = lily,
                type = CareType.WEIGH_IN,
                intervalCount = 1,
                intervalUnit = CareIntervalUnit.WEEK,
                firstDueOn = today.minusWeeks(6),
            ),
        )
    // Two typed completions well behind the weight series, so the weigh-in's due date visibly comes
    // from the *later* of the two sources rather than from these.
    care.complete(weighIn, today.minusWeeks(5), today = today)
    care.complete(weighIn, today.minusWeeks(4), note = "Wriggled off the scale twice.", today = today)

    care.add(
        CareReminderEntity(
            bunnyId = sznycel,
            label = "Hay order",
            intervalCount = 2,
            intervalUnit = CareIntervalUnit.MONTH,
            firstDueOn = today.plusDays(3),
        ),
    )
}

/**
 * A handful of pictures, generated rather than shipped as assets — an APK does not need to carry
 * fake rabbits, and going through [PhotoRepository.add] means these land the same way an owner's do:
 * downsampled, stripped, file before row (ADR-0020).
 *
 * The set is chosen for what it makes visible in the gallery:
 *
 * - **two EXIF dates, on the two *newest-added* photos**, back-dated by months. Ordering by
 *   `COALESCE(capturedAt, createdAt)` puts them in the middle of the grid; ordering by insertion
 *   would put them first, so a regression there is visible at a glance rather than by reading rows.
 * - **both orientations**, because the grid crops to squares and the pager fits the whole frame —
 *   a landscape photo is where those two disagree.
 * - **captions on some and not others**, since the pager renders the two cases differently.
 * - **a photo for the housemate too**, so deleting one bunny can be seen not to take the other's.
 */
private suspend fun seedPhotos(
    photos: PhotoRepository,
    cacheDir: File,
    lily: String,
    sznycel: String,
    now: Instant,
) {
    val samples =
        listOf(
            SamplePhoto(lily, 1600, 1200, 0xFF8D6E63.toInt(), takenDaysAgo = null, caption = "Flopped on the rug"),
            SamplePhoto(lily, 1200, 1600, 0xFF6D4C41.toInt(), takenDaysAgo = null),
            SamplePhoto(sznycel, 1600, 1200, 0xFF9E9D24.toInt(), takenDaysAgo = null),
            // Added last, taken first: the two rows that prove the gallery orders by capture date.
            SamplePhoto(lily, 1600, 1200, 0xFF00796B.toInt(), takenDaysAgo = 200, caption = "First week home"),
            SamplePhoto(lily, 1200, 1600, 0xFF5D4037.toInt(), takenDaysAgo = 320),
        )

    samples.forEach { sample ->
        val source = writeSampleJpeg(cacheDir, sample, now)
        val id = photos.add(sample.bunnyId, source)
        sample.caption?.let { photos.setCaption(id, it) }
        // The original is rubbish the moment the pipeline has re-encoded it.
        source.path?.let { File(it).delete() }
    }
}

private data class SamplePhoto(
    val bunnyId: String,
    val width: Int,
    val height: Int,
    val colour: Int,
    val takenDaysAgo: Long?,
    val caption: String? = null,
)

/**
 * One generated JPEG: a flat colour with a lighter band down one side, which is enough to tell the
 * square grid crop apart from the pager's whole frame. A real `DateTimeOriginal` is written with
 * `saveAttributes` when the sample carries one, so the media pipeline reads the same tag a camera
 * would rather than a value handed to it directly.
 */
private fun writeSampleJpeg(
    cacheDir: File,
    sample: SamplePhoto,
    now: Instant,
): Uri {
    val bitmap = createBitmap(sample.width, sample.height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(sample.colour)
    canvas.drawRect(
        0f,
        0f,
        sample.width / 4f,
        sample.height.toFloat(),
        Paint().apply { color = 0x33FFFFFF },
    )

    val file = File(cacheDir, "sample-${UUID.randomUUID()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

    sample.takenDaysAgo?.let { days ->
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, EXIF_DATE.format(now.daysAgo(days)))
            saveAttributes()
        }
    }
    return Uri.fromFile(file)
}

/** EXIF's own format: local wall time, no zone — which is why the pipeline has to assume one. */
private val EXIF_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * The document half, matching the medication and photo halves above: generated pages through
 * [DocumentRepository.add], which means through `MediaFiles.persist(Document)` — **no ML Kit
 * anywhere**, so this runs on an emulator without Play services exactly as it does on the phone.
 *
 * The set is chosen for what it makes visible:
 *
 * - **A three-page document**, because a document is its pages and one-page fixtures never show the
 *   pager, the page counter or the reorder dialog.
 * - **One attached to a visit and one not**, which is the pair that makes "detaching leaves the
 *   document with its bunny" checkable rather than argued.
 * - **One with a date on the page and one without**, since `Dated` and `Scanned` are deliberately
 *   different words and only a fixture with both shows that they are.
 * - **One for the housemate**, so deleting one bunny can be seen not to take the other's paperwork.
 * - **One page above the 3000 px cap**, so `MediaKind.Document`'s spec is actually exercised rather
 *   than passed through.
 *
 * It also exists to put **weight on disk**: PLAN 5h admits documents into Auto Backup under a
 * ~25 MB ceiling, and hand-scanning enough vet printouts to breach one is not a test anybody would
 * re-run. The pages are drawn with text-like bars and speckle rather than flat colour for the same
 * reason — a flat fill compresses to almost nothing and would make the fixture weigh nothing too,
 * which is the opposite of useful for a budget.
 */
private suspend fun seedDocuments(
    documents: DocumentRepository,
    cacheDir: File,
    lily: String,
    sznycel: String,
    visits: SeededVisits,
    now: Instant,
) {
    val random = Random(SAMPLE_SEED)

    suspend fun scan(
        bunnyId: String,
        title: String,
        pages: Int,
        visitId: String? = null,
        datedDaysAgo: Long? = null,
        wide: Boolean = false,
    ) {
        val sources = List(pages) { page -> writeSamplePage(cacheDir, random, page + 1, pages, wide) }
        documents.add(
            bunnyId = bunnyId,
            title = title,
            pages = sources,
            visitId = visitId,
            capturedAt = datedDaysAgo?.let { now.daysAgo(it) },
        )
        // The originals are rubbish the moment the pipeline has re-encoded them.
        sources.forEach { source -> source.path?.let { File(it).delete() } }
    }

    scan(
        bunnyId = lily,
        title = "Vaccination record",
        pages = 2,
        visitId = visits.vaccination,
        // The date on the page is the visit's, months before this fixture was seeded — which is what
        // makes the list's COALESCE ordering visible rather than merely believed.
        datedDaysAgo = 213,
    )
    scan(
        bunnyId = lily,
        title = "Dental X-ray report",
        pages = 3,
        visitId = visits.molars,
        datedDaysAgo = 9,
        // The one page above the long-edge cap, so the downsample is exercised on real bytes.
        wide = true,
    )
    // Deliberately attached to nothing and dated by nothing: the two states the screens render
    // differently, and the row the attach picker is allowed to offer.
    scan(bunnyId = lily, title = "Pet insurance policy", pages = 1)
    scan(
        bunnyId = sznycel,
        title = "Eye drops instructions",
        pages = 1,
        visitId = visits.sznycelEye,
        datedDaysAgo = 40,
    )
}

/**
 * One generated page: a header bar, ruled "text" of varying length, a small table block and fine
 * speckle.
 *
 * The speckle is load-bearing rather than decorative — JPEG reduces a flat fill to a few kilobytes,
 * and a fixture whose documents weigh nothing cannot exercise a storage budget. The bars are what
 * make the pinch-zoom review meaningful: something has to be small enough that zooming in is the
 * only way to tell whether it is legible.
 *
 * No EXIF date is written, unlike the photo fixture. A document's date is what is *printed* on the
 * page, which no camera tag can know — the seeder passes it to `capturedAt` explicitly for the same
 * reason the app makes the owner type it.
 */
private fun writeSamplePage(
    cacheDir: File,
    random: Random,
    page: Int,
    ofPages: Int,
    wide: Boolean,
): Uri {
    // A4 at ~200 dpi, or once above `MediaKind.Document`'s 3000 px cap so the reduction runs.
    val width = if (wide) 3200 else 1654
    val height = if (wide) 4525 else 2339

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFF7F5F0.toInt())

    val ink = Paint().apply { color = 0xFF2B2B2B.toInt() }
    val faint = Paint().apply { color = 0xFFBFB9AE.toInt() }
    val margin = width * 0.08f
    val lineHeight = height * 0.018f

    // A header block, so a thumbnail of page one is distinguishable from page two at 56 dp.
    canvas.drawRect(margin, margin, margin + width * 0.45f, margin + lineHeight * 2f, ink)
    // A page marker down the side — the fixture's own "page 2 of 3", visible while paging.
    canvas.drawRect(
        width - margin - width * 0.04f * page,
        margin,
        width - margin,
        margin + lineHeight,
        faint,
    )

    var y = margin + lineHeight * 5f
    while (y < height - margin - lineHeight * 8f) {
        val runLength = 0.35f + random.nextFloat() * 0.55f
        canvas.drawRect(margin, y, margin + (width - margin * 2f) * runLength, y + lineHeight * 0.42f, ink)
        y += lineHeight * 1.6f
    }

    // A table, which is the shape of every lab result and the thing zoom exists for.
    val tableTop = height - margin - lineHeight * 7f
    for (row in 0..5) {
        val rowY = tableTop + row * lineHeight
        canvas.drawRect(margin, rowY, width - margin, rowY + 2f, faint)
    }

    // Speckle: enough entropy that JPEG cannot flatten the page to a few kilobytes, which is what
    // makes this fixture weigh what a real scan weighs. One rectangle per dot is slow at any real
    // density, so they are sparse and large rather than per-pixel noise.
    val dots = (width * height) / 900
    repeat(dots) {
        val x = random.nextFloat() * width
        val dotY = random.nextFloat() * height
        val shade = 0xFF000000.toInt() or (random.nextInt(0x60, 0xE0) * 0x010101)
        canvas.drawRect(x, dotY, x + 3f, dotY + 3f, Paint().apply { color = shade })
    }

    val file = File(cacheDir, "sample-page-$page-of-$ofPages-${UUID.randomUUID()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    bitmap.recycle()
    return Uri.fromFile(file)
}

/**
 * The observation half: three entries carrying the cases the timeline is reviewed against.
 *
 * - **A shared observation with individual facts that differ** — which is the whole tray/individual
 *   split on one card: the droppings appear once, and only Lily is subdued and hunched. Reviewing
 *   the collapse needs a group whose participants genuinely disagree, or a bug that copied one
 *   bunny's mood onto the other would look correct.
 * - **A healthy day**, written through exactly the shortcut's own field set, so what the button
 *   claims and what the timeline shows can be compared without tapping it.
 * - **A solo observation recording "looked, none seen"** — `symptomsChecked` with no links, the one
 *   state the join table cannot express (ADR-0010), and the only way to see that it renders as an
 *   affirmative rather than as a blank.
 */
private suspend fun seedObservations(
    observations: ObservationRepository,
    symptoms: SymptomRepository,
    lily: String,
    sznycel: String,
    now: Instant,
) {
    // A built-in's id is minted by the seed callback, so it can only be looked up by key here.
    val symptomIdsByKey = symptoms.allNow().filter { it.key != null }.associate { it.key!! to it.id }

    val shared =
        observations.add(
            participants = listOf(lily, sznycel),
            recordedAt = now.daysAgo(1, OBSERVATION_TIME),
            facts =
                ObservationFacts(
                    // One tray, one real-world fact — and a worrying one, matching the weight drop.
                    // One value in each set, deliberately: the two fields went multi-valued in
                    // ADR-0029 and this seed did **not** change with them — 61 matrix scenes, the
                    // before/after comparison and the Play listing screenshots all rest on it. A
                    // tray with two appearance values is reached through a seed variant instead.
                    tray =
                        TrayFacts(
                            droppingsAmount = DroppingsAmount.FEW,
                            droppingsSizes = setOf(DroppingsSize.SMALL),
                            droppingsAppearance = setOf(DroppingsAppearance.ROUND),
                            cecotropes = Cecotropes.LEFT_UNEATEN,
                        ),
                ),
        )
    // Individual facts for Lily alone, through the path that touches exactly one row.
    observations.updateIndividual(
        observationId = shared.first(),
        individual =
            IndividualFacts(
                appetite = Appetite.REDUCED,
                mood = Mood.SUBDUED,
                activity = ActivityLevel.QUIET,
                water = WaterIntake.LESS,
                note = "Sitting hunched in the corner, not interested in greens.",
                symptomIds = setOfNotNull(symptomIdsByKey["hunched_posture"], symptomIdsByKey["loud_teeth_grinding"]),
            ),
    )

    observations.add(
        participants = listOf(lily, sznycel),
        recordedAt = now.daysAgo(3, OBSERVATION_TIME),
        facts = healthyDayFacts(),
    )

    observations.add(
        participants = listOf(sznycel),
        recordedAt = now.daysAgo(5, OBSERVATION_TIME),
        facts =
            ObservationFacts(
                tray = TrayFacts(droppingsAmount = DroppingsAmount.NORMAL),
                // Looked, none seen — affirmative, and distinguishable in the database from never
                // having opened the picker.
                individual = IndividualFacts(mood = Mood.BRIGHT, symptomsChecked = true),
            ),
    )
}

/**
 * A year of uneven, back-dated weighings carrying the four cases the chart and the flag are reviewed
 * against:
 *
 * - **uneven intervals** — roughly weekly with jitter, so the chart cannot be plotted by list index
 *   without visibly lying (house rule);
 * - **a fat-fingered entry** — `250` typed for `2500`, which the trailing *median* must resist
 *   (ADR-0021);
 * - **a tied `recordedAt`** — two rows at the same minute, which only the stated total order
 *   separates;
 * - **a long gap before an acute drop**, which must still fire: damping by elapsed time is the one
 *   thing ADR-0001 says must never silence this signal.
 */
private fun lilySeries(): List<Pair<Long, Int>> {
    val random = Random(SAMPLE_SEED)
    val series = mutableListOf<Pair<Long, Int>>()

    // Roughly weekly from a year ago until five months ago, wobbling around 2 500 g.
    var daysAgo = 365L
    while (daysAgo > 150L) {
        series += daysAgo to 2500 + random.nextInt(-40, 41)
        daysAgo -= random.nextInt(5, 10).toLong()
    }

    series += 203L to 2495 // the tie: same day, same clock time as the row below
    series += 203L to 2510
    series += 180L to 250 // the fat-fingered one — a 2 500 g bunny cannot weigh 250 g

    // Then nothing at all for five months: the owner stopped weighing, which is a period nobody
    // recorded rather than evidence of anything (ADR-0001).
    series += 21L to 2490
    series += 14L to 2470
    series += 7L to 2480
    series += 2L to 2300 // ~7.6 % below the 2 480 g baseline: the flag fires

    return series
}

/**
 * `n` days back, stamped at a fixed clock time in the phone's own zone.
 *
 * Kotlin note: `at` is a *defaulted* parameter, so every weighing call site stays `daysAgo(21)` and
 * only the observations pass a second argument — no overload, and no call site to keep in step.
 */
private fun Instant.daysAgo(
    days: Long,
    at: LocalTime = WEIGHING_TIME,
): Instant =
    atZone(ZoneId.systemDefault())
        .minusDays(days)
        .with(at)
        .toInstant()
        .truncatedTo(ChronoUnit.MINUTES)
