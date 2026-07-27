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
    // Phase 4 adds UNDER_WATCH here, and nothing else changes.
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
 * that could drop them would produce an observation covering nobody.
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
): ParticipantPreSelection {
    val candidates = mutableListOf(ParticipantCandidate(subject.id, subject.name))
    val excluded = mutableListOf<ExcludedParticipant>()

    for (member in fluffleMembers.sortedBy { it.name.lowercase() }) {
        if (member.id == subject.id) continue
        // Kotlin note: `when` used as an expression here, not a statement — each branch yields the
        // exclusion reason or null, so adding Phase 4's Watch predicate is one branch above this one
        // rather than a new `if` somewhere else in the function.
        val reason =
            when {
                member.archivedAt != null -> ParticipantExclusion.ARCHIVED
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
