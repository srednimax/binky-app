package app.binky.tracker.data

import app.binky.tracker.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0023's asymmetry: a schema mistake is free in a debug build, a failed launch in a release
 * one, and never a silent deletion in either.
 *
 * This is a JVM test because the property cannot be checked by hand on a release build — `run-as`
 * does not work on one, so there is no way to look at the database of the build that matters.
 *
 * **What it cannot prove**, and the reason the decision is a function of a parameter rather than a
 * read of the flag: `./gradlew test` runs the *debug* unit tests only, where `BuildConfig.DEBUG` is
 * true, so a default rewired to a hardcoded `true` would pass [theDefaultFollowsTheBuild] here.
 * What is pinned is that the app has exactly one answer to this question, and that the answer is
 * *no* for a build that says it is not a debug build.
 */
class DestructiveMigrationTest {
    /** The one that matters: a release build never destroys a database it cannot open. */
    @Test
    fun aReleaseBuildRefusesToDestroy() {
        assertFalse(destructiveMigrationAllowed(isDebugBuild = false))
    }

    /**
     * And the churn stays free where it is meant to be. ADR-0007 grants Phases 4-5 a throwaway
     * debug database, so this is not merely the inverse of the test above — it is the half that
     * keeps `installDebug` usable after 1.0.
     */
    @Test
    fun aDebugBuildKeepsTheFreeWipe() {
        assertTrue(destructiveMigrationAllowed(isDebugBuild = true))
    }

    @Test
    fun theDefaultFollowsTheBuild() {
        assertEquals(BuildConfig.DEBUG, destructiveMigrationAllowed())
    }
}
