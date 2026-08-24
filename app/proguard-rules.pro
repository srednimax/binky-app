# R8 rules for the release build (10c, 2026-08-24).
#
# This file is referenced by `app/build.gradle.kts` and, until 1.9.0, did not exist — harmless only
# while `isMinifyEnabled` was false. It exists now, and it deliberately contains **no keep rules**.
#
# That is a finding, not an omission. Every reflection-shaped thing in this app is already covered by
# a rule someone else ships, and the evidence is in the build's own output rather than in a habit:
#
#   app/build/outputs/mapping/release/configuration.txt   every rule R8 actually ran with
#   app/build/outputs/mapping/release/mapping.txt         what was renamed, and to what
#   app/build/outputs/mapping/release/usage.txt           what was removed
#
# What was checked, and what covers it:
#
#   * **The manifest's classes.** AGP generates `aapt_rules.txt` from the merged manifest, which
#     covers the application, the activity, every receiver and provider — and `android:backupAgent`,
#     so `BinkyBackupAgent` is kept without a rule here. Confirmed: it survives under its own name
#     with `onFullBackup` and `onRestoreFinished` intact. Its failure mode is a backup that silently
#     does nothing, so it was checked rather than assumed.
#
#   * **Workers, across versions.** `androidx.work` ships `-keepnames class * extends
#     androidx.work.ListenableWorker`, which is the rule that matters most here: WorkManager persists
#     the worker's *class name* in its own database, so a pending sweep enqueued by 1.9.0 must still
#     resolve after the update to 1.10. `-keepnames` is what makes the name stable release to release.
#     Confirmed: `ReminderSweepWorker` and `UpdateCatchUpWorker` are unrenamed.
#
#   * **Room.** `-keep class * extends androidx.room.RoomDatabase` plus R8's own handling of the
#     `Class.forName(… + "_Impl")` lookup. Confirmed: `BunnyDatabase_Impl` is unrenamed. The DAOs are
#     renamed, which is fine — nothing looks them up by name.
#
#   * **kotlinx.serialization** — the backup manifest (ADR-0005) and every Nav3 route key. The library
#     ships R8 rules that preserve the `Companion` field and the `serializer()` method, which is the
#     reflective path `serializer(KClass)` takes. Confirmed: `Companion -> Companion` and the
#     `$$serializer` INSTANCE fields survive. Renaming the *classes* is harmless — a `@Serializable`
#     class's `serialName` is a string literal baked in at compile time, so the JSON an owner's
#     archive holds does not change shape when R8 renames the class that reads it.
#
#   * **Enums stored by name** — the house rule, and the one that could silently rewrite history.
#     R8 renames the constant *fields* (`DoseStatus.GIVEN -> e`) but never the name string handed to
#     the enum constructor, because `Enum.valueOf` reads it; `.name` is therefore unchanged. Confirmed
#     by grepping the compiled dex for the constants themselves — `WITHDRAWN`, `LEFT_UNEATEN`,
#     `KILOGRAMS` and the rest are all still there, verbatim. **No rule can pin this**: the usual
#     `-keepclassmembers enum *` keeps field names, which is not what `.name` returns. It is a
#     property of how R8 works, so 10c proves it by behaviour on the phone as well.
#
# The rule to follow when this file next changes: **add a keep only with the mapping or usage output
# that shows something was renamed or removed.** A rule added on suspicion cannot be removed later,
# because nobody can prove it was doing nothing.
