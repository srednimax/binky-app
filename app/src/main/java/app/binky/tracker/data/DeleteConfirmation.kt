package app.binky.tracker.data

/** How many times deleting a bunny must be confirmed (ADR-0004). */
enum class DeleteConfirmation {
    /** One dialog. */
    SINGLE,

    /** Two, the second stating what is actually destroyed. */
    TWO_STAGE,
}

/**
 * The two-stage ceremony is calibrated to destroying **history** — weights, observations, photos,
 * documents, visits, doses. An avatar and the profile fields do not count: the avatar is a
 * photograph the owner still has in their camera roll, and making it trip the same alarm as a year
 * of weighings teaches the owner to click through both dialogs without reading them, which is what
 * costs them on the deletion that is genuinely irreversible (ADR-0004).
 *
 * So through Phase 1, where no record type exists yet, every deletion is a single confirmation —
 * as a consequence of the rule rather than as a special case for the phase.
 */
fun deleteConfirmationFor(counts: RecordCounts): DeleteConfirmation =
    if (counts.soleOwnedRecords > 0 || counts.sharedRecords > 0) {
        DeleteConfirmation.TWO_STAGE
    } else {
        DeleteConfirmation.SINGLE
    }
