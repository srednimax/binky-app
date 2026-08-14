package app.binky.tracker.ui.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The attribution list's parsing and grouping (Phase 7.5 §3).
 *
 * The fixtures below are **real excerpts** from `app.cash.licensee`'s own report rather than invented
 * JSON — an `androidx` artifact, the one BSD-3-Clause one nobody knew was in the build, and the two
 * Google terms that carry no SPDX identifier. A test written against a shape we imagined would pass
 * while the screen stayed blank.
 */
class LicencesTest {
    @Test
    fun `an artifact lands under its licence, with its coordinates and name`() {
        val groups = groupLicences("[$ACTIVITY]")

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals("Apache License 2.0", group.title)
        assertEquals("Apache-2.0", group.spdxId)
        assertEquals(listOf("androidx.activity:activity:1.13.0"), group.artifacts.map { it.coordinates })
        assertEquals("Activity", group.artifacts.single().displayName)
    }

    @Test
    fun `a licence with no SPDX identifier keeps its URL and is marked as one we cannot bundle`() {
        // The distinction the screen turns on: an SPDX licence's text ships inside the APK, where
        // Google's terms of service are not ours to redistribute and can only be linked.
        val group = groupLicences("[$PLAY_SERVICES_BASE]").single()

        assertEquals("Android Software Development Kit License", group.title)
        assertEquals(null, group.spdxId)
        assertEquals("https://developer.android.com/studio/terms.html", group.url)
    }

    @Test
    fun `SPDX licences come before the ones without an identifier, each sorted among themselves`() {
        // Not alphabetical over the whole list and not by size — Apache-2.0 has 195 artifacts and
        // BSD-3-Clause has one, and BSD still comes second. What the order says is: here are the
        // licences whose text this app ships, then the two it can only point at.
        val groups = groupLicences("[$PLAY_SERVICES_BASE,$PROTOBUF,$MLKIT,$ACTIVITY]")

        assertEquals(
            listOf("Apache-2.0", "BSD-3-Clause", null, null),
            groups.map { it.spdxId },
        )
        assertEquals(
            listOf("ML Kit Terms of Service", "Android Software Development Kit License"),
            groups.drop(2).map { it.title }.sortedDescending(),
        )
    }

    @Test
    fun `artifacts inside a group are ordered by group and artifact, not by resolution order`() {
        val group = groupLicences("[$ROOM,$ACTIVITY]").single()

        assertEquals(
            listOf("androidx.activity:activity:1.13.0", "androidx.room:room-runtime:2.8.4"),
            group.artifacts.map { it.coordinates },
        )
    }

    @Test
    fun `a module sorts above its own submodules, which a string sort of the coordinates does not`() {
        // Caught on the phone: `-` sorts before `:`, so sorting the joined coordinate string files
        // `androidx.activity:activity` third, underneath `activity-compose` and `activity-ktx`.
        val compose =
            """
            {
              "groupId": "androidx.activity", "artifactId": "activity-compose", "version": "1.13.0",
              "spdxLicenses": [ { "identifier": "Apache-2.0", "name": "Apache License 2.0" } ]
            }
            """.trimIndent()

        val group = groupLicences("[$compose,$ACTIVITY]").single()

        assertEquals(
            listOf("androidx.activity:activity:1.13.0", "androidx.activity:activity-compose:1.13.0"),
            group.artifacts.map { it.coordinates },
        )
    }

    @Test
    fun `a dual-licensed artifact is listed under both, because the obligation is per licence`() {
        // Nothing in the app ships this way today. De-duplicating to "one row per artifact" would be
        // the bug rather than the tidy-up: each licence's section is the list of what it covers.
        val dual =
            """
            {
              "groupId": "com.example", "artifactId": "thing", "version": "1.0",
              "spdxLicenses": [
                { "identifier": "Apache-2.0", "name": "Apache License 2.0" },
                { "identifier": "MIT", "name": "MIT License" }
              ]
            }
            """.trimIndent()

        val groups = groupLicences("[$dual]")

        assertEquals(listOf("Apache-2.0", "MIT"), groups.map { it.spdxId })
        groups.forEach { assertEquals(listOf("com.example:thing:1.0"), it.artifacts.map { a -> a.coordinates }) }
    }

    @Test
    fun `a licence that says nothing draws no section`() {
        // `allowUrl` needs a URL, so Licensee cannot emit this today. It is pinned because the fix
        // for a future `allowDependency` must not be an empty heading over a real artifact.
        val nameless =
            """
            {
              "groupId": "com.example", "artifactId": "thing", "version": "1.0",
              "unknownLicenses": [ {} ]
            }
            """.trimIndent()

        assertEquals(emptyList<LicenceGroup>(), groupLicences("[$nameless]"))
    }

