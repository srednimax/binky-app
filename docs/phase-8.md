# Phase 8 — Nine languages — ships as 1.6

**Status: all nine languages ship and all nine listings are written.** What is left of the phase is the
capture driver's re-proof against seven new locales. Boxes in [`DOD.md`](DOD.md) §7;
this file is the reasoning. Finished phases are in [`PLAN.md`](PLAN.md) and are not needed to build this one.

⚠️ **This file's review gate no longer holds.** It said a language ships when its native read-through
passes. No reviewer was available for any of the seven, so on 2026-08-17 that gate was replaced by an audit
of the half a read-through does that needs no native speaker, plus an in-app report row for the half that
does — **[ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md)**. Every sentence
below about native review is kept as the reasoning that was *in force when the drafts were written*, and
is amended where it is now wrong.

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
- **A language ships when its native review passes, not when its draft lands.** ⚠️ **Retracted by
  [ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md)** — it ships when the
  audit passes; the staging directory below is unaffected, and did its job right up to promotion.
  Promotion is four edits
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
- The gate checks four things, and the second is the one no test could hold: resources **missing** from a
  locale (split by whether this branch introduced them, so the list is the work rather than the debt),
  translations gone **stale** because the English moved on this branch and they did not, **orphans** a
  rename left behind, and — added 2026-08-17 — an **unusable comparison**: no merge base means no "before",
  which does not make the stale check fail but makes it *disappear*, so the gate now refuses to pass rather
  than silently dropping to the checks that still work. `--report` prints the same list and exits 0, for use
  mid-branch.
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
⚠️ **Amended 2026-08-17.** No test holds tone and none was built to. What ships in its place is an audit of
the rules that *outrank* tone — blame, inference from silence, advice — and a report row that puts the tone
question to the people who read the language every day. See
[ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md).

## Gate

- ~~**Every screen read end to end in each language by a native speaker**, with no English left behind —
  ADR-0013's original promise, now nine times. A language that has not had this does not ship.~~
  ⚠️ **Replaced 2026-08-17 by [ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md).**
  What a language must pass instead, and all three did: an **ethics audit** of its high-consequence
  resources against ADR-0026, ADR-0001 and *observe-never-advise*, plus a blame- and alarm-word scan of the
  whole file; a **back-translation and argument-role pass** over every multi-argument string and every
  string that substitutes a name; and a **compile-and-render check on the phone**, which is the only thing
  that sees clipping. "No English left behind" survives untouched — it is `TranslationTest`'s job and always
  was. The **report row in the language picker** is what carries the part none of this reaches.
