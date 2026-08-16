# Phase 8 — Nine languages — ships as 1.6

**Status: planned, not started.** Boxes in [`DOD.md`](DOD.md) §7; this file is the reasoning. Finished
phases are in [`PLAN.md`](PLAN.md) and are not needed to build this one.

**Prerequisite: Phases 6, 7 and 7.5 ship first**, for one reason applied three times — translating a
string set that is about to change means translating it twice, in nine languages. Phase 6 adds a Support
screen; Phase 7's redesign rewrites copy wherever the old wording was the thing that confused people;
[Phase 7.5](phase-7.5.md) adds the gain signal's health copy and an attribution screen. Seven languages of
re-translation *and* seven native re-reads is the most expensive way to discover that ordering, so it is
fixed here rather than left to judgement.

**Retargeted from 1.5 to 1.6 on 2026-08-14**, because Phase 7.5 adds functionality and `release-please`
answers to commit subjects rather than to a phase file.

**Decisions it leans on:** ADR-0013 (English base, in-app switcher, endonym labels), ADR-0001 and
ADR-0026 (what the copy may never say — the reason this phase is not a mechanical job), ADR-0009 (the
listing).

## What ships

Polish was the proof of concept: it proved the mechanism — `AppCompatDelegate`, the backport, the
`locales_config.xml` / `AppLanguage` pair, and a test that holds the translation afterwards. **Seven
languages join it**, taking the app to nine:

| Locale | Resource directory | Plural categories *(verify against CLDR when it lands)* |
| --- | --- | --- |
| `en` | `values` | one, other |
| `pl` | `values-pl` | one, few, many, other |
| `de` | `values-de` | one, other |
| `es` | `values-es` | one, many, other |
| `fr` | `values-fr` | one, many, other |
| `it` | `values-it` | one, many, other |
| `pt-BR` | `values-pt-rBR` | one, many, other |
| `cs` | `values-cs` | one, few, many, other |
| `uk` | `values-uk` | one, few, many, other |

**631 translatable resources × 7 ≈ 4 400 strings**, drafted by machine against a brief and **read by a
native speaker before that language ships**. The plural table above is written from CLDR's rules and is
**checked per row when the language lands, not trusted from this file** — a missing category is not an
error, it silently resolves to `other` and renders a grammatically wrong sentence. Note the romance
`many` category is unreachable for the integer counts this app produces (it is a large-number form), but
it is declared anyway for the same reason Polish declares `other`.

## The two facts that shape the workflow

- **Resource resolution does not consult `locales_config.xml`.** That file governs the *per-app language
  picker* on Android 13+ and AppCompat's backport below it — it is not a delivery filter. The moment
  `values-de/` exists in `res/`, **every phone set to German gets those strings**, reviewed or not. So a
  wide tranche cannot be drafted in place and released language by language: drafts live **outside
  `res/`**, in `translations/<locale>/strings.xml`, and *promotion is a file move*. The test suite reads
  paths, so it can validate a staged draft exactly as it validates a shipped one — the review gate keeps
  its teeth without the drafts being invisible. (`androidResources` locale filters could do the same job
  from the build file; the staging directory is preferred because it needs no build configuration and
  cannot be switched off by accident.)
- **A language ships when its native review passes, not when its draft lands.** Promotion is four edits
  and they go together, or the app offers a language it does not have: move the file into `res/`, add the
  `<locale>` line, add the `AppLanguage` entry, add the endonym label. `AppLanguageTest` already asserts
  the XML and the enum agree; it cannot see the other two.

## Decisions

- **The gate stops being named after one language.** `PolishTranslationTest` becomes `TranslationTest`,
  parameterised over the locale table: every translatable resource present, nothing orphaned, format
  arguments preserved, plural categories complete **per that language's own rules** rather than against a
  hardcoded set of four. Its existing five assertions are already the right ones — this is generalisation,
  not new test design, and the Polish-specific comments become the table's rows.
- **The endonym labels become `translatable="false"`, which removes 81 duplicated entries.** ADR-0013
  names each language in its own language *in every locale*, so `settings_language_polish` is `Polski`
  everywhere — it is locale-*invariant*, which is what `translatable="false"` means. Today `values/` and
  `values-pl/` carry byte-identical copies; at nine languages that becomes nine labels duplicated nine
  times for no benefit. Marking them untranslatable collapses it to nine strings, and the existing
  "untranslatable resources never appear in a translated file" assertion enforces it for free.
- **The translator brief is a deliverable, and it is the part that makes machine drafting safe.** This
  app's copy carries rules a translator cannot infer: ADR-0026 forbids *missed* and *overdue* outside
  Phase 4's care reminders, ADR-0001 forbids language that infers a problem from silence, weight changes
  are always stated in grams, and `CONTEXT.md`'s vocabulary is deliberate — *fluffle*, *watch*,
  *observation*, *visit* — with an *Avoid* list per term. A translator without that produces fluent copy
  that quietly turns an observation into a warning, in a language no reviewer here reads. So the brief
  carries the vocabulary, the tone, and a **per-language banned-word list** (`verpasst`/`überfällig`,
  `pominięta`/`zaległa`, …) written *with* the native reviewer rather than guessed at.
