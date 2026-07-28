package app.binky.tracker.data

/**
 * What this phone has *recorded* about first-run setup (ADR-0006).
 *
 * **Absent is the third state, and it carries the most meaning**: it says nobody has recorded
 * anything, which is true both of a fresh install and of an install that predates the wizard
 * existing. [resolveSetupState] is where that ambiguity is settled.
 *
 * Two values rather than a `Boolean` because a boolean would have to encode "shown but not
 * finished" as `false`, and "false" is the value someone reads as "not set up" six months from now.
 * Stored by name like every other enum in this app, so adding a value cannot rewrite history.
 */
enum class SetupProgress {
    /** The wizard has been put in front of the owner and has not been finished. */
    Started,

    /** The owner reached the end of it. */
    Complete,
}

/**
 * Whether the app still owes the owner first-run setup (ADR-0006).
 *
 * Kotlin note: a plain `enum` rather than a sealed interface, because no case carries a payload —
 * this is the closed three-value vocabulary a discriminated union would be overkill for.
 */
enum class SetupState {
    /** Before the first preferences and database emissions. Never returned by [resolveSetupState]. */
    Loading,

    /** The wizard has not been through, and there is no evidence it ever needed to be. */
    Required,

    Complete,
}

/**
 * Whether setup has to run, **resolved on read rather than merely stored**.
 *
 * [SetupProgress] is absent until the wizard is first shown, and an absent record does not mean
 * "never set up" — it means *nobody has answered yet*, which is a different claim. Resolving it
 * against whether a bunny exists is Phase 1's [resolveSelection] idiom reused, and it settles two
 * cases with one rule:
 *
 * - the author's existing debug install, which predates the wizard and holds a year of real
 *   history, is never made to meet it;
 * - a phone that has just restored a backup is not asked to set the app up again — the records are
 *   already there, and a wizard in front of them would read as data loss.
 *
 * The second case has a **second, independent mechanism** behind it: the record itself is a
 * preference, and preferences travel in every export scope from Essential upward (ADR-0005), so a
 * restore normally carries the answer with it. This resolver is what covers the restore that does
 * not — Android's Auto Backup onto a phone whose app data arrived without it, or an owner restoring
 * an archive made before this release.
 *
 * **[hasBunny] is consulted in exactly one branch, and that is the point.** It answers a question
 * about the *past* — did this install exist before the wizard did — so it may only be asked while
 * nothing has been recorded. Asked in the [SetupProgress.Started] branch too it would answer a
 * completely different question, because the wizard's own first step creates a bunny: adding one
 * would end the wizard halfway through itself and take the backup step with it. Which is precisely
 * what it did, on the phone, the first time this was run by hand.
 *
 * @param progress what the phone has recorded: `null` until the wizard is first shown.
 * @param hasBunny whether any bunny exists at all, archived ones included — an owner whose only
 *   bunny is archived has plainly used the app before (ADR-0004).
 */
fun resolveSetupState(
    progress: SetupProgress?,
    hasBunny: Boolean,
): SetupState =
    when (progress) {
        SetupProgress.Complete -> SetupState.Complete
        // Shown and not finished. It stays owed whatever else has happened on the phone since, and
        // it survives a process death mid-wizard, because it is written down rather than held in
        // composition.
        SetupProgress.Started -> SetupState.Required
        null -> if (hasBunny) SetupState.Complete else SetupState.Required
    }