- `TranslationTest` green across all nine locales; `AppLanguageTest` green across all three declarations.
- **Plural categories correct per language**, checked on a real count in each: 1, 2, 5 and 22 of
  something, in `cs` and `uk` as well as `pl`, where the *few*/*many* split is where a wrong table shows.
- No string in any locale says *missed* or *overdue* outside Phase 4's care reminders, and no banned-word
  list entry appears in its own language's file.
- The switcher offers nine languages by endonym, and switching to each restarts into that language.
- `settings_language_*` are `translatable="false"` and appear in no `values-<locale>` file.
- ✅ The Play listing carries title, short and full description in all nine — written 2026-08-17, and the
  English one rewritten from 1.0 scope to 1.6 first, so the seven were not translated from stale copy.
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
   plural reddens the build. `scripts/translation-gate.py` likewise, on each of its failure modes — and
   re-proven **against CI itself** on 2026-08-17, one pushed commit per mode, which is how the stale check
   was found to have been silently dead there.
2. ✅ **Endonyms done 2026-08-16**, along with `app_name`'s general rule and `med_editor_name_placeholder`
   (a medicine brand name). The brief is [`translator-brief.md`](translator-brief.md); its per-language
   banned lists are drafts until each native reviewer confirms their own row.
3. **Re-prove the locale-aware capture driver** — built in Phase 7.5 and proven there on `pl`, so what is
   owed here is only that its needle table still resolves once seven locales exist. Every new needle is a
   claim that some resource still says what the table thinks it says.
4. Draft all seven into `translations/`, validated by the test in place. ✅ **Done 2026-08-17** —
   `de`, `es` and `fr` on 2026-08-16, `it`, `pt-BR`, `cs` and `uk` the next day, 685/685 each —
   along with the staging area this step assumed and nothing had yet built: `TranslationTest` reads
   `translations/<tag>/strings.xml` beside `values-<qualifier>/`, holds a draft to the same
   assertions, and rejects a tag that is both staged and shipped — promotion is a *move*, and two
   files for one language drift. `translations/` is declared a test input in `app/build.gradle.kts`
   for the reason `src/main/res` already was: a file Gradle cannot see is a file the test reports a
   stale verdict on. The gate prints a draft's completeness under a heading of its own and never
   gates on it, because the merge rule is about what ships.
   The German draft's own decisions are in [the per-language record below](#what-the-seven-drafts-found--the-per-language-record); the one worth carrying to the next six is
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
   is rather than checked against Polish's. Both per-language records are [the per-language record below](#what-the-seven-drafts-found--the-per-language-record).
   **Italian closes both of those questions rather than adding to them.** `care_every` is *Ogni
   %1$s* and needs no label — *ogni* governs a bare unit and a counted gap alike — so the split is
   **two of four**, and Spanish stops being the exception: ask a new language which side it falls
   on rather than expecting the label. And §7.4 comes back **empty** for the first time, which is a
   result and not a skipped check: Italian declines nothing *and* need not elide (*foto di Alice*
   is correct), so no string was reordered. What Italian pays instead is §7.2, in Polish's own
   coin — *in* + the article contracts (*negli*, *nell’*) and the app cannot do it at run time, so
   the chart windows are pre-inflected exactly as Polish's are. **The technique generalises past
   the language it was written for; the trap it answers does not.** Its own record is [the per-language record below](#what-the-seven-drafts-found--the-per-language-record).
   **Portuguese settles the `care_every` question and opens one nothing had looked at.** *A cada*
   governs both, so the split is **three of five** and German's and French's label is the minority
   shape rather than the expected one; §7.2's pre-inflection is paid in Italian's coin (*em* + *os*
   → *nos*), §7.4 comes back empty a second time, and the divergence French found between
   *weigh-in* and *weighing* shows up a third. What is new is a **plural** trap, and it is the
   first thing in four drafts that no earlier record could have warned about: CLDR gives `pt`
   *one: i = 0..1*, so **a count of zero renders the singular item** — correct in French, wrong in
   Brazilian Portuguese, and unfixable from the plural table because the category is right and the
   language disagrees with it. That turns the check into a question about **the code**, not the
   copy: which counts can actually be zero? Three can, and two were recast as labels rather than
   sentences. **Ask this of `cs` and `uk` before drafting them** — Slavic `one` excludes zero, so
   they should come back clean, and confirming that is cheaper than discovering it. Its own record
   is [the per-language record below](#what-the-seven-drafts-found--the-per-language-record).
   **Czech was asked, and the answer held**: `one` is *i = 1*, zero lands in `other`, and the
   genitive plural is what Czech wants there anyway, so all three of Portuguese's counts stay
   ordinary sentences. What the same question turned up instead is that **four plural categories
   are not four reachable ones** — `cs` spends its `many` on *fractions* (*1,5 dne*), where `pl`
   and `uk` spend theirs on ordinary integers, so a Slavic row can be as unreachable as a romance
   one. The table above counts categories and says nothing about which of them render; that gap is
   now closed in the brief rather than here. And Czech adds the trap **no** plural table can
   reach — *the predicate agrees with the count* (*jsou 3 měsíce* against *je 6 měsíců*), so a
   sentence that substitutes a counted phrase has to be built on a verb that does not vary. Ask it
   of `uk`, which has the same grammar and the same substitution list.
   Czech is also the first draft to pay **§7.3 on both halves** — its past tense agrees with the
   addressee, so no sentence in the file puts *you* in the past — and the most expensive **§7.4**
   of the six, which is where it leaves something reusable: besides Polish's colon, **a declined
   common noun in front of the name carries the case for it** (*fotky králíka %1$s*), and a name in
   **subject** position needs nothing at all (*Kolik let má %1$s?*). Two of six languages now lose
   `care_every`'s sentence for agreement rather than for a missing preposition, which makes the
   split three and three: the label is neither the expected answer nor the exception. Its own
   record is [the per-language record below](#what-the-seven-drafts-found--the-per-language-record).
   **Ukrainian closes the step and answers three of the questions the six before it left open.**
   Portuguese's zero trap comes back clean a second time, so both Slavic rows are confirmed rather
   than assumed — but the pair is **not** the same shape underneath: `cs` spends `many` on fractions
   where `uk` spends `other` on them, so a draft written off the other Slavic row would have put the
   genitive plural in the wrong slot. Czech's verb-agreement trap is real here too and has a cheaper
   answer, **the zero copula** — *Між цими двома зважуваннями — %1$s*, a dash where Czech needed a
   verb spelled alike in both numbers. And `care_every` **survives**, which nothing predicted:
   *кожен* has exactly Czech's agreement problem, but *Раз на %1$s* governs a bare unit and a counted
   gap alike, so the split is four of seven keeping the sentence. That reframes the question the last
   five drafts were asked — not *does your word for "every" govern both*, but **does any idiom of
   yours** — and it is worth putting back to the Czech reviewer, since *jednou za %1$s* may be the
   same escape there.
   What Ukrainian adds that no earlier record could have warned about is a **third unknown gender:
   the vet's**. §7.3 has been read as the owner and the bunny for six drafts; English hides the third
   behind *they*, and four strings needed the impersonal plural for it. It also makes the **address
   form and §7.3 one decision** — *ти* costs the past tense outright, where *ви* would buy it back,
   because the polite form takes the genderless plural past. Both are whole-file choices, and both
   are the reviewer's. Its own record is [the per-language record below](#what-the-seven-drafts-found--the-per-language-record).
5. ✅ **All seven promoted 2026-08-17** — four edits, one commit, one language, as written; what changed is
   the trigger, which is now the audit rather than a native read
   ([ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md)). `translations/` is
   gone and the gate reads **688 × 8, complete**.
   **Promotion was rehearsed first — promote all seven, build, install, screenshot, revert — and that
   throwaway is where the value was.** A staged draft is never compiled, so it was the first time any of
   the seven met `aapt2`. All seven compiled, which was not a given: French carries ~200 typographic
   apostrophes and a missed escape would have surfaced exactly here and nowhere earlier. It found **four
   clipped navigation labels** (`destination_observations` in de/es/uk, `destination_care` in de/it),
   **`ImpliedQuantity` false positives** in fr/pt-BR/uk, and **stale `DRAFT` headers**. **None of the three
   was predictable from a draft, and none needed a native speaker to see** — which is most of the argument
   for the audit that replaced the review, and the reason the rehearsal is worth repeating for a tenth
   language rather than trusting a green `TranslationTest`.
   ℹ️ **The clipping fix is the label, not the string.** `TextAutoSize.StepBased(8sp, 11sp)` in
   `Navigation.kt`; wrapping stays rejected for 1.0's reason. With no reviewer, prefer a fix that needs no
   vocabulary judgement — *Beobachtung*, *observación* and *спостереження* are `CONTEXT.md`'s concept
   rather than a phrasing, and trading them for something shorter is exactly the call a reviewer was for.
   Consequence: German keeps *Pflege &amp; Medis* and Italian *Cure e farmaci*, so **six of nine keep the
   meds half**.
6. ✅ **Listing text for all nine, 2026-08-17** — [`store-listing.md`](store-listing.md), 27 fields, every
   one measured against Play's 30 / 80 / 4000 limits. Screenshots later, driven by install data.
   ⚠️ **The English description had to be rewritten before anything could be translated from it.** It was
   still cut at 1.0 and described an app with no care reminders, no watch, no vet visits, no medications
   and no documents — 1.0 was the only build a listing had ever been written for, and nothing since had
   needed one, because nothing since had been uploaded. Translating *that* seven times is the exact
   failure this phase's ordering argument exists to prevent, one surface over: the listing would have been
   translated once against 1.0's copy and again the moment it caught up. So English went to 1.6 scope
   first, Polish was brought level, and the seven were written from there.
   ⚠️ **Romance prose runs about a tenth longer than the English it comes from, and 4000 characters is a
   real ceiling.** French came in at **4134** and needed thirteen trims that cut words rather than claims;
   it now sits at 3992 and Italian at 3993, against English's 3628. **Those two have no headroom left** —
   a paragraph added to the English description cannot simply be translated into them.
   ℹ️ **No locale is a translation of another**, deliberately. Each carries its own keywords and the
   vocabulary its own draft settled on (*Köttel*, *cagarrutas*, *crottes*, *palline*, *bolinhas*, *bobky*,
   *котяхи*), because a listing that uses the clinical word for what the app calls something else reads as
   a different app. All nine carry ADR-0001's closing *"a record, not a diagnosis"* paragraph, which is the
   first thing to check in any locale that gets rewritten.
   ⚠️ **The copy must not go up before the build it describes is on the track** — Play treats advertising
   absent features as a violation, and the tracks are still on 1.0.0 / 1.3.

## What the seven drafts found — the per-language record

Moved here from `DOD.md` §7 on 2026-08-17, when that section was cut down to what is still open. It is
kept rather than deleted because each block ends in **four decisions the native read-through was meant to
confirm**, each with a **named fallback** — and under
[ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md) there is no read-through, so
those fallbacks are live: they are the pre-decided answers waiting for the first user report that touches
one. The running tables the blocks build between them — which languages keep `care_every` as a sentence,
what §7.1's *Normal* costs, where §7.3 falls, whether §7.4 is empty — are what a tenth language would be
drafted against.

### German (`de`)

🟡 **`de` drafted 2026-08-16** — 685/685, and the staging area itself now exists: `TranslationTest`
holds a draft to every rule it holds a shipped file to (proven by breaking the German plural
and the German format argument and watching both redden), `translations/` is a declared test
input so Gradle cannot report a stale verdict on it, and the gate prints what a draft still
owes without ever gating on it.

ℹ️ **German costs nothing to §7.3, and that is the surprise.** The perfect tense with *haben*
carries no gender and predicate adjectives do not inflect, so *"Es ist archiviert"* and
*"Lebt allein"* are simply correct — the trap that rewrote two Polish strings does not exist
here. `care_every` is where German pays instead: the host takes either a bare unit (*Woche*)
or a counted gap (*6 Wochen*), and no German preposition governs both — *"Alle Woche"* is not
idiomatic and *jede/jeder/jedes* would have to guess the unit's gender. It is `Rhythmus: %1$s`,
a label rather than a sentence, and it is the first thing to put in front of the reviewer.

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
`destination_care` = *Pflege & Medis* (a bottom-nav tab, and *Pflege & Medikamente* does not
fit — the fallback is *Pflege* alone, losing the meds half rather than clipping it); *Klo* for
the litter tray and *Köttel* for droppings (what German rabbit owners say, where *Kot* is the
clinical word §5 rejects); *Im Blick* for the watch (*Beobachtungszeitraum* collides with
Beobachtung and *Wachphase* reads as sleep); and three breed rows that are mappings rather
than translations — Harlequin (*Japaner* in the standards, *Harlekin* in use), the UK *Polish*
(*Hermelin*), and the lop family, which splits differently either side of the Atlantic.
German's banned list needed no argument: *überfällig* appears nowhere, `care_due_overdue` is
*%1$s nach dem Termin* and `care_notification_overdue` is *der Termin ist verstrichen* — the
same move Polish makes with *po terminie*. `dose_status_skipped` is *Ausgelassen*, agentive
the way *Pominięta* is.

### Spanish (`es`)

🟡 **`es` drafted 2026-08-16** — 685/685, mechanically green, into `translations/es/`.
**All seven are in as of 2026-08-17.**

ℹ️ **Spanish is the first language to pay §7.3 on the bunny's side rather than the
owner's**, which is the reverse of German. The owner's half is free — the compound perfect
with *haber* does not agree with its subject, so *has guardado* and *he mirado* carry no
gender and the file needs no rewrite. But *conejo* is masculine where *Kaninchen* is
neuter, so an adjective about the animal has to pick one. **The policy is the masculine
generic, Spanish's unmarked form, stated in the file's header rather than hidden** — with
the genderless wording preferred wherever it is equally natural: *Vive sin compañía* (not
*solo*), *%1$s (en el archivo)* (not *archivado*), and `archived_banner` / `archived_on`
both recast around the noun. No parenthesised suffix appears anywhere, which is the rule
that actually matters.

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
`destination_care` is ***Cuidados* alone** — Spanish has no established short form for
"meds" the way German has *Medis*, and *Cuidados y medicación* is twenty-one characters in
a bottom-nav tab, so the meds half is lost rather than clipped and 5e's point with it;
***cagarrutas*** for droppings, the *Köttel* decision one language on (fallback
*excrementos*, and *bolitas* was rejected outright as exactly the food-pellet ambiguity the
brief warns about); ***seguimiento*** for the watch, where *vigilancia* carries alarm
ADR-0001 forbids; and ***Bienvenida a Binky*** as a noun, because *Bienvenido/a* would
gender the owner on the first screen they ever see.

⚠️ **Four breed rows are mappings rather than translations**, as German's three were:
**Dutch → *Holandés*** against **Netherland Dwarf → *Enano holandés***, which Spanish
genuinely collides where German splits them with *Farbenzwerg*; the **lop family**
(*belier*), which splits differently either side of the Atlantic; **Himalayan →
*Himalayo***, where continental standards say *Ruso*; and the UK **Polish → *Polaco***.

ℹ️ **Three traps cost Spanish nothing, and it is worth knowing which.** §7.1's *Normal* —
Polish's five distinct forms and the file's single biggest trap — is one word six times,
because *normal* is invariable in gender. §7.2's `care_every` is *Cada %1$s*, and *cada*
governs a bare unit and a counted gap alike, so the sentence German had to give up
survives. And `photo_import_partial` earns its plural for the second language running:
*no se pudo leer* against *no se pudieron leer*, where English has nothing to vary.

### French (`fr`)

🟡 **`fr` drafted 2026-08-16** — 685/685, mechanically green, into `translations/fr/`.

⚠️ **German's `care_every` problem is not German's**, which is what the last draft asked
to have checked. *Tous les jours* but ***toutes** les semaines*, and the app cannot know
which unit it is about to substitute; the unit plurals cannot carry the article either,
because the editor puts them beside a number field of their own. So the host became a
label — `Rythme : %1$s` — and that decided `care_due_in` and `care_due_overdue` in turn:
*À faire dans %1$s* and *À faire depuis %1$s*, an invariable infinitive that states the
timing and judges nothing. **Two of three languages pay it**, which makes Spanish's
*Cada* the exception rather than the rule.

⚠️ **§7.4 exists in French, for a reason its entry does not name.** French has no cases —
but *de* elides before a vowel, and the app can no more elide a name the owner typed than
Polish can decline it. *"Photo de Alice"* is simply wrong. Six strings put the name first
or drop the preposition: `home_about_bunny`, `photo_description`,
`bunny_avatar_description`, `watch_expired_title`, `watch_notification_title` and
`document_page_description`, whose second argument is a document title. A seventh,
`photo_gallery_empty_help`, keeps its argument in the same job by changing the verb —
*des photos qui **montrent** %1$s* — which is §7.6's trap and §7.4's in one string.

⚠️ **Apostrophes are typographic (’), not `\'`.** Correct French typography, and it also
removes a class of failure the other drafts never faced: French needs some two hundred
escapes, and **a missed one would not surface until promotion**, because a staged draft is
never compiled. A later edit must not "fix" them back.

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
`destination_care` is ***Soins* alone** — Spanish's outcome for Spanish's reason, since
*médocs* is too casual for a label seen on every screen and *méds* is not French;
***crottes*** for droppings (the *Köttel* / *cagarrutas* decision a third time, where
*excréments* is the clinical word §5 rejects and *crottins* belong to horses), with
***caecotrophes*** beside it; ***suivi rapproché*** for the watch, where *surveillance*
carries the alarm ADR-0001 forbids; and ***Sautée*** for `dose_status_skipped`, agentive
the way *Pominięta*, *Ausgelassen* and *Omitida* are — *la dose s’est sautée* is not
French, which is §6's own test. The fallback is *Omise*.

ℹ️ **French finds a §7.1 divergence English hides**: *weigh-in* and *weighing* are two
words there and one word here. `care_type_weigh_in` is ***Contrôle du poids*** rather than
*Pesée*, or `care_history_weight_help` would have read "les pesées comptent comme des
pesées". Nothing predicted it — it shows only from the sentence downstream.

⚠️ **Six breed rows are mappings rather than translations**: the **lop family** (*bélier*),
**Himalayan → *Russe*** — the continental standards' name, where Spanish went the other
way with *Himalayo* — the UK **Polish → *Hermine*** (German's *Hermelin*), **Dutch →
*Hollandais*** against **Netherland Dwarf → *Nain néerlandais***, which French keeps apart
where Spanish collides them, plus **Mini Rex → *Rex nain*** and **Rhinelander → *Rhénan***.

ℹ️ **Three traps priced, against the table the other two drafts started.** §7.1's *Normal*
costs **three** forms of six, between Polish's five and Spanish's one. §7.3 splits the way
Spanish's does rather than German's — the owner's half free, because the compound past
with *avoir* does not agree with its subject; the bunny's half paid, because *lapin* is
masculine, so the masculine generic is stated in the header and genderless wording used
wherever it is equally natural. And `photo_import_partial` earns its plural for the
**third** language running (*n’a pas pu être lue* / *n’ont pas pu être lues*) while
needing a **dodge** neither of the others did: the *added* count sits on the same string
and the wrong plural axis, so it is a noun — *Ajout : %1$d sur %2$d* — rather than a
participle that would be wrong half the time.

### Italian (`it`)

🟡 **`it` drafted 2026-08-17** — 685/685, mechanically green, into `translations/it/`.

ℹ️ **`care_every` survives as a sentence, which settles what the German draft asked.**
*Ogni* governs a bare unit and a counted gap alike (*ogni settimana*, *ogni 6 settimane*),
so Italian needs neither German's label nor French's, the unit plurals stay in the citation
form, and `care_due_in` / `care_due_overdue` are rewritten for §6's reason rather than for
grammar: *Da fare tra %1$s* and *Da fare da %1$s*. **Two of four pay it, two do not** —
Spanish was not the exception it looked like, so the thing to ask a new language is which
side it falls on, not whether it is the odd one out.

ℹ️ **§7.4 costs Italian nothing, and that is the first nil result in four.** Italian
declines nothing *and* does not have to elide — *foto di Alice*, *informazioni su Alice*,
*a Alice* are all correct as they stand, the euphonic *ad* being a style choice rather than
a rule — and no article precedes a first name. Every name-substituting string was read with
a vowel-initial name in it and none needed reordering, so `home_about_bunny` keeps English's
shape. **"Nothing" is a legitimate answer to §7.4**, not a sign the check was skipped.

⚠️ **Italian's own §7.2 trap is the preposition swallowing the article**, which is Polish's
problem in a language with no cases: *in* + *gli* is **negli**, *in* + *l’* is **nell’**, and
the app cannot contract at run time. The four `weight_chart_window_*` are **pre-inflected** —
*negli ultimi 30 giorni*, *nell’ultimo anno* — exactly as Polish pre-inflects for the
locative, which is the first reuse of that technique outside the language it was written
for. Both hosts take the fragment bare; a third host with a different preposition would
break all four at once.

⚠️ ***Saltata* stays banned, which is the mirror image of Polish's *pominięta*.** Same test,
opposite answer: *saltare* is agentive when transitive (*ho saltato la dose*), but *è saltata
la dose* is idiomatic for a thing that simply fell through, and a status chip has no subject
to disambiguate it. `dose_status_skipped` is ***Omessa*** — Spanish's *Omitida* and French's
fallback, reached independently. *Mancata*, *dimenticata* and *scaduta* appear nowhere, and
`backup_folder_forget` is *Rimuovi questa cartella* rather than *Dimentica*, because the gate
reads the file for those words rather than for their sense.

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
`destination_care` is ***Cure e farmaci*** — **the meds half survives**, where Spanish and
French both dropped it, at fourteen characters, the width German accepted for *Pflege &amp;
Medis*; **the capture driver is what settles it**, and the fallback is *Cure* alone.
***Palline*** for droppings, where *feci* is the clinical word §5 rejects and *cacca* the
baby talk §3 rejects — Spanish's objection to *bolitas* does not carry over, because pelleted
food is *mangime* or *pellet* in Italian, never *palline*. ***Controllo ravvicinato*** for the
watch: *sorveglianza* carries the alarm ADR-0001 forbids, *osservazione* collides with the
record type, *monitoraggio* is clinical. And ***terapia*** for the medication course, because
**English *care* and *course* both want *cura*** and the two meet on one screen — a §7.1
collision running the other way, two English words folding into one Italian one and split by
hand.

ℹ️ **Three traps priced against the running table.** §7.1's *Normal* costs **three** forms of
six, French's count: *Normale* four times, ***Normali*** for the droppings' size — which
agrees with the pellets rather than with the measurement, the only split in the file that
turns on number instead of gender — and *Beve normalmente* for water. §7.3 splits the
Spanish/French way, the owner's half free because *avere* does not agree with its subject
(the trend flag is *Dal %3$s %1$s ha perso %2$s*), the bunny's half paid — with **two dodges
neither of them had**: the possessive agrees with the thing possessed (*i suoi dati*), and a
pronoun can hang off the common noun *il coniglio*, whose masculine is a fact about the word
rather than a guess about the animal. And `photo_import_partial` earns its plural for the
**fourth** language running, needing French's noun dodge for the added count.

⚠️ **`observation_not_checked` is the string most worth reading in place.** One resource sits
under four fields of two genders — *appetito* and *umore* masculine, *attività* and *acqua*
feminine — so any participle is wrong on half the screen. It is *Nessun controllo*, a noun
phrase, which is also what keeps it a fact about the record (ADR-0001).

### Brazilian Portuguese (`pt-BR`)

🟡 **`pt-BR` drafted 2026-08-17** — 685/685, mechanically green, into `translations/pt-BR/`.
Brazilian throughout, not European: *celular*, *tela*, *arquivo*, and the gerund
(*está comendo*, never *está a comer*). pt-PT would be a second locale, not an edit to this
one.

⚠️ **Portuguese's own trap is the plural category `one`, and it is the first one no other
language's record warns about.** CLDR gives `pt` *one: i = 0..1* — French's rule, not
Italian's — so **a count of 0 renders the singular item**. In French that is correct
(*0 jour*); in Brazilian Portuguese it is wrong, because nobody writes *0 página*. **No
plural table can fix it**: the category is right and the language disagrees with it, so the
only question is whether a given count can actually be zero. The code was read for it and
**three can**: `backup_restored_overlaid` renders unconditionally (an Essential-scope backup
of a bunny with no avatar restores none), `delete_records_sole_owned` renders whenever the
second delete dialog opens and `DeleteConfirmation.kt` opens it when *either* count is above
zero, and `document_page_count` can be zero for a document whose pages were all removed. The
first two are recast as labels — *Imagens vindas do backup: %d* — which is right at 0, 1 and
2 alike; **the third is left wrong knowingly**, because a label reads badly in a list row and
the state is rare. Everything else is guarded at ≥ 1, checked one by one. **Every future
plural in this app now owes this question.**

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
***consulta*** for a vet visit, which is §5's *Avoid* column overruled on purpose — the brief
rejects *consultation*, and in Brazil *consulta* is simply what the appointment is called
while *visita* means somebody coming to see **you**; the vocabulary's intent survives in the
copy instead, since nothing in `visit_*` mentions money. ***Backup*** left in English, the
one word where ADR-0013's "no English left behind" and ordinary Brazilian usage disagree
(*cópia de segurança* reads as Portugal) — roughly thirty strings if the ruling goes the
other way, and note `destination_home` went the other direction as ***Início***.
`destination_care` is ***Cuidados*** alone, since *Cuidados e remédios* is nineteen
characters and there is no short BR form for *meds* — **three of five now drop the meds
half**. And ***bolinhas*** for droppings, the *Köttel* / *cagarrutas* / *crottes* /
*palline* decision a fifth time: Spanish rejected *bolitas* for colliding with food pellets
and Portuguese has no such collision, because pelleted food here is *ração*. Cecotropes are
*cecotrofos*, the tray is a *caixa de areia*, and the watch is ***acompanhamento***
(*vigilância* carries ADR-0001's alarm, *observação* collides with the record type).

ℹ️ **`dose_status_skipped` is *Pulada*, and it is Italian's finding with the opposite
answer.** Same metaphor, §6's same test: *pulei a dose* is agentive and *a dose pulou* is
not Portuguese at all, where *è saltata la dose* **is** ordinary Italian — which is exactly
why *saltata* stays banned there and *pulada* passes here. The fallback pair, if a reviewer
finds it too colloquial, is *Omitida* / *Administrada*, and it moves together with
`dose_status_given` (*Dada*): the two have to share a register.

ℹ️ **Three traps priced, and two questions closed.** §7.2's `care_every` is ***A cada %1$s***
and governs a bare unit and a counted gap alike, so **three of five keep the sentence** —
German's and French's label is now the minority shape, not the expected one. §7.2's
*pre-inflection* is paid in full, in Italian's coin: *em* + *os* is **nos**, so the chart
windows carry the contraction (*nos últimos 30 dias*, *no último ano*) and both hosts take
them bare — third language for the technique. §7.1's *Normal* costs **three** forms of six
(*Normal*, ***Normais*** for the droppings' size, ***Bebendo normalmente*** for water, whose
neighbouring chips are gerunds), and §7.1's other rows cost **nothing**: *Nome* serves both
the bunny and the vet, *Não se sabe* serves both *Unknown*s — identical on purpose, and the
second is also the only genderless option. ***Controle de peso*** for the weigh-in reminder
against *pesagem* for one weighing, the French/Italian divergence found a **third** time.
§7.3's owner half is nearly free (the simple past does not agree), paid twice:
*Boas-vindas ao Binky* and *Você pediu este lembrete*.

ℹ️ **§7.4 comes back empty for the second time, and this one has a caveat worth keeping.**
Portuguese neither declines nor elides, so *Sobre Alice* and *Foto de Alice* stand and not
one string was reordered — **but the trap exists one register away**: spoken Brazilian
Portuguese puts an article before a first name (*a Alice*) and *de* + *a* contracts to *da*.
The file stays in the written standard, which takes no article, and that is what keeps the
contraction out of reach. A reviewer who prefers the spoken register **cannot have it** —
the app cannot know a name's gender, so *da/do* is unproduceable.

⚠️ **The breed rows go the other way from every earlier draft.** Brazil's pet-rabbit
vocabulary follows ARBA rather than the European standards, so **the whole lop family stays
in English** (*Mini Lop*, *Holland Lop*, *French Lop*, *English Lop*, *Dwarf Lop*, *American
Fuzzy Lop*) where Spanish, French and Italian each had a native name (*belier*, *bélier*,
*ariete*) — the single row most likely to be wrong, and one for a Brazilian breeder rather
than a dictionary. The same lean argues *Polonês* for the UK **Polish**, and it is
***Arminho*** anyway, joining *Hermelin* / *Hermine* / *Ermellino*, with the choice handed to
the reviewer. **Himalayan → *Himalaio*** goes Spanish's way against French's and Italian's
*Russe* / *Russo*; **Dutch → *Holandês*** stays apart from **Netherland Dwarf → *Anão
holandês***; **Lionhead → *Cabeça de leão*** is genuinely current here unlike the English
names around it; and **Mixed / unknown → *Sem raça definida***, Brazil's real idiom (SRD),
which is why it earns first place rather than being sorted there.

### Czech (`cs`)

🟡 **`cs` drafted 2026-08-17** — 685/685, mechanically green, into `translations/cs/`, and
the staging harness re-proven on a locale it had never seen: dropping `few` from one Czech
plural reddens `TranslationTest`, so a brand-new draft directory is checked rather than
merely counted.

✅ **The question Portuguese left for the Slavic pair comes back clean, and it was worth
asking rather than assuming.** CLDR gives `cs` *one: i = 1*, so **zero lands in `other`** —
*0 stran*, *0 záznamů*, *0 obrázků* — which is the genitive plural Czech actually wants.
The three counts Portuguese had to recast as labels stay ordinary sentences here, and
nothing in the file is knowingly wrong at zero. `uk` should answer the same way.

⚠️ **Czech's own plural finding is that four categories are not four reachable ones.**
`many` is *v != 0* — the **fraction** form (*1,5 dne*), spelled genitive singular — where
Polish and Ukrainian spend theirs on ordinary integers. So a Slavic row can be as
unreachable as a romance one, and the draft fills it with what a decimal would really take
rather than mirroring `other`, except in whole sentences where a fractional count is
nonsense. **The brief's plural table is right about the count of categories and silent
about which of them can render**, which is now written down in §7.5.

⚠️ **The trap no plural table can see: the predicate agrees with the count.** *Jsou 3
měsíce* against *je 6 měsíců* — so `trend_flag_long_gap`, which substitutes a gap phrase,
would need two verbs. It is built on ***dělí***, whose 3rd person singular and plural are
spelled alike (*Ta dvě vážení dělí %1$s*). Every §7.2 substitution owes this check in an
inflecting language, and English cannot show it.

⚠️ **§7.3 is paid on both halves, which no earlier draft has been.** German paid neither;
Spanish, French, Italian and Portuguese paid the bunny's half only. Czech's past tense
agrees with the addressee, so **no sentence in this file puts "you" in the past** —
imperatives, the present and impersonal passives (*zapsáno*, *nepodařilo se*) carry it, and
several strings were rewritten for it including two notifications. The bunny's half is the
masculine generic *králík*, stated in the header, with the graded chips dodging it entirely
by agreeing with their **field noun** (*chuť*, *nálada*, *aktivita* are all feminine) rather
than with the animal. ℹ️ **A third party needs the same care**: *Binky* has no settled
gender in Czech, so the app is never the subject of a verb that has to agree with it — the
feminine common noun *aplikace* stands in where one is unavoidable.

⚠️ **§7.4 is the most expensive of the six drafts, and it produced a technique worth
carrying.** Beyond Polish's colon (*%1$s — informace*, *Smazat: %1$s?*), Czech puts a
**declined common noun in front of the name and lets it carry the case** — *fotky králíka
%1$s*, *pro králíka %1$s*, *z dokumentu %2$s* — so the sentence keeps its shape and
`photo_gallery_empty_help` keeps its argument in its real job. And one string needed nothing
at all: *Kolik let má %1$s?* puts the name in **subject** position, where the citation form
is already correct. Ask that before reordering anything.

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
`destination_care` is ***Péče a léky*** at eleven characters — **the meds half survives**, a
third language keeping it against three that dropped it, because *léky* is the ordinary word
and not an abbreviation; the fallback is *Péče* alone. ***Bobky*** for droppings, the
*Köttel* / *cagarrutas* / *crottes* / *palline* / *bolinhas* decision a sixth time, where
*trus* is the clinical word §5 rejects and pelleted food is *granule*, so Spanish's
collision does not arise. ***Sledování*** for the watch — and Czech is the first language
where **Polish's own collision does not happen**: *pozorování* stays the record type,
*sledování* is the watch, two ordinary words where Polish had to qualify one. And ***kúra***
for a medication course (fallback *léčba*), which avoids Italian's *cura* collision because
care is *péče*.

ℹ️ ***Vynechána* comes off the banned list, and it refines §6's test rather than repeating
it.** Czech *vynechat* **does** have an intransitive life — *motor vynechává* — which by
Italian's rule alone would condemn it. But that reading takes a machine as its subject and
***dávka vynechala* is not Czech**, so the chip has no agentless reading. The question is
the intransitive life **with this noun**, which is Portuguese's version of the test, and it
is now in the brief. `dose_status_given` is *Podána*; the fallback pair is *Přeskočena* /
*Podána*, and the two move together.

ℹ️ **Three traps priced, and one surprise.** §7.1's *Normal* costs **three** forms of six —
the romance count, in a language with seven cases — because ***normální*** is one of the
adjectives Czech does not inflect in the nominative singular, which is *Spanish's* reason.
The two that diverge are *Normálně* for the amount and *Pije normálně* for water. §7.1's
other rows split the way Polish's do (*Jméno* / *Jméno nebo název*, *Neznámé* / *Neví se*),
and ***Kontrolní vážení*** against plain *vážení* is the French weigh-in divergence found a
**fourth** time. §7.2's pre-inflection is Polish's exact locative technique, second Slavic
and fourth language overall. And `photo_import_partial` earns its plural for the sixth
language running, but only because the file **names the noun** (*%3$d soubor nešel přečíst*)
— the impersonal phrasing that came first would have collapsed all four items into one.

⚠️ **Two breed rows sit one word apart and want a rabbit person, not a dictionary**:
**Flemish Giant → *Belgický obr*** against **Belgian Hare → *Belgický zajíc***, because the
Czech standard names the Flemish Giant after Belgium. Otherwise Czech has a national
standard (ČSCH) and so translates **more** rows than any earlier draft: the lop family is
*beran* (*Zakrslý beran*, *Anglický beran*, *Francouzský beran*) with **Holland Lop left in
English**, because *Zakrslý beran* is already the Dwarf Lop and mapping both onto it would
lose a breed; **Polish → *Hermelín***, a fifth language reaching the continental name;
**Himalayan → *Ruský***, French's and Italian's way; **Harlequin → *Japonský***, the
standard's name where pet shops write *Harlekýn*; **Tan → *Ohnivák***, **Rhinelander →
*Rýnský***, **Checkered Giant → *Německý obrovitý strakáč***, **Lionhead → *Lvíček***, and
**Mixed / unknown → *Kříženec / neznámé plemeno***.

### Ukrainian (`uk`)

🟢 **`uk` drafted 2026-08-17 — the seventh and last, so every shipped-language draft now
exists.** 685/685, mechanically green, into `translations/uk/`, and the harness re-proven able
to fail on it: dropping `few` from one Ukrainian plural reddens `TranslationTest`.

✅ **The Slavic pair both come back clean on Portuguese's zero trap**, which is what the
question was for. `uk` puts 0 in **`many`** — *0 сторінок*, *0 записів*, *0 зображень* — the
genitive plural the language wants, so the three counts Portuguese recast as labels stay
sentences here too. Two checks, two clean answers, and the cost of asking was an hour against
a defect that renders in a rare state and reads as fluent.

⚠️ **The two Slavic rows are not the same shape, and reading one off the other would have put
the genitive plural in the wrong slot.** Czech spends `many` on fractions; **Ukrainian spends
`other` on them**, as Polish does, and its `many` is an ordinary large-integer category. Four
categories, one unreachable, different one each time — §7.5's finding generalises, its
*instance* does not.

✅ **`care_every` survives as a sentence, and not for the reason five drafts have been asking
about.** *Кожен* has exactly Czech's agreement problem (*кожен тиждень*, *кожні 2 тижні*,
*кожних 6 тижнів*), so the label looked certain — but **a different idiom governs both**:
*Раз на %1$s*, because *на* takes the accusative and all four units are spelled there as in the
nominative the plurals already hold. So the split is **four of seven keep it** (es, it, pt-BR,
uk) against three that lose it. ⚠️ **This is worth putting to the Czech reviewer**: *jednou za
%1$s* may be the same escape there, in which case that draft's label was avoidable. The
question a new language owes is not "does your word for *every* govern both" but **"does any
idiom of yours govern both"** — the brief asked the narrower one and got a label three times.

⚠️ **A third unknown gender, and no earlier draft names it: the vet's.** English hides it
behind *they*. *Ветеринар* has *ветеринарка* beside it and the app never asks which, so
`med_editor_amount`, `med_editor_amount_help`, `visit_weight_label` and `vet_delete_body` all
take the **3rd-person-plural impersonal** (*якщо тобі сказали*, *якщо зважували*), which names
nobody and inflects for nothing. §7.3 has been read as owner + bunny for six drafts; it is
three parties, and the third only shows in a language that inflects the predicate.

⚠️ **The address form and §7.3 are one decision here, which they are nowhere else.** *Ти* was
chosen for consistency with the other six, and it costs the past tense outright — so no
sentence in the file puts "you" in the past, exactly as in Czech. But **`ви` would buy the
tense back**, because the polite form takes the *plural* past, which carries no gender at all.
That makes the register a whole-file rewrite either way, and it is the first thing in front of
the reviewer rather than a preference to be noted afterwards.

ℹ️ **Czech's verb-agreement trap has a cheaper answer: the zero copula.** Where the count sits
inside a `<plurals>` the table carries the verb (`watch_days_left`, `delete_records_shared`
have four deliberately different predicates). Where a counted phrase is substituted into a
sentence, `trend_flag_long_gap` is built with **a dash instead of a verb** — *Між цими двома
зважуваннями — %1$s* — and a dash agrees with nothing. Czech reached for a verb spelled alike
in both numbers; removing the verb is the same fix one step earlier.

⚠️ **Four decisions the native read-through has to confirm, not just read past:**
`destination_care` is ***Догляд і ліки*** at thirteen characters — **the meds half survives**,
four of seven now keeping it; the fallback is *Догляд* alone. ***Котяхи*** for droppings, the
*Köttel* / *cagarrutas* / *crottes* / *palline* / *bolinhas* / *bobky* decision a seventh time
(*кал* and *екскременти* are §5's clinical words, *какашки* §3's baby talk, fallback *кульки* —
pelleted food is *гранули*, so Spanish's collision does not arise). ***Нагляд*** for the watch,
where *спостереження* is the record type and *стеження* is surveillance of people — **the
second language after Czech where Polish's collision simply does not happen**. And
***Кличка*** against ***Імʼя або назва***: Ukrainian has a separate word for an animal's name,
so §7.1's *Name* row splits **further apart** here than in any earlier draft rather than
collapsing.

ℹ️ **`dose_status_skipped` is answered with a construction rather than a word.** The whole
*пропущ-* family fails §6's test — *дозу пропущено* has an agentless reading exactly as *è
saltata la dose* does — so what is left is the 3rd-person-plural impersonal, which in Ukrainian
always implies human agents: ***Дали*** / ***Не давали***, moving together as Portuguese said
the pair must. Fallback *Дано* / *Свідомо не дано*. **The same construction answers
`observation_not_checked`** (*Не перевіряли*), which sits under four fields of two genders and
so cannot be a participle — Italian solved that one with a noun phrase, Ukrainian with a verb
that has no subject.

ℹ️ **Three traps priced.** §7.1's *Normal* costs **five** forms of six — Polish's count, the
most expensive of the seven, against Spanish's one. §7.2's pre-inflection is Polish's locative
technique in the accusative (*за останні 30 днів*), third Slavic and fifth language overall,
plus a miniature of it in the two weight-unit options. And `photo_import_partial` earns its
plural for the **seventh** language running, while ***`photo_import_added` does not*** — *фото*
is an **indeclinable borrowing**, so all four items are the same word, which is Czech's *vážení*
finding arriving through a loanword instead of a declension class.

⚠️ **The breed list changes script, which is a decision no earlier draft had to make.** "Keep
the registered name" is invisible in a Latin-script language and glaring in Cyrillic, so the
obscure rows are **transliterated** (*Беверен*, *Британія петіт*, *Тан*, *Тріанта*) rather than
left in Latin — Ukrainian breeders write them that way, and a Cyrillic list with Latin islands
reads as untranslated. The lop family is ***баран***. **Himalayan → *Гімалайський***, not the
continental standards' *російський горностаєвий*, going Spanish's and Portuguese's way against
French's and Italian's *Russe* / *Russo* — and in Ukrainian that is not only a naming
convention. **Polish → *Гермелін***, a sixth language reaching the continental name.
**Flemish Giant → *Фландр***, which incidentally avoids Czech's near-collision: *Бельгійський
заєць* stands alone. ⚠️ **The row to read twice is Dutch (*Голландський*) against Holland Lop
(*Голландський баран*)** — one word apart, where Netherland Dwarf stays clear as
*Нідерландський карликовий*.


## When it closes

Write the results here, tick **Phase 8** in `PLAN.md`'s status list, and empty §7 of `DOD.md`.
