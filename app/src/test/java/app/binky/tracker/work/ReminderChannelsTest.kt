package app.binky.tracker.work

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
    fun `there are exactly three channels, and they are the three this release has behind them`() {
        // One per thing that posts, and no more. A channel is the owner's only per-kind control:
        // muting a daily watch nag must not mute an annual vaccination, and deciding a monthly
        // "make a backup" prompt is not for you must not cost either of the other two. Three rather
        // than four, because doses are Phase 5's and a channel with nothing behind it is a settings
        // row describing a lie. `backup` was added in 4e, with something behind it — this test is
        // what makes each addition a deliberate act rather than a passing convenience.
        assertEquals(setOf("care", "watch", "backup"), ReminderChannel.entries.map { it.id }.toSet())
    }

    @Test
    fun `channel ids are stable, lowercase and free of the app's package`() {
        // A renamed id is a *new* channel: the owner's mute silently goes back to unmuted and there
        // is no migration for it. Nothing enforces that but review — this at least pins the shape.
        assertTrue(ReminderChannel.entries.all { it.id.matches(Regex("[a-z][a-z_]*")) })
    }
}