- **Listing text for all nine; screenshots may lag.** Play falls back to the default listing's
  screenshots when a language has none, but there is no fallback for *discovery* — an untranslated
  listing means nobody in that market finds the app at all. So the title, short and full descriptions are
  part of this phase for every language, and localised screenshots are taken for the languages with
  installs once there are installs to count. They wait on the locale-aware driver below — **not** on a
  `--locale` flag, which is what this line used to say was missing and which already exists.
- **The capture driver has to be locale-aware, and that is a translation job rather than a capture one.**
  **Built in [Phase 7.5](phase-7.5.md) as of 2026-08-14**, on `en` + `pl`, so this phase inherits the tool
  and owes only its re-proof as the needle table grows. The reasoning below is why it exists and stays
  here; the isolation half and the Polish after set go with it.
  Carried in from Phase 7 on 2026-08-13, where it was the single box that phase did not close. `--locale`
  already exists on `screenshots.py` and already switches the app — the dump comes back `14 dni, 3 dni,
  7 dni` — and then **every scene fails at its first tap**, because the scene needles in
  `edge-to-edge.py`'s table are English string literals and `tap("Choose which bunny")` matches nothing.
  The flag has never actually been exercised, which is why two phase files had been treating it as the
  whole job.
  **ADR-0013 is what makes the fix small.** Every user-visible string is a resource in every locale and
  `TranslationTest` keeps them level, so a needle can resolve *through the resource name*: parse
  `values/strings.xml` and `values-<locale>/strings.xml`, build the map, translate at tap time. Two
  wrinkles, and both argue for a lookup that **falls through to the literal** rather than failing —
  several needles are deliberate **substrings** of their string (`"What you noticed"`), and several are
  not resources at all (`Bijou` and `Metacam` are sample data, identical in every locale).
  It belongs in `edge-to-edge.py`, where the needles live and where the inset matrix reads them too;
  `screenshots.py` imports that table rather than copying it, so one fix serves both.
  **Build it before the seven drafts land**, not after: it is the copy-length canary, and a German
  compound clipping a redrawn row is cheaper to find before a native speaker reads that language than
  after. It is also what pays back Phase 7's one deferred claim — that phase shipped its comparison in
  English, so **Polish is the first language whose copy length has never been photographed against the
  new layouts**, and it goes through this driver alongside the seven rather than as a special case.
- **`values-pt-rBR`, not `values-pt-BR`.** The resource qualifier uses the `r` prefix for a region; the
  BCP-47 tag in `locales_config.xml` and in `AppLanguage` does not (`pt-BR`). Two spellings of one
  locale, in two files that must agree, is exactly the shape of mistake `AppLanguageTest` exists to
  catch — extend it to compare the resource directory too.
- **No RTL in this phase.** Arabic and Hebrew are not a translation job, they are a layout project:
  mirroring, `supportsRtl`, and 4f's edge-to-edge matrix doubling. Excluded deliberately so that the cost
  is a decision rather than a discovery.

## The open question this phase owed 🟢 answered 2026-08-16

**Nine locales means every future string is nine translations before the build goes green.** The old test
made a missing translation a red build, which is why Polish never rotted — and at nine languages that same
strictness puts every future feature behind a translation round.

**Answered: the boundary moves, not the rule.** Neither of the two options this file weighed — strict red
build, or a dated `translations-pending` allowlist — is what shipped. Both treat the question as *how much
lag to tolerate*, and the better answer is *where to ask*: **free while you work, strict before it merges.**

- **Completeness left `TranslationTest` for `scripts/translation-gate.py`**, which CI runs on every pull
  request beside the schema gate. Adding an English string no longer reddens your own build; a branch that
  still owes a translation cannot merge.
- The point is not developer comfort, it is **translating once**. Under a red-build rule the copy gets
  translated against the draft wording and then again after review reworded it — nine times, for nothing.
  ADR-0013's promise is about what *ships*, and `main` is where shipping starts.
- The allowlist is unnecessary under this shape and was dropped: it existed to make lag *visible*, and a
  gate that refuses the merge makes lag impossible instead. Nothing to date, nothing to expire, no file
  admitting a debt.
- The gate checks three things, and the second is the one no test could hold: resources **missing** from a
  locale (split by whether this branch introduced them, so the list is the work rather than the debt),
  translations gone **stale** because the English moved on this branch and they did not, and **orphans** a
  rename left behind. `--report` prints the same list and exits 0, for use mid-branch.
- Everything that must hold for whatever *is* translated — format arguments, plural categories per CLDR,
  orphans, untranslatable resources — stays in `TranslationTest` and stays green continuously.

⚠️ **`./gradlew test` was reporting a stale verdict, and this is what made it matter.** Both translation
tests read `res/` off disk as plain files, which Gradle's up-to-date check cannot see — so editing a
translation and re-running `test` printed `:app:test UP-TO-DATE` and a green build having checked nothing.
Fixed by declaring `src/main/res` as a test input in `app/build.gradle.kts`. Found while building this
gate; it had been true since the Polish test was written.

