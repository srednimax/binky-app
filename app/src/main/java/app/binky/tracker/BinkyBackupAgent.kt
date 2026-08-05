package app.binky.tracker

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import app.binky.tracker.data.BUNNY_DATABASE_FILE
import app.binky.tracker.data.backup.AUTO_BACKUP_STAGING_PATH
import app.binky.tracker.data.backup.adoptRestoredDatabase
import app.binky.tracker.data.backup.autoBackupFileSet
import app.binky.tracker.data.backup.checkpointDatabaseTo
import app.binky.tracker.data.backup.clearAutoBackupMarker
import app.binky.tracker.data.backup.writeAutoBackupMarker
import java.io.File
import java.time.Instant

/**
 * Android Auto Backup, taking control of its own file set (ADR-0005).
 *
 * Registered with `android:fullBackupOnly="true"`: declaring an agent without it would put the app
 * on the key/value path instead, which is not what ADR-0005 describes and would leave [onBackup]
 * below — a deliberate no-op — as the thing that actually ran.
 *
 * **This class holds no state and reaches for nothing.** Every decision lives in `AutoBackup.kt` as a
 * function over `File`, because when the system starts the process for a backup it binds the base
 * `android.app.Application` rather than [BinkyApplication]: `app.container` is not there to be had,
 * and a cast would be a `ClassCastException` in exactly the conditions nobody tests under. What this
 * agent is allowed to use is [android.content.Context] paths — `filesDir`, `getDatabasePath` — which
 * a plain `Application` provides.
 *
 * It also runs **without `BinkyApplication.onCreate`**, so ADR-0007's wipe guard has not run and no
 * database is open. That is why the checkpoint below opens its own connection rather than asking Room
 * for one.
 */
class BinkyBackupAgent : BackupAgent() {
    /**
     * Back up the checkpointed database, the preferences and the avatars — and, on a device-to-device
     * transfer, the photo gallery.
     *
     * The staged copy is deleted on the way out. It exists for the duration of one `fullBackupFile`
     * call, which consumes the bytes synchronously, so nothing is waiting on it afterwards.
     */
    override fun onFullBackup(data: FullBackupDataOutput) {
        val staged = File(filesDir, AUTO_BACKUP_STAGING_PATH)
        try {
            val databaseFile = getDatabasePath(BUNNY_DATABASE_FILE)
            if (databaseFile.isFile) checkpointDatabaseTo(databaseFile, staged)

            val set =
                autoBackupFileSet(
                    filesDir = filesDir,
                    stagedDatabase = staged,
                    deviceToDeviceTransfer = data.isDeviceToDeviceTransfer(),
                )
            set.files.forEach { file -> fullBackupFile(file, data) }

            // The last honest moment: the transport can still fail after this returns, and there is
            // no callback for that. Overstating by one failed upload is the mild direction — the
            // marker ages out at 14 days either way, and the state that must never be wrong is the
            // *absence* of a backup, which no write here can invent.
            //
            // The excluded count rides along because this process cannot say it any other way: the
            // app's DataStore is unreachable from here and its writes are `suspend` inside these
            // blocking callbacks. The marker is the message, and the app reads it on next launch.
            writeAutoBackupMarker(filesDir, Instant.now(), excludedDocuments = set.excludedDocuments)
        } finally {
            staged.delete()
            staged.parentFile?.delete()
        }
    }

    /**
     * Runs on the receiving phone once every file has landed.
     *
     * Two jobs, and they are unrelated. The staged database is moved into place — [onFullBackup]
     * shipped it as `autobackup/bunny.db` under `filesDir`, because a full restore puts each file
     * back at the relative path it was taken from and Room's own file is not one of them.
     *
     * And the marker is cleared, because this phone no longer holds the data any marker of its own
     * vouched for. The marker is never in the backup set, so nothing has arrived from the old phone;
     * this is the second, independent mechanism ADR-0005 asks for.
     */
    override fun onRestoreFinished() {
        adoptRestoredDatabase(filesDir = filesDir, databaseFile = getDatabasePath(BUNNY_DATABASE_FILE))
        clearAutoBackupMarker(filesDir)
    }

    /**
     * A transfer straight to another phone: no cloud account, and no quota. Both of ADR-0005's
     * reasons for holding the gallery back are about that quota, and silently dropping every photo
     * on a phone upgrade would be the worse failure — so on this path they travel.
     *
     * The flag only exists from API 30. Below it, this returns false and the gallery stays out,
     * which is precisely what the deleted `backup_rules.xml` did for "API 30 and below".
     */
    private fun FullBackupDataOutput.isDeviceToDeviceTransfer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return (transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
    }

    /**
     * The key/value path, which `android:fullBackupOnly="true"` means is never taken. Both are
     * abstract on [BackupAgent], so they are implemented rather than omitted; leaving them empty is
     * the statement that this app has no key/value dataset, not an oversight.
     */
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit
}
