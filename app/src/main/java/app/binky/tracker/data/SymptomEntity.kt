package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.util.UUID

/**
 * One symptom the owner can tick on an observation.
 *
 * A table rather than an enum, unlike droppings and mood, because bunnies develop quirks no built-in
 * list anticipates and the ones an owner bothers to write down are usually the recurring ones — and a
 * symptom that can only be typed into a note can never answer "how often has this happened?", which
 * is the reason symptoms are a field at all rather than prose (ADR-0010).
 *
 * **[key] and [label] are mutually exclusive, and that pair *is* the built-in/owner-added
 * distinction.** A built-in carries a stable key and stores no label, so its display text resolves
 * through `strings.xml` and therefore translates like all other UI text (ADR-0013). An owner-added
 * row carries its literal text and no key; that one is untranslatable, as expected. There is
 * deliberately **no `ownerCreated` flag** — `key == null` already says it, and a second column could
 * only drift out of step with the first.
 *
 * An observation references the **stable [id]**, never the display text, so a symptom can be renamed
 * or translated without orphaning its history.
 *
 * [hiddenAt] retires a symptom from the picker without deleting it: historical observations still
 * resolve it. Built-ins are retired by hiding, never by deleting.
 */
@Entity(
    tableName = "symptoms",
    indices = [
        // **Unique**, and the `INSERT OR IGNORE` reconciliation below is why: without a conflict to
        // ignore it would insert the entire built-in list on every launch (ADR-0010). Safe on a
        // nullable column because SQLite treats NULLs as distinct — it pins the built-ins to one row
        // each while permitting unlimited owner rows, all of which have a NULL key.
        Index(value = ["key"], unique = true),
    ],
)
data class SymptomEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Non-null for a built-in. The stable identity behind a `strings.xml` label. */
    val key: String? = null,
    /** Non-null for an owner-added symptom. Stored literally, and therefore untranslatable. */
    val label: String? = null,
    /** Set when retired from the picker. Hiding is not deleting (ADR-0010). */
    val hiddenAt: Instant? = null,
)

/**
 * The built-in symptom list (ADR-0010), as **stable keys rather than English strings**.
 *
 * *Loud* teeth grinding is named deliberately: soft tooth purring means a content bunny and loud
 * grinding means pain, so an unqualified "teeth grinding" would invert the meaning. The key spells
 * that out so the label cannot be softened by accident.
 *
 * Adding a key here is all it takes to ship a new built-in: the reconciliation below inserts it on
 * the next launch. Removing one leaves the row in place, which is correct — an owner's history keeps
 * resolving it.
 */
val BUILT_IN_SYMPTOM_KEYS: List<String> =
    listOf(
        "head_tilt",
        "drooling_or_wet_chin",
        "sneezing_or_nasal_discharge",
        "eye_discharge",
        "dirty_bottom",
        "loud_teeth_grinding",
        "hunched_posture",
        "laboured_breathing",
        "not_drinking",
        "limping",
        "ear_scratching",
        "blood_in_urine",
        "hiding_more_than_usual",
    )

/**
 * Seeds the built-in symptoms on create and **reconciles them on open** (ADR-0010), so the list in
 * code stays identical to the list in the database once wipes stop being free after Phase 3.
 *
 * Both hooks call the same statement, and the redundancy is deliberate rather than sloppy: `onOpen`
 * alone would in fact cover every case, including the one that matters most — after ADR-0007's
 * destructive wipe Room drops and recreates the tables inside `onUpgrade`, so **`onCreate` does not
 * fire** and a seed hung only on it would leave an owner with an empty picker. Stating create
 * separately keeps the intent readable at the cost of two lines that can never be wrong.
 *
 * `INSERT OR IGNORE` keyed on the unique index means this is idempotent, matches on `key` and so
 * leaves a hidden symptom's `hiddenAt` untouched — an owner who retired "limping" does not get it
 * back at the next launch.
 *
 * Raw SQL rather than the DAO because a Room callback runs on the database thread with no coroutine
 * to suspend in: it is handed a [SupportSQLiteDatabase], not a DAO.
 */
fun builtInSymptomSeedCallback(): RoomDatabase.Callback =
    object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            reconcileBuiltInSymptoms(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            reconcileBuiltInSymptoms(db)
        }
    }

private fun reconcileBuiltInSymptoms(db: SupportSQLiteDatabase) {
    for (key in BUILT_IN_SYMPTOM_KEYS) {
        db.execSQL(
            "INSERT OR IGNORE INTO symptoms (id, `key`, label, hiddenAt) VALUES (?, ?, NULL, NULL)",
            arrayOf(UUID.randomUUID().toString(), key),
        )
    }
}
