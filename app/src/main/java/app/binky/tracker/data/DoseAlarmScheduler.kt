package app.binky.tracker.data

/**
 * What a write does to the app's one pending dose alarm (ADR-0025, PLAN 5f).
 *
 * The rebuild itself lives in `work/DoseAlarm.kt` and needs a `Context`; a repository has none and
 * should not acquire one. So the repositories hold this instead — one method, called after every
 * write that could change which dose is next — and `AppContainer` supplies the implementation that
 * reaches `AlarmManager`.
 *
 * **Wired at the repository rather than at the call sites**, deliberately (ADR-0025). Archiving a
 * bunny, un-archiving one and deleting one all change the answer without touching a medication
 * table, and a delete takes the courses by cascade with no course write happening at all — so a
 * rebuild remembered at each call site is a rebuild that misses exactly those paths, which is how
 * they were missed the first time. Since the rebuild is idempotent and costs one query, hanging it
 * off every write is cheaper than deciding which writes deserve one.
 *
 * Kotlin note: `fun interface` is a single-abstract-method interface, so any lambda of the right
 * shape converts to one — `DoseAlarmScheduler { … }` below is the whole implementation. The
 * abstract member is `suspend`, which is allowed and is what lets the rebuild query Room.
 */
fun interface DoseAlarmScheduler {
    /** Recompute the one pending alarm from truth, and arm, move or cancel it. */
    suspend fun rebuild()

    companion object {
        /**
         * Writes happen; no alarm is touched.
         *
         * The default every repository takes, so a test — or any caller with no phone under it —
         * constructs one without a scheduler and gets a repository that is still correct about the
         * database. `AppContainer` is the only place that passes a real one.
         */
        val None = DoseAlarmScheduler { }
    }
}
