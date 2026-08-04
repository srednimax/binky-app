package app.binky.tracker.work

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The channel enum and `strings.xml` are the same claim written twice, and this is what stops them
 * drifting — the same shape as `AppLanguageTest`, for the same reason.
 *
 * Checked **in both directions**, and neither failure is loud on its own. A channel with no strings
 * behind it cannot happen (the `R` reference would not compile), but a *stale* `channel_…` string
 * left behind after a channel is renamed or removed is a settings row's worth of copy describing
 * something that no longer exists — and channel ids are permanent, so this is the drift that
 * actually happens. Lint says nothing about either.
 *
 * Reads the resource off disk rather than through `R`: a JVM unit test has no Android framework
 * under it, and the file's *contents* are what is in question. Gradle runs unit tests with the
 * module directory as the working directory.
 */
class ReminderChannelsTest {
    private val declared: Set<String> =
        Regex("""<string\s+name="(channel_[^"]+)"""")
            .findAll(File("src/main/res/values/strings.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    /** The naming convention the enum's `R.string` fields follow, stated once. */
    private val expected: Set<String> =
        ReminderChannel.entries
            .flatMap { channel -> listOf("channel_${channel.id}_name", "channel_${channel.id}_description") }
            .toSet()

    @Test
    fun `every channel has a name and a description, and nothing else declares one`() {
        assertEquals(expected, declared)
    }

    @Test
    fun `there are exactly four channels, and they are the four this release has behind them`() {
        // One per thing that posts, and no more. A channel is the owner's only per-kind control:
        // muting a daily watch nag must not mute an annual vaccination, deciding a monthly "make a
        // backup" prompt is not for you must not cost either of the other two, and muting doses must
        // not follow from any of the three. `backup` arrived in 4e and `doses` in 5a, each with
        // something behind it — this test is what makes an addition a deliberate act rather than a
        // passing convenience.
        assertEquals(setOf("care", "watch", "backup", "doses"), ReminderChannel.entries.map { it.id }.toSet())
    }

    @Test
    fun `doses is the only high-importance channel`() {
        // The level is spent once, on purpose. If everything is HIGH then nothing is, and 4a
        // deliberately left it unspent so this one would read as a real signal rather than as the
        // volume the app already sits at. The other direction is what makes it worth pinning: a
        // channel created at the wrong importance can never be raised again, only lowered by the
        // owner, so this is a decision with no second attempt.
        assertEquals(
            setOf(ReminderChannel.Doses),
            ReminderChannel.entries.filter { it.importance == NotificationManager.IMPORTANCE_HIGH }.toSet(),
        )
    }

    @Test
    fun `no channel is created below IMPORTANCE_DEFAULT`() {
        // Creating `watch` or `backup` quiet would be making the mute decision on the owner's
        // behalf, in the one direction that cannot be undone.
        assertTrue(ReminderChannel.entries.all { it.importance >= NotificationManager.IMPORTANCE_DEFAULT })
    }

    @Test
    fun `channel ids are stable, lowercase and free of the app's package`() {
        // A renamed id is a *new* channel: the owner's mute silently goes back to unmuted and there
        // is no migration for it. Nothing enforces that but review — this at least pins the shape.
        assertTrue(ReminderChannel.entries.all { it.id.matches(Regex("[a-z][a-z_]*")) })
    }
}
