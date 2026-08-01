package app.binky.tracker.data

/*
 * Who an observation covers by default, and **why anybody was left out**.
 *
 * Built as a filter with a stated reason per exclusion even though Phase 2 has only one exclusion to
 * state, because ADR-0008 already names the next: a bunny under an active Watch is dropped from the
 * healthy day's pre-selection, with the reason shown — *"Clover is under a watch — log for her
 * separately."* Written as a filter, that is one predicate added in Phase 4. Written as
 * `fluffleMembers.filter { it.archivedAt == null }`, it is a rework of every call site.
 *
 * Pure Kotlin, no Android: the reasons are an enum the UI resolves through `strings.xml` (ADR-0013),
 * so this file holds the rule and none of the wording.
 */

/** Why a fluffle member was left out of the pre-selection. */
enum class ParticipantExclusion {
    /**
     * Archived. Its records are read-only (ADR-0004), and ADR-0008 says archival "drops it from
     * future pre-selection while leaving its past shared observations intact" — the bunny genuinely
     * did live with the others, and none of that history moves. Only new writes stop.
     */
    ARCHIVED,

    /**
     * Under a **running** watch (ADR-0008, ADR-0001), which is the exclusion 2f built this filter
     * for. The unreviewed one-tap write path must not sweep a separated, ill bunny into a shared
     * tray fact — if they are being watched, the owner has singled them out, and the tray they are
     * using may not be the tray the others are.
     *
     * A **flagged** bunny is still covered, as 2f decided, and the distinction is the point: the
     * flag is about *weight*, where a bunny losing weight with entirely normal droppings is real and
     * useful data. The watch is about the owner having separated this bunny out.
     *
     * An **expired** watch excludes nobody. Expiry stops the nagging on its own, and the prompt that
     * follows is only about re-arming — leaving a bunny out of the healthy day because nobody has
     * answered a dialog yet would be the app acting on a question it has not asked.
     */
    UNDER_WATCH,
}

/** A bunny the form offers as a participant, ticked when the form opens. */
data class ParticipantCandidate(
    val bunnyId: String,
    val name: String,
)

/** A fluffle member the form deliberately did not offer, and the reason to say so. */
data class ExcludedParticipant(
    val bunnyId: String,
    val name: String,
    val reason: ParticipantExclusion,
)

/**
 * The pre-selection for an observation about [subject].
 *
 * [fluffleMembers] is who currently lives with them — the **current** fluffle, never derived from
 * past observations (ADR-0008): the group an observation covered is an immutable historical fact
 * stamped at creation, and re-deriving it from today's living arrangement would silently rewrite
 * history the first time a bond broke.
 *
 * [subject] is always a candidate and never excluded. The owner asked to log for this bunny; a rule
 * that could drop them would produce an observation covering nobody — and that holds for a *watched*
 * subject too. Starting a watch on Bijou and then logging for Bijou is the ordinary case; the
 * exclusion is about not sweeping a watched **housemate** in alongside them.
 *
 * `activelyWatchedIds` is who is under a running watch, resolved by the caller against the clock —
 * an expired one excludes nobody (see [ParticipantExclusion.UNDER_WATCH]).
 */
data class ParticipantPreSelection(
    val candidates: List<ParticipantCandidate>,
    val excluded: List<ExcludedParticipant>,
) {
    val bunnyIds: List<String> get() = candidates.map { it.bunnyId }
}

fun preSelectParticipants(
    subject: BunnyEntity,
    fluffleMembers: List<BunnyEntity>,
    activelyWatchedIds: Set<String> = emptySet(),
): ParticipantPreSelection {
    val candidates = mutableListOf(ParticipantCandidate(subject.id, subject.name))
    val excluded = mutableListOf<ExcludedParticipant>()

    for (member in fluffleMembers.sortedBy { it.name.lowercase() }) {
        if (member.id == subject.id) continue
        // Kotlin note: `when` used as an expression here, not a statement — each branch yields the
        // exclusion reason or null, which is what made Phase 4's Watch predicate one branch rather
        // than a new `if` somewhere else in the function. Archived is asked first because it is the
        // stronger fact: archiving closes any watch, so the two cannot both be true of a live row,
        // and if they ever were, "archived" is the answer that explains more.
        val reason =
            when {
                member.archivedAt != null -> ParticipantExclusion.ARCHIVED
                member.id in activelyWatchedIds -> ParticipantExclusion.UNDER_WATCH
                else -> null
            }
        if (reason == null) {
            candidates += ParticipantCandidate(member.id, member.name)
        } else {
            excluded += ExcludedParticipant(member.id, member.name, reason)
        }
    }

    return ParticipantPreSelection(candidates = candidates, excluded = excluded)
}
