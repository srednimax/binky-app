package app.binky.tracker.data

/*
 * What an observation records, split the way ADR-0008 splits it — deliberately *not*
 * `ObservationEntity`.
 *
 * The entity is one row per bunny; these are the facts a *write* is made of, and separating them is
 * what makes the shared write's rule expressible in a signature rather than in a comment: tray facts
 * go onto every participant identically, individual facts belong to one row. A single flat parameter
 * list would let a caller set one bunny's droppings, which is the false attribution ADR-0008 exists
 * to prevent, arriving through the API instead of through the UI.
 *
 * Plain Kotlin with no Room and no Android in this file, for the same reason `WeightTrend.kt` keeps
 * them out: the healthy-day field set is then a JVM test on a data class rather than an instrumented
 * one with a database behind it.
 */

/**
 * The facts read off **one litter tray** — the reason a shared observation is shared at all.
 *
 * Every field defaults to empty, and for the single-valued two that means `null`, which *is* "not
 * checked" (ADR-0001). An untouched form therefore records nothing rather than a silent "normal".
 *
 * [droppingsSizes] and [droppingsAppearance] are **sets**, because one tray genuinely holds more than
 * one kind at once — round pellets *and* soft ones is the commonest early sign of a gut going wrong
 * (ADR-0029). An empty set is the same absence a `null` is, spelt as zero join rows; the app still
 * never reads it as *normal*.
 */
data class TrayFacts(
    val droppingsAmount: DroppingsAmount? = null,
    val droppingsSizes: Set<DroppingsSize> = emptySet(),
    val droppingsAppearance: Set<DroppingsAppearance> = emptySet(),
    val cecotropes: Cecotropes? = null,
    /** Relative to `filesDir`, under `observations/`. See [ObservationEntity.trayPhotoPath]. */
    val trayPhotoPath: String? = null,
)

/**
 * The facts that belong to **one bunny** — the ones that legitimately differ between two rabbits
 * sharing a tray.
 */
data class IndividualFacts(
    val appetite: Appetite? = null,
    val mood: Mood? = null,
    val activity: ActivityLevel? = null,
    val water: WaterIntake? = null,
    val note: String? = null,
    /** Whether the owner looked for symptoms. See [ObservationEntity.symptomsChecked]. */
    val symptomsChecked: Boolean = false,
    val symptomIds: Set<String> = emptySet(),
) {
    /**
     * The same facts with the one pair that cannot both be true resolved: **any symptom link implies
     * the owner looked** (ADR-0010).
     *
     * [ObservationRepository] passes every write through this rather than trusting the caller, which
     * is what makes "enforced, not merely expected" true of the database and not just of the form
     * that happens to be on screen today. A blank note collapses to `null` here too, so "" and `null`
     * do not become two spellings of "nothing written".
     */
    fun normalised(): IndividualFacts =
        copy(
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            symptomsChecked = symptomsChecked || symptomIds.isNotEmpty(),
        )
}

/** Both halves of one write, so a caller cannot pass the tray facts of one observation with the individual facts of another. */
data class ObservationFacts(
    val tray: TrayFacts = TrayFacts(),
    val individual: IndividualFacts = IndividualFacts(),
)

/**
 * The tray half of a stored row, for the paths that copy it onto a new participant.
 *
 * The two sets are **parameters rather than fields**, because they are not on the row: they are join
 * rows the caller has to have read (ADR-0029). Deliberately without defaults, so a call site cannot
 * quietly produce a tray fact with the sets silently missing — the same reasoning that keeps the
 * symptom links off [individualFacts].
 */
fun ObservationEntity.trayFacts(
    droppingsSizes: Set<DroppingsSize>,
    droppingsAppearance: Set<DroppingsAppearance>,
) = TrayFacts(
    droppingsAmount = droppingsAmount,
    droppingsSizes = droppingsSizes,
    droppingsAppearance = droppingsAppearance,
    cecotropes = cecotropes,
    trayPhotoPath = trayPhotoPath,
)

/**
 * The individual half of a stored row. Deliberately **without** the symptom links — they live in the
 * join table, so a caller that needs them asks for them and cannot mistake an empty set here for
 * "this observation has no symptoms".
 */
fun ObservationEntity.individualFacts() =
    IndividualFacts(
        appetite = appetite,
        mood = mood,
        activity = activity,
        water = water,
        note = note,
        symptomsChecked = symptomsChecked,
    )

/**
 * Applies the **column half** of the tray facts to a row. Used by both the shared write and the
 * add-a-participant path.
 *
 * It can no longer carry the whole tray fact by itself: the two sets are join rows, written per
 * participant by [ObservationRepository] inside the same transaction (ADR-0029). That is the cost of
 * multi-valuing them, and it is stated here because a `copy()` that silently carried four fifths of a
 * fact would be the easiest possible place to lose one.
 */
fun ObservationEntity.withTrayFacts(tray: TrayFacts) =
    copy(
        droppingsAmount = tray.droppingsAmount,
        cecotropes = tray.cecotropes,
        trayPhotoPath = tray.trayPhotoPath,
    )

/** Applies individual facts to a row. The symptom links are written separately, by the repository. */
fun ObservationEntity.withIndividualFacts(individual: IndividualFacts) =
    copy(
        appetite = individual.appetite,
        mood = individual.mood,
        activity = individual.activity,
        water = individual.water,
        note = individual.note,
        symptomsChecked = individual.symptomsChecked,
    )
