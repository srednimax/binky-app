package app.binky.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every shipped translation against the English base, checked mechanically (ADR-0013, Phase 8).
 *
 * Was `PolishTranslationTest`, which held one language. The assertions were already the right ones;
 * what changed is that the language is now a **row in a table** rather than a field, so the ninth
 * language costs a line of `locales_config.xml` and a CLDR entry instead of a new test file.
 *
 * **Completeness is deliberately not here.** "Every base string has a counterpart in every locale"
 * used to be this file's first assertion, and at nine languages it would put every feature branch
 * behind a translation round — you could not add an English string and build until seven other
 * languages had caught up, which is how translation gets done twice: once against the draft copy
 * and again after review reworded it. It moved to `scripts/translation-gate.py`, which CI runs on
 * every pull request. The rule is unchanged and the boundary moved: **free while you work, strict
 * before it merges.**
 *
 * What stays here is everything that must hold for whatever *is* translated, at any moment — a
 * half-translated file is fine, a wrongly-translated one is not.
 *
 * Kotlin note: the XML is parsed with the JDK's own DOM parser rather than Android's, because a
 * `src/test` unit test runs on the JVM with no Android framework under it — the same reason
 * [app.binky.tracker.ui.settings.AppLanguageTest] reads its resource as a plain [File]. Gradle runs
 * unit tests with the module directory as the working directory.
 */
class TranslationTest {
    private val base = parse(BASE_STRINGS)

