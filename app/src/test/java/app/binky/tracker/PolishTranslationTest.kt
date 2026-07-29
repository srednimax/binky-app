package app.binky.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Polish translation against the English base, checked mechanically (ADR-0013, PLAN 3i).
 *
 * 3i's promise is "every screen read in Polish with no English left behind", and reading every
 * screen is a person's job that gets done once. This is the part of that promise a machine can hold
 * afterwards: a string added to `values/` and forgotten in `values-pl/` shows up here rather than as
 * an English sentence in the middle of a Polish dialog, six releases later.
 *
 * Lint's `MissingTranslation` covers some of the same ground, but only some — it says nothing about
 * plural categories, nothing about a `%1$s` dropped in translation, and it is a warning in a build
 * that has plenty of those. These are assertions.
 *
 * Kotlin note: the XML is parsed with the JDK's own DOM parser rather than Android's, because a
 * `src/test` unit test runs on the JVM with no Android framework under it — the same reason
 * [app.binky.tracker.ui.settings.AppLanguageTest] reads its resource as a plain [File].
 */
class PolishTranslationTest {
    private val base = parse("src/main/res/values/strings.xml")
    private val polish = parse("src/main/res/values-pl/strings.xml")

    @Test
    fun `every translatable resource in the base language has a Polish counterpart`() {
        val missing = base.translatable.keys - polish.all.keys
        assertTrue(
            "no Polish translation for: ${missing.sorted()}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `the launcher label is deliberately untranslated and stays that way`() {
        // A launcher label resolves against the *system* locale, not the app's, so a values-pl
        // app_name would rename the icon on a Polish phone whose owner set Binky to English. Both
        // halves are asserted: the exemption is declared in the base file, and nobody has quietly
        // "fixed" the gap it leaves.
        assertTrue(
            "app_name should be marked translatable=\"false\" in the base language",
            "app_name" in base.untranslatable,
        )
        assertTrue(
            "app_name must not appear in values-pl — see the comment on it in values/strings.xml",
            "app_name" !in polish.all,
        )
    }

    @Test
    fun `Polish declares nothing the base language does not`() {
        // The reverse drift: a resource renamed in `values/` and left behind in `values-pl/` resolves
        // to nothing, silently, because the base file is what the R class is generated from.
        val orphaned = polish.all.keys - base.all.keys
        assertTrue(
            "values-pl declares resources the base language does not: ${orphaned.sorted()}",
            orphaned.isEmpty(),
        )
    }

    @Test
    fun `every Polish plural covers all four of the language's quantity categories`() {
        // Polish has one/few/many/other against English's two, which is the whole reason counts go
        // through <plurals> and never through concatenation. `other` is unreachable for the integer
        // counts this app produces, but it is the fallback Android resolves against when nothing
        // else matches, so a missing one is a blank string rather than a wrong one.
        val required = setOf("one", "few", "many", "other")
        polish.plurals.forEach { (name, element) ->
            val quantities =
                element
                    .items()
                    .mapNotNull { it.getAttribute("quantity").takeIf(String::isNotEmpty) }
                    .toSet()
            assertEquals("plural '$name' is missing quantity categories", required, quantities)
        }
    }

    @Test
    fun `Polish keeps every format argument the base language passes`() {
        // A dropped %1$s is not a typo — the argument is still passed at the call site, so the
        // sentence renders without the bunny's name and nothing anywhere fails. This is the check
        // that catches a translation reworded past its own placeholders.
        base.strings.forEach { (name, element) ->
            val translated = polish.strings[name] ?: return@forEach
            assertEquals(
                "format arguments differ for string '$name'",
                element.formatArguments(),
                translated.formatArguments(),
            )
        }
        base.plurals.forEach { (name, element) ->
            // Compared against the base's `other` item: English has two categories and Polish four,
            // so there is no item-for-item pairing to make — every Polish item has to carry the same
            // arguments as the English sentence it is a form of.
            val expected =
                element
                    .items()
                    .first { it.getAttribute("quantity") == "other" }
                    .formatArguments()
            polish.plurals[name]?.items()?.forEach { item ->
                assertEquals(
                    "format arguments differ for plural '$name', quantity '${item.getAttribute("quantity")}'",
                    expected,
                    item.formatArguments(),
                )
            }
        }
    }

    @Test
    fun `the breed suggestions line up one for one`() {
        // Breed is free text and the picker accepts anything typed, so these are suggestions rather
        // than a vocabulary — but a Polish list that has drifted in length has lost or gained a
        // breed relative to the English one, which is a translation bug either way.
        val baseBreeds = base.arrays.getValue("built_in_breeds").items()
        val polishBreeds = polish.arrays.getValue("built_in_breeds").items()
        assertEquals(baseBreeds.size, polishBreeds.size)
    }

    private fun parse(path: String): Resources {
        val file = File(path)
        assertTrue("$path not found at ${file.absolutePath} — has the resource moved?", file.exists())
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)

        fun named(tag: String) =
            document
                .getElementsByTagName(tag)
                .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
                // Only top-level declarations: <item> lives inside these and is read separately.
                .filter { it.hasAttribute("name") }
                .associateBy { it.getAttribute("name") }
        return Resources(named("string"), named("plurals"), named("string-array"))
    }

    /**
     * One `strings.xml`, split by resource kind.
     *
     * Kotlin note: a `data class` here is closer to a TS interface than to a class — it exists for
     * the named fields and the copy/equals that come free, not for behaviour.
     */
    private data class Resources(
        val strings: Map<String, Element>,
        val plurals: Map<String, Element>,
        val arrays: Map<String, Element>,
    ) {
        val all: Map<String, Element> get() = strings + plurals + arrays

        /** Marked `translatable="false"`: never expected in a `values-<locale>` folder. */
        val untranslatable: Set<String>
            get() = all.filterValues { it.getAttribute("translatable") == "false" }.keys

        val translatable: Map<String, Element> get() = all - untranslatable
    }

    private companion object {
        /** `%1$s`, `%2$d`, and the non-positional `%d` that single-argument plurals use. */
        val FORMAT_ARGUMENT = Regex("""%(\d+\$)?[a-zA-Z]""")

        fun Element.items(): List<Element> =
            getElementsByTagName("item")
                .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

        /** Sorted, because a translation may legitimately reorder arguments — that is what `%1$s` is for. */
        fun Element.formatArguments(): List<String> =
            FORMAT_ARGUMENT
                .findAll(textContent)
                .map { it.value }
                .sorted()
                .toList()
    }
}
