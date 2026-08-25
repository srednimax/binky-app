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

# ---------------------------------------------------------------------------
# The one keep rule, added 2026-08-25 with the evidence the paragraph above asks for.
#
# **Symptom.** On the phone, under R8, the guided document scanner never opened. It did not crash:
# `MlKitDocumentScanner.start` catches every exception and falls back to the plain camera by design
# (ADR-0009), so the whole defect surfaced as one `I BinkyScanner: Guided scanner unavailable`
# line and an owner quietly losing the feature. An unminified build of the same commit opens
# `com.google.android.gms/.mlkit.docscan.ui.DocumentScanningActivity` on the same phone.
#
# **Cause, read out of the build's own output rather than guessed.** ML Kit finds its components the
# Firebase way: the merged manifest declares
#
#     <service android:name="com.google.mlkit.common.internal.MlKitComponentDiscoveryService">
#       <meta-data android:name="com.google.firebase.components:com.google.mlkit.common.internal.CommonComponentRegistrar"
#                  android:value="com.google.firebase.components.ComponentRegistrar" />
#
# — the registrar's class name is a *fragment of a meta-data key*, and it is instantiated by
# reflection through its no-arg constructor. AGP's `aapt_rules.txt` reads `android:name` on
# components, not class names embedded in meta-data keys, so nothing tells R8 that constructor is
# reachable.
#
# `mapping.txt` shows the class itself surviving unrenamed — `CommonComponentRegistrar ->
# CommonComponentRegistrar` — **with no members under it**: a plain `-keep class` keeps the class and
# lets the shrinker take the members, and the constructor is exactly what got taken. The runtime says
# the same thing in one line:
#
#     W ComponentDiscovery: Caused by: java.lang.NoSuchMethodException:
#         com.google.mlkit.common.internal.CommonComponentRegistrar.<init> []
#
# With discovery failing, the component the scanner client needs is never registered, and building
# the client dies on a `requireNonNull` deep inside
# `com.google.android.gms.internal.mlkit_vision_document_scanner.zztp.<init>` — which is the
# NullPointerException that reached the log, retraced through this build's own mapping.
#
# **Why members and not the class.** The class is already kept; only `<init>()` is missing. So this is
# `-keepclassmembers`, which adds nothing when a registrar is absent and keeps only the constructor
# when one is present. It is scoped to the interface rather than to `com.google.mlkit.**`, because the
# contract being satisfied is Firebase's component discovery and any future registrar has the same
# hole.
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
