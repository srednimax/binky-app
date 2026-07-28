package app.binky.tracker.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Android's own backup settings, where the switch this app cannot read — or set — actually lives.
 *
 * **Best-effort by design** (ADR-0005). There is no public, guaranteed action for this screen: AOSP
 * has moved it between Privacy and System over the years, and HyperOS moves it again. So this walks
 * a list, most specific first, and ends at the top level of Settings, which every device has. The
 * owner lands one tap further away on an odd phone rather than tapping a button that does nothing.
 *
 * `startActivity` in a loop rather than `resolveActivity`: package-visibility filtering on Android 11
 * and up can make a query return null for an activity that would have launched perfectly well, and a
 * lookup that lies in the safe direction still costs the owner the same dead button.
 */
fun Context.openSystemBackupSettings() {
    val actions =
        buildList {
            // What a number of OEM builds register for the old Backup & reset screen.
            add("android.settings.BACKUP_AND_RESET_SETTINGS")
            // AOSP's own, on builds that still export it.
            add("android.settings.BACKUP_SETTINGS")
            // Up to Android 11 this action *was* Backup & reset. From 12 it is the Privacy
            // dashboard — permissions, microphone access, no backup switch anywhere on it — which
            // is worse than the fallback below: it looks like the destination and is not. Checked
            // on the Xiaomi, where it is the only one of the three that resolves at all.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) add(Settings.ACTION_PRIVACY_SETTINGS)
            // Always present, and every Settings app of this era opens on a search field.
            add(Settings.ACTION_SETTINGS)
        }

    for (action in actions) {
        try {
            // NEW_TASK, so Settings opens as its own task rather than being pushed onto Binky's.
            // Without it the owner's back stack becomes Binky-then-three-Settings-screens, and the
            // app cannot be brought forward again while they are in there — watched happening on
            // the Xiaomi, which is the only way this kind of thing is ever noticed.
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Not on this device — try the next one.
        } catch (e: SecurityException) {
            // Present but not exported to us, which is the same outcome from here.
        }
    }
}