    @Test
    fun `two POMs spelling one licence differently still land in a single group`() {
        // The group key is the terms' identity — the URL — rather than whatever `<name>` a POM chose,
        // which is what keeps the three play-services artifacts under one heading.
        val other =
            """
            {
              "groupId": "com.example", "artifactId": "thing", "version": "1.0",
              "unknownLicenses": [
                { "name": "Android SDK Terms", "url": "https://developer.android.com/studio/terms.html" }
              ]
            }
            """.trimIndent()

        val groups = groupLicences("[$PLAY_SERVICES_BASE,$other]")

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().artifacts.size)
        // The first spelling wins the heading. Either is defensible; what matters is that it is one
        // section rather than two saying the same thing.
        assertEquals("Android Software Development Kit License", groups.single().title)
    }

    @Test
    fun `a field the generator adds later does not blank the screen`() {
        // The report is written by a plugin we bump. An unknown key has to be ignored rather than
        // throw, because the failure mode is a licence screen with nothing on it.
        val future = ACTIVITY.dropLast(1) + """, "somethingNew": { "added": "in 1.15" } }"""

        assertEquals(1, groupLicences("[$future]").single().artifacts.size)
    }

    @Test
    fun `an artifact whose POM never named it falls back to its artifact id`() {
        val unnamed =
            """
            {
              "groupId": "com.example", "artifactId": "thing", "version": "1.0",
              "spdxLicenses": [ { "identifier": "Apache-2.0", "name": "Apache License 2.0" } ]
            }
            """.trimIndent()

        assertEquals(
            "thing",
            groupLicences("[$unnamed]")
                .single()
                .artifacts
                .single()
                .displayName,
        )
    }

    @Test
    fun `every SPDX licence the build allows has its text bundled`() {
        // The coupling this whole mechanism rests on, asserted rather than remembered. Licensee fails
        // the build when a dependency arrives under a licence nobody allowed; the fix is two steps —
        // `allow("X")` in the build file *and* `assets/licences/X.txt` — and only the first of them
        // has a build failure behind it. Without this, step two gets forgotten and the screen quietly
        // degrades from shipping the licence to linking at it, which is the obligation DOD §8 is
        // about. Reads the build file as text for the same reason `PolishTranslationTest` reads
        // `strings.xml` that way: a JVM unit test has no Gradle model under it.
        val buildFile = File("build.gradle.kts")
        assertTrue("build.gradle.kts not found at ${buildFile.absolutePath}", buildFile.exists())

        val allowed =
            Regex("""^\s*allow\("([^"]+)"\)""", RegexOption.MULTILINE)
                .findAll(buildFile.readText())
                .map { it.groupValues[1] }
                .toSet()
        assertTrue("no allow(...) lines found — has the licensee block moved?", allowed.isNotEmpty())

        val bundled =
            File("src/main/assets/$LICENCE_TEXT_DIRECTORY")
                .listFiles()
                .orEmpty()
                .map { it.name.removeSuffix(".txt") }
                .toSet()

        assertEquals(
            "a licence is allowed in the build but its text is not bundled",
            emptySet<String>(),
            allowed - bundled,
        )
    }

    private companion object {
        val ACTIVITY =
            """
            {
              "groupId": "androidx.activity", "artifactId": "activity", "version": "1.13.0",
              "name": "Activity",
              "spdxLicenses": [
                {
                  "identifier": "Apache-2.0", "name": "Apache License 2.0",
                  "url": "https://www.apache.org/licenses/LICENSE-2.0"
                }
              ],
              "scm": { "url": "https://cs.android.com/androidx/platform/frameworks/support" }
            }
            """.trimIndent()

        val ROOM =
            """
            {
              "groupId": "androidx.room", "artifactId": "room-runtime", "version": "2.8.4",
              "name": "Room-Runtime",
              "spdxLicenses": [
                { "identifier": "Apache-2.0", "name": "Apache License 2.0" }
              ]
            }
            """.trimIndent()

        val PROTOBUF =
            """
            {
              "groupId": "androidx.datastore",
              "artifactId": "datastore-preferences-external-protobuf",
              "version": "1.2.1",
              "name": "Preferences External Protobuf",
              "spdxLicenses": [
                {
                  "identifier": "BSD-3-Clause",
                  "name": "BSD 3-Clause \"New\" or \"Revised\" License",
                  "url": "https://opensource.org/licenses/BSD-3-Clause"
                }
              ]
            }
            """.trimIndent()

        val PLAY_SERVICES_BASE =
            """
            {
              "groupId": "com.google.android.gms", "artifactId": "play-services-base",
              "version": "18.5.0", "name": "play-services-base",
              "unknownLicenses": [
                {
                  "name": "Android Software Development Kit License",
                  "url": "https://developer.android.com/studio/terms.html"
                }
              ]
            }
            """.trimIndent()

        val MLKIT =
            """
            {
              "groupId": "com.google.android.gms",
              "artifactId": "play-services-mlkit-document-scanner",
              "version": "16.0.0", "name": "play-services-mlkit-document-scanner",
              "unknownLicenses": [
                {
                  "name": "ML Kit Terms of Service",
                  "url": "https://developers.google.com/ml-kit/terms"
                }
              ]
            }
            """.trimIndent()
    }
}