    /** The locales this build actually ships, read from the file the platform itself reads. */
    private val shipped: List<String> =
        Regex("""<locale\s+android:name="([^"]+)"""")
            .findAll(File(LOCALES_CONFIG).readText())
            .map { it.groupValues[1] }
            .toList()

    /** Every shipped locale except the base one, paired with its parsed file. */
    private val translations: Map<String, Resources>
        get() =
            shipped
                .filterNot { it == BASE_LOCALE }
                .associateWith { parse(stringsFor(it)) }

    @Test
    fun `every shipped locale has a resource directory and a plural table`() {
        // Three declarations of one list — locales_config.xml, the AppLanguage enum and the res
        // directory — and this is the one that catches the spelling. A BCP-47 tag writes a region
        // plainly (`pt-BR`); a resource qualifier prefixes it with `r` (`values-pt-rBR`). Two
        // spellings of one locale in two files is exactly the mistake that ships a language the
        // app declares and cannot load, and it is silent: resource resolution simply falls back to
        // English and nothing anywhere fails.
        shipped.forEach { tag ->
            assertTrue(
                "no CLDR plural categories recorded for '$tag' — add its row to CLDR_PLURALS. " +
                    "A missing category is not an error at run time: it silently resolves to " +
                    "'other' and renders a grammatically wrong sentence.",
                tag in CLDR_PLURALS,
            )
            if (tag != BASE_LOCALE) {
                val file = File(stringsFor(tag))
                assertTrue(
                    "locales_config.xml declares '$tag' but ${file.path} does not exist. " +
                        "Note the qualifier spelling: a region takes an 'r' prefix, so the tag " +
                        "'pt-BR' lives in 'values-pt-rBR'.",
                    file.isFile,
                )
            }
        }
    }

    @Test
    fun `no translated file declares a resource the base language does not`() {
        // The reverse drift: a resource renamed in `values/` and left behind in a translation
        // resolves to nothing, silently, because the base file is what the R class is generated
        // from. Unlike a missing translation, this one is never visible on screen.
        translations.forEach { (tag, translated) ->
            val orphaned = translated.all.keys - base.all.keys
            assertTrue(
                "values-${qualifier(tag)} declares resources the base language does not: ${orphaned.sorted()}",
                orphaned.isEmpty(),
            )
        }
    }

    @Test
    fun `nothing marked untranslatable appears in a translated file`() {
        // `translatable="false"` means locale-*invariant*: the launcher label, the endonyms, a
        // medicine's brand name. A copy in a translated file resolves fine and renders the same
        // words, so nothing is ever visibly wrong — it just costs a line per language, forever,
        // and every future translator reads past it. At nine languages the endonyms alone would
        // have been 81 duplicated entries.
        translations.forEach { (tag, translated) ->
            val copied = base.untranslatable intersect translated.all.keys
            assertTrue(
                "values-${qualifier(tag)} translates resources the base language marks " +
                    "translatable=\"false\": ${copied.sorted()} — delete them there, or drop the " +
                    "marker in values/strings.xml",
                copied.isEmpty(),
            )
        }
    }

    @Test
    fun `the launcher label is deliberately untranslated and stays that way`() {
        // A launcher label resolves against the *system* locale, not the app's, so a translated
        // app_name would rename the icon on a Polish phone whose owner set Binky to English. The
        // exemption has to be *declared* in the base file, which is the half the general rule above
        // cannot check: a deleted marker makes app_name translatable again and every assertion here
        // still passes, right up until a translator obliges.
        assertTrue(
            "app_name should be marked translatable=\"false\" in the base language",
            "app_name" in base.untranslatable,
        )
    }

    @Test
    fun `every translated string keeps the base language's format arguments`() {
        // A dropped %1$s is not a typo — the argument is still passed at the call site, so the
        // sentence renders without the bunny's name and nothing anywhere fails.
        //
        // Note what this cannot see: an argument that is *kept* and given a different job. Polish
        // `photo_gallery_empty_help` carried its %1$s faithfully and moved it from the thing the
        // photos are of to the gallery they land in, describing a folder that does not exist. Every
        // assertion here passed. That half is the native read-through's, and always will be.
        translations.forEach { (tag, translated) ->
            base.strings.forEach { (name, element) ->
                val counterpart = translated.strings[name] ?: return@forEach
                assertEquals(
                    "format arguments differ for string '$name' in values-${qualifier(tag)}",
                    element.formatArguments(),
                    counterpart.formatArguments(),
                )
            }
            base.plurals.forEach { (name, element) ->
                // Compared against the base's `other` item: English has two categories and Polish
                // four, so there is no item-for-item pairing to make — every translated item has to
                // carry the same arguments as the English sentence it is a form of.
                val expected =
                    element
                        .items()
                        .first { it.getAttribute("quantity") == "other" }
                        .formatArguments()
                translated.plurals[name]?.items()?.forEach { item ->
                    assertEquals(
                        "format arguments differ for plural '$name', quantity " +
                            "'${item.getAttribute("quantity")}' in values-${qualifier(tag)}",
                        expected,
                        item.formatArguments(),
                    )
                }
            }
        }
    }

    @Test
    fun `every translated plural covers exactly its language's CLDR categories`() {
        // Counts go through <plurals> and never through concatenation, which is the whole point of
        // ADR-0013's consequence — and a plural is only as good as its category list. Checked
        // against the language's *own* rules rather than a hardcoded set of four: Polish needs
        // one/few/many/other where German needs one/other, and requiring four of German would be as
        // wrong as requiring two of Polish.
        translations.forEach { (tag, translated) ->
            val required = CLDR_PLURALS.getValue(tag)
            translated.plurals.forEach { (name, element) ->
                val quantities =
                    element
                        .items()
                        .mapNotNull { it.getAttribute("quantity").takeIf(String::isNotEmpty) }
                        .toSet()
                assertEquals(
                    "plural '$name' in values-${qualifier(tag)} does not carry exactly the " +
                        "categories CLDR gives '$tag'",
                    required,
                    quantities,
                )
            }
        }
    }

    @Test
    fun `the breed suggestions line up one for one`() {
        // Breed is free text and the picker accepts anything typed, so these are suggestions rather
        // than a vocabulary — but a translated list that has drifted in length has lost or gained a
        // breed relative to the English one, which is a translation bug either way. Names with no
        // local form stay in their original ("Beveren", "Blanc de Hotot"); that is a translation
        // decision, not a missing entry, and it keeps the lengths equal.
        val baseBreeds = base.arrays.getValue("built_in_breeds").items()
        translations.forEach { (tag, translated) ->
            val breeds = translated.arrays[BREEDS] ?: return@forEach
            assertEquals(
                "built_in_breeds in values-${qualifier(tag)} has drifted in length",
                baseBreeds.size,
                breeds.items().size,
            )
        }
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
    }

    private companion object {
        const val BASE_LOCALE = "en"
        const val BASE_STRINGS = "src/main/res/values/strings.xml"
        const val LOCALES_CONFIG = "src/main/res/xml/locales_config.xml"
        const val BREEDS = "built_in_breeds"

        /**
         * Plural categories per language, from CLDR.
         *
         * Nine rows because Phase 8 ships nine languages; only the locales named in
         * `locales_config.xml` are actually asserted, so a row here is ready rather than active.
         * **Verify the row when its language lands** — this table is written from CLDR's rules, not
         * read out of the tool, and a wrong row fails in the one direction nothing notices.
         *
         * The romance `many` is a large-number form and is unreachable for the integer counts this
         * app produces. It is declared anyway, for the same reason Polish declares `other`: Android
         * resolves against the declaration, and a category that is missing renders blank rather
         * than falling back to something sensible.
         */
        val CLDR_PLURALS =
            mapOf(
                "en" to setOf("one", "other"),
                "de" to setOf("one", "other"),
                "es" to setOf("one", "many", "other"),
                "fr" to setOf("one", "many", "other"),
                "it" to setOf("one", "many", "other"),
                "pt-BR" to setOf("one", "many", "other"),
                "pl" to setOf("one", "few", "many", "other"),
                "cs" to setOf("one", "few", "many", "other"),
                "uk" to setOf("one", "few", "many", "other"),
            )

        /**
         * The `values-` qualifier for a BCP-47 tag: `pl` → `pl`, `pt-BR` → `pt-rBR`.
         *
         * The `r` prefix on the region is the whole reason this function exists rather than being
         * string interpolation at the call sites — the tag and the directory are two spellings of
         * one locale, and Phase 8 named that as the shape of mistake to expect.
         */
        fun qualifier(tag: String): String =
            tag.split('-').let { parts ->
                if (parts.size == 2) "${parts[0]}-r${parts[1]}" else tag
            }

        fun stringsFor(tag: String) = "src/main/res/values-${qualifier(tag)}/strings.xml"

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
