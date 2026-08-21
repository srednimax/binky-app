package app.binky.tracker.ui.settings

import androidx.compose.runtime.Composable

/**
 * **The release build's answer to the debug section: nothing at all** (Phase 9, `9k`).
 *
 * `SettingsScreen` lives in `main/` and is compiled into both builds, so the debug affordances need
 * *some* symbol here for it to call. This is that symbol, and it is empty — which is the difference
 * between a strip and a hide. The old `if (BuildConfig.DEBUG)` guard left `SampleData.kt`,
 * `DebugReminder.kt` and the section itself in the release AAB, unreachable but present, because
 * `isMinifyEnabled = false` means nothing removes a branch that is statically false. With the seam
 * here instead, the release variant never compiles any of it.
 *
 * Kotlin note: `= Unit` is an expression-bodied function returning nothing — the idiomatic empty
 * body, and it keeps the whole no-op on one line so there is nothing to accidentally add to.
 *
 * If this ever needs to become more than a no-op, it has become a shipped feature, and it belongs
 * in `main/` rather than here.
 */
@Composable
fun DebugSettings() = Unit