## Tests

JVM, and all of it mechanical: `TranslationTest` over the nine-row table (completeness, orphans, format
arguments, plural categories per CLDR), `AppLanguageTest` extended to assert `locales_config.xml`, the
`AppLanguage` enum **and** the resource directories all name the same nine locales. What no test can
hold is tone — that is the native read-through, and it is why a language ships on a person's word.

## Gate

- **Every screen read end to end in each language by a native speaker**, with no English left behind —
  ADR-0013's original promise, now nine times. A language that has not had this does not ship.
- `TranslationTest` green across all nine locales; `AppLanguageTest` green across all three declarations.
- **Plural categories correct per language**, checked on a real count in each: 1, 2, 5 and 22 of
  something, in `cs` and `uk` as well as `pl`, where the *few*/*many* split is where a wrong table shows.
- No string in any locale says *missed* or *overdue* outside Phase 4's care reminders, and no banned-word
  list entry appears in its own language's file.
- The switcher offers nine languages by endonym, and switching to each restarts into that language.
- `settings_language_*` are `translatable="false"` and appear in no `values-<locale>` file.
- The Play listing carries title, short and full description in all nine.
- **A device set to a language Binky does not ship** still falls back to English cleanly, including its
  plurals.
- Edge-to-edge unaffected: longest-string languages (German compounds, Ukrainian) do not clip or wrap
  wrongly on the narrowest supported screen — the one visual risk a translation genuinely carries.
  **Polish is re-checked here rather than assumed**: Phase 7 deferred its capture and Phase 7.5 shot it,
  so what this phase owes is the same check against copy the seven drafts have since changed nothing of —
  cheap, and the only shipped language with a before to compare against.

`spotlessApply`, `assembleDebug`, `test` and `lint` at the gate. No `connectedAndroidTest` is owed —
there is no schema change and no media path.

## Order of work

1. ✅ **Done 2026-08-16.** Generalise the test and the locale table **first**, on `en` + `pl` alone. It
   must be able to fail before there is anything to check. `PolishTranslationTest` → `TranslationTest`,
   the locale list read from `locales_config.xml` so a tenth language is one line of XML, and
   `CLDR_PLURALS` carrying all nine rows ready. Proven able to fail: dropping `few` from one Polish
   plural reddens the build. `scripts/translation-gate.py` likewise, on all three of its failure modes.
2. ✅ **Endonyms done 2026-08-16**, along with `app_name`'s general rule and `med_editor_name_placeholder`
   (a medicine brand name). The brief is [`translator-brief.md`](translator-brief.md); its per-language
   banned lists are drafts until each native reviewer confirms their own row.
3. **Re-prove the locale-aware capture driver** — built in Phase 7.5 and proven there on `pl`, so what is
   owed here is only that its needle table still resolves once seven locales exist. Every new needle is a
   claim that some resource still says what the table thinks it says.
4. Draft all seven into `translations/`, validated by the test in place. 🟡 **`de`, `es` and `fr`
   done 2026-08-16** — four to go —
   along with the staging area this step assumed and nothing had yet built: `TranslationTest` reads
   `translations/<tag>/strings.xml` beside `values-<qualifier>/`, holds a draft to the same
   assertions, and rejects a tag that is both staged and shipped — promotion is a *move*, and two
   files for one language drift. `translations/` is declared a test input in `app/build.gradle.kts`
   for the reason `src/main/res` already was: a file Gradle cannot see is a file the test reports a
   stale verdict on. The gate prints a draft's completeness under a heading of its own and never
   gates on it, because the merge rule is about what ships.
   The German draft's own decisions are in `DOD.md` §7; the one worth carrying to the next six is
   that **§7.2's `care_every` is harder than §7.3's gender trap** in at least one language. German
   has no preposition that takes both a bare unit and a counted gap, so the host became a label
   (`Rhythmus: %1$s`) and the gap plurals stayed in the citation form, which then decided the
   wording of `care_due_in` and `care_due_overdue` too. Check that chain early in each language: it
   is four strings deep and only visible from the code that composes them (`CareLabels.kt`).
   **French confirmed it and Spanish did not**, which settles the shape: *tous les jours* against
   *toutes les semaines* is the same missing preposition, so `care_every` is `Rythme : %1$s` there
   too, and the same two due-date strings had to be rewritten behind it. Two of three languages
   pay it; treat Spanish's *Cada* as the exception. **French then added a trap the brief did not
   have**: §7.4 is not only about cases. French declines nothing, but *de* elides before a vowel
   and the app can no more elide *Alice* than Polish can decline *Bijou* — seven strings reordered
   or reworded for it. The lesson for the four remaining is that **§7.4 is really "the app cannot
   touch a name the owner typed"**, and each language should be asked what its own version of that
   is rather than checked against Polish's. Both per-language records are `DOD.md` §7.
5. Promote one language at a time as its native read completes — four edits, one commit, one language.
6. Listing text for the promoted set; screenshots later, driven by install data.

## When it closes

Write the results here, tick **Phase 8** in `PLAN.md`'s status list, and empty §7 of `DOD.md`.
