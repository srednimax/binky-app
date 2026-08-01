package app.binky.tracker.work

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/** How an ask ended. Three outcomes, because the third one needs a different button. */
enum class NotificationPermissionOutcome {
    Granted,

    /** Refused, and askable again — Android permits two denials before it stops showing the dialog. */
    Denied,

    /**
     * Refused for good, or switched off in settings. The system dialog will never appear again, so
     * the only way back is the app's own settings screen.
     */
    PermanentlyDenied,
}

/**
 * **The app's one ask for `POST_NOTIFICATIONS`, and the only caller of a permission request
 * anywhere in it.**
 *
 * One function because Android permits only two denials before the dialog stops appearing, and
 * ADR-0006's arithmetic only works if there is a single place spending them. Both hosts — first-run
 * setup's third step and the point-of-use sheet — call this; they cannot both fire, because they
 * are the same composable in two places rather than two asks that happen to agree.
 *
 * **Permanent refusal is recognised from the result, not guessed at beforehand.**
 * `shouldShowRequestPermissionRationale` is famously ambiguous *before* an ask — it returns false
 * both for "never asked" and for "asked twice and refused" — but after a denial it is exact. So the
 * request is fired and the answer read: denied with a rationale still available is an ordinary no,
 * denied with none left is Android saying it will not ask again, and the caller offers app settings
 * instead of a button that would silently do nothing. ADR-0006's two-denial arithmetic is about not
 * *spending* both; this is the case after they are spent, and it is the one the owner actually hits.
 *
 * Kotlin note: this returns a lambda rather than performing anything. `rememberLauncherForActivityResult`
 * has to be called during composition — it registers a result callback with the Activity — but the
 * *launch* belongs to a button press, so the composable hands back the trigger. Roughly a React hook
 * returning a stable callback.
 */
@Composable
fun rememberNotificationPermissionAsk(onOutcome: (NotificationPermissionOutcome) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onOutcome(
                when {
                    granted -> NotificationPermissionOutcome.Granted
                    activity != null &&
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) -> NotificationPermissionOutcome.Denied
                    else -> NotificationPermissionOutcome.PermanentlyDenied
                },
            )
        }

    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Below API 33 there is no runtime permission: notifications are granted at install and
            // the only way off is the settings switch, which nothing can re-ask for. So the answer
            // is already known, and "off" is permanent in the only sense that matters — app settings
            // is the sole way back.
            onOutcome(
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    NotificationPermissionOutcome.Granted
                } else {
                    NotificationPermissionOutcome.PermanentlyDenied
                },
            )
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * This app's page in Android's settings — where a permanently refused permission can still be turned
 * back on, and the only remaining route once the system dialog is spent.
 */
fun Context.openAppNotificationSettings() {
    val actions =
        listOf(
            // The notification page directly, which is where the switch actually is.
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            // The app's details page, one tap further away and present on every device.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    for (intent in actions) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Not on this device — try the next one.
        } catch (e: SecurityException) {
            // Present but not exported to us, which is the same outcome from here.
        }
    }
}
