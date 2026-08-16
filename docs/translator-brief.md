# Translator brief

**English (`values/strings.xml`) is the source for every language.** Never translate from Polish or from
any other translation — a second-hand rendering carries the first translator's compromises as if they were
the original's meaning.

**Translate for meaning, not word for word.** Several strings below are deliberately *not* parallel to
their English in any target language, because the English sentence and the target sentence solve the same
problem with different grammar. Where that is true it is written down here with a worked Polish example.

Read this whole file before drafting. It is short on purpose, and none of it is style preference — every
rule here has either an ADR or a bug behind it.

---

## 1. What the app is, in one paragraph

Binky is a free, ad-free Android app made by one person. It keeps the health history of one or more pet
rabbits on the owner's own phone — weights, observations of droppings and wellbeing, vet visits,
medications, scanned paperwork, photos, and reminders for recurring care. There is no account and no
server. **It is a record, never a diagnosis.** The owner is an ordinary pet owner, not a vet.

---

## 2. The three rules that outrank fluency

A fluent translation that breaks one of these is worse than a clumsy one that keeps it, because the failure
is invisible to everyone who cannot read the language.

### 2.1 Never infer a health problem from missing data (ADR-0001)

When the app has no data, it says so **about the record**, never about the animal. Silence means nobody
looked — it does not mean all was well, and it does not mean something is wrong.

- `home_no_weighings` / `home_no_observations` — *"None recorded yet"*. This is a fact about the file.
  It must not become "nothing to worry about", "all fine so far" or "nothing has happened yet".
- `observation_not_checked` — *"Not checked"*. **Never** a polite word for "normal". Every graded field
  (appetite, mood, activity, water) has this state and it is a recorded fact in its own right.
- `observation_symptoms_checked` — *"I looked, and saw none of these"* is deliberately different from an
  empty list. Keep the two distinguishable.

### 2.2 The app observes; it never advises (ADR-0026)

No medical opinion, in any string, ever.

- Nothing may suggest seeing a vet **except** `trend_flag_not_advice`, which is the one string licensed to
  say it, and says it as a conditional the owner controls (*"If you are worried, ask a vet"*).
- `droppings_appearance_blood` is *"Blood"* and nothing more. Not "Blood — see a vet".
- `med_disclaimer` is load-bearing and appears on screen permanently. It must stay a plain statement of
  what the record **is**, never a warning or a disclaimer in tone.
- No exclamation marks on any health string. No word that codes a gap as a failure.

### 2.3 Weight changes are always in grams

`weight_change_up` / `weight_change_down` / `weight_change_none` are grams whatever the display unit says,
because *−0.04 kg* hides the signal that *−40 g* makes obvious. Do not convert, do not round, do not add a
unit that is not there.

---

## 3. Register

- **Source is British English.** *colour*, *diarrhoea*, *laboured*, *licences*, *optimisation*. Match your
  own language's standard written form; do not carry British spelling into it.
  (One deliberate exception: **cecotrope**, not *caecotrope* — see the vocabulary below.)
- **Second person, plain, calm.** The app talks to one owner about their own animal.
- **Sentence case** for buttons, titles and labels. Not Title Case, and not ALL CAPS.
- **Warm, never cute.** No baby talk, no emoji, no exclamation marks.
- **Never blame the owner.** When something failed, the sentence says what happened and what is still
  possible — see `backup_folder_refused`, which ends with *"nothing was lost"*.
- Em dashes (—) and typographic quotes (“ ”) are used throughout. Use your language's own conventions:
  German „ “, French « », Polish „ ”.

---

## 4. Do not translate

| Resource | Value | Why |
| --- | --- | --- |
| `app_name` | Binky | The product name. Also resolves against the *system* locale, not the app's, so a translated copy would rename the launcher icon on a phone whose owner set Binky to English. |
| `settings_language_english` | English | An endonym: each language is named **in itself, in every locale**, so someone stranded in a language they cannot read finds their own by the name they would recognise. Locale-invariant by definition. |
| `settings_language_polish` | Polski | Same. Add one per language as it ships, in that language, `translatable="false"`. |
| `med_editor_name_placeholder` | Metacam | A medicine **brand name**, sold as Metacam in every market this app ships to. Translating it would invent a medicine. |

All four are marked `translatable="false"` in the base file and **must not appear in your file at all** —
not as a copy, not as a translation. A test fails if they do.

### Things that look untranslatable and are not

- **`med_editor_amount_placeholder` — `0.3 ml`.** This is a *number*, and the decimal separator is
  locale-specific: Polish writes **`0,3 ml`**. It sits directly below `Metacam` in the file, and the pair is
  the point — "looks like data, so leave it" is wrong exactly half the time here.
- **`weight_in_kilograms` / `weight_in_grams` — `%1$s kg`, `%1$s g`.** SI symbols look invariant and are
  not: Ukrainian writes **кг** and **г**. Translate the symbol into your script.
- **`age_approximate` — `~%1$s`.** A tilde is not universal shorthand for "about". Use whatever your
  language actually writes (`ok. %1$s`, `ca. %1$s`, `~%1$s`).
- **Licence names.** *Apache-2.0*, *BSD-3-Clause* and the licence texts are generated by the build and are
  not resources — a translated Apache-2.0 is not Apache-2.0. But the seven `licences_*` strings **around**
  them (screen title, intro, buttons) are ordinary copy and do translate.
- **Breeds — `built_in_breeds`.** Translate the *descriptive* names, keep the *registered* ones. Polish is
  the worked example: `Angora (English)` → *Angora angielska*, `Belgian Hare` → *Zając belgijski*,
  `Californian` → *Kalifornijski*, but `Beveren`, `Blanc de Hotot`, `Britannia Petite` and `American Sable`
  stay exactly as they are. Where your language has an established breed name, use it; where the breed is
  known only by its original name, keep the original. **The list must stay the same length and the same
  order** — a test asserts it, and `Mixed / unknown` stays first.
- **Sample data.** `Bijou`, `Clover`, `Nugget`, `Metacam` and the other seeded names are not resources.
  They are identical in every locale by design and the screenshot harness depends on it.

---

## 5. Vocabulary

These terms are deliberate. Pick **one** word per term in your language and use it everywhere — the app's
credibility rests on the same thing being called the same thing on every screen. The *Avoid* column is what
English rejected and roughly what to reject in yours.

| Term | Means | Avoid |
| --- | --- | --- |
| **Bunny** | One animal the owner tracks. The app's central noun. | rabbit, pet, animal |
| **Observation** | Something the owner noticed at a specific moment. Recorded when noticed, never on a schedule. | health log, daily log, diary entry, check-in |
| **Archive** | Hide a bunny from everyday use while keeping every record. The opposite of deleting. | delete, remove, hide, deactivate |
| **Visit** | One appointment with a vet, recorded afterwards. A health record, **never** an expense. | appointment, consultation, checkup, vet bill |
| **Vet** | A directory entry for a vet or practice. | doctor, clinic *(the clinic is a field on a vet)*, practice, provider |
| **Document** | A scan of paperwork from a vet. Evidence the owner may need again. | file, attachment, scan, paper |
| **Photo** | A picture of a bunny kept for its own sake. Sentimental, not evidential. | image, picture, media |
| **Avatar** | The small picture identifying a bunny across the app. Not a gallery photo. | profile picture, thumbnail, icon |
| **Medication course** | A medicine to be taken from a start date, optionally on a daily schedule. | prescription, treatment, med |
| **Dose** | One administration of a course. | intake, administration, pill |
| **Care reminder** | A prompt for recurring husbandry — nail trim, vaccination, weigh-in. | task, todo, alert, notification |
| **Watch** | A time-boxed period of closer attention, declared by the owner, auto-expiring. | alert mode, monitoring, sick mode, observation period |
| **Droppings** | The ordinary hard round pellets. | poop, faeces, stool, *pellets* (ambiguous with food pellets) |
| **Cecotrope** | The soft nutrient-rich dropping a rabbit normally eats directly. Not the same thing as droppings. | night faeces, soft poop |
| **Symptom** | A named sign of illness the owner can tick. | condition, issue, ailment, problem |
| **Together** | Bunnies sharing a living space and litter tray. | bonded pair, group, herd, cage mates |
| **Fluffle** | The set of bunnies that live together. On screen this is only ever *"Lives with"* — translate the label, not the word. | group, warren, household, cage, hutch |

Two on-screen labels are worth naming separately because they are the ones owners read most:

- **"Lives with"** (`bunny_lives_with_label`) — the fluffle's on-screen name. Polish: *"Mieszka z"*.
- **"Care & Meds"** (`destination_care`) — a bottom-navigation tab, so it must be **short**. Polish fits it
  as *"Opieka i leki"*. If your language cannot fit both halves, shorten rather than wrap.

---

## 6. The *missed* / *overdue* rule, and the per-language banned list

ADR-0026: **the word "missed" belongs to the owner, not to the app.** A scheduled dose with nothing
recorded against it is *unanswered* — a fact about the record, not about the rabbit. There is no "missed"
status in the database, and there must be none in any language's strings.

**Exactly two strings may say a thing is late**, and both belong to Phase 4's *care reminders* — a nail trim
or a vaccination, never a medication:

- `care_due_overdue` — *"%1$s overdue"*
- `care_notification_overdue` — *"Overdue for %1$s."*

Everywhere else, and in **every** medication string, the concept must not appear. Polish renders the two
permitted ones as *"po terminie"* ("past the date") rather than reaching for *zaległa*, which is the shape
to copy: state the timing, not a judgement.

**Banned words, per language.** Each list is confirmed **with that language's native reviewer** before its
draft is read — the entries below are starting points, not decisions. If a banned word is genuinely the only
natural rendering of the two permitted strings above, say so and it gets discussed; do not quietly use it
anywhere else.

| Locale | Banned (draft — confirm with the reviewer) |
| --- | --- |
| `pl` | zaległa, zaległy, przegapiona, zapomniana — **not** *pominięta*, see below |
| `de` | verpasst, versäumt, überfällig, vergessen |
| `es` | perdida, olvidada, atrasada, vencida |
| `fr` | manquée, oubliée, en retard *(outside the two permitted strings)*, dépassée |
| `it` | mancata, saltata, dimenticata, scaduta |
| `pt-BR` | perdida, esquecida, atrasada, vencida |
| `cs` | zmeškaná, vynechaná, opomenutá, po splatnosti |
| `uk` | пропущена, забута, прострочена |

`dose_status_skipped` — *"Skipped"* — is **not** in this family and must stay available: skipping is
something the owner deliberately did and recorded. Keep it clearly distinct from anything meaning
"forgotten".

Polish is the worked example of how fine that line is. *Pominięta* was on this list as a first draft and
came off it: `pominąć` is **agentive** — *pominąłem dawkę* is "I skipped the dose", a thing the owner did
— where *przegapiona* and *zapomniana* carry the passive "it got away from me" sense the ADR forbids.
`dose_status_skipped` is *Pominięta* and is correct. **Check your own candidates the same way before
banning them**: the test is whether the word can only describe something that happened *to* the owner.

---

## 7. Traps

### 7.1 One English word, several target words

English collapses distinctions your language may not. Each row below is **one English string appearing
under several resource names on purpose** — they are separate resources precisely so they can diverge.
A flat glossary export will show them as duplicates. They are not duplicates.

| English | Resources | Why they diverge | Polish |
| --- | --- | --- | --- |
| **Normal** | `droppings_amount_normal`, `droppings_size_normal`, `appetite_normal`, `mood_normal`, `activity_normal`, `water_normal` | Six different nouns, six agreements. This is the single biggest trap in the file. | *Normalnie · Normalne · Normalny · Normalny · Normalny · Normalnie* — **five distinct forms** |
| **Unknown** | `sex_unknown`, `neutered_unknown` | One qualifies a feminine noun, one answers a yes/no question. | *Nieznana* · *Nie wiadomo* |
| **Name** | `bunny_name_label`, `vet_name_label` | An animal has a given name; a vet may be a person **or** a practice. | *Imię* · *Nazwa lub nazwisko* |
| **What is it?** | `care_editor_kind`, `med_editor_name` | Two different questions. One picks a *kind* of care task; the other asks the medicine's name (placeholder: Metacam). | *Czego dotyczy?* · *Co to jest?* |
| **Not drinking** | `water_none`, `symptom_not_drinking` | A graded water state, and a symptom chip. May coincide — check the register of each. | *Nie pije* · *Nie pije* |

The rest of the file's repeated values (*Settings*, *Vets*, *Close*, *Edit*, *Documents*, *Take a photo*,
*Photo of %1$s*, *Add a bunny*, …) are the same word in the same sense on two screens, and should translate
identically. If yours diverge, that is fine — just be deliberate about it.

### 7.2 A substituted noun phrase has to fit its sentence

Several strings substitute a **translated phrase** into a **translated sentence**. The app cannot inflect,
so the translation has to solve it. Polish demonstrates the two techniques:

**Pre-inflect the fragment for its host.** `weight_chart_window_30d` is English *"the last 30 days"* — a
standalone noun phrase. Polish makes it **`ostatnich 30 dniach`**, already locative, because both host
sentences put it after *w*. The host is reordered to suit: `weight_chart_single_in_range` becomes
*"**W %1$s** jest tylko jedno ważenie…"*.
This works only because every host uses the same preposition. **Check both hosts before choosing the
case** — `weight_chart_single_in_range` and `weight_chart_none_in_range` are the only two.

**Quote the substitution to dodge agreement entirely.** `backup_scope_essential/records/everything` are
substituted into `backup_restored_scope` and `backup_restore_confirm_body`. English writes *"Restored the
%1$s backup"*; Polish writes *"Przywrócono kopię **„%1$s”** z %2$s"* — the quotation marks turn an
inflected adjective into a quoted name, and the sentence stops caring about its gender. Use this whenever
a run-time value will not decline.

The full list of strings where a translated phrase lands inside a translated sentence:

| Sentence | Substituted from |
| --- | --- |
| `weight_chart_single_in_range`, `weight_chart_none_in_range` | `weight_chart_window_*` |
| `backup_restored_scope`, `backup_restore_confirm_body` | `backup_scope_*` |
| `care_every` | `care_unit_days/weeks/months/years` (plurals) |
| `care_due_in`, `care_due_overdue`, `trend_flag_long_gap` | `gap_days/weeks/months/years` (plurals) |
| `med_history_for_slot`, `med_record_for_slot` | a formatted clock time |
| `bunny_lives_with_value`, `observation_with`, `healthy_day_logged` | a joined list of names |

`care_every` has a note of its own: English *"Every %1$s"* takes *"6 weeks"* or the bare unit *"week"*, so
`Every 1 year` can never render. Polish *"Co %1$s"* governs the accusative, which for all four units is
spelled like the nominative the plurals already hold. **Check that coincidence in your language** — if the
accusative differs, the unit plurals need the accusative form, because that is their only host.

### 7.3 The app knows neither the owner's gender nor the bunny's

**This is the trap that costs the most strings, and English hides it completely.** Binky never asks the
owner's gender and has nowhere to store it. It does store the bunny's `sex` — but that field is optional,
defaults to unknown, and is not consulted when rendering copy.

So **every** form that agrees with either of them is unusable:

- **Second person.** Polish *zapisałeś* addresses half the audience; *zapisałaś* the other half. The file
  uses imperatives (*Dodaj*), the present tense (*nie może*) and impersonal forms (*zapisano*, *nie udało
  się*) throughout, none of which inflect. The same problem is French *vous êtes sûr(e)*, Czech
  *uložil/uložila*, Ukrainian *зберіг/зберегла*, Spanish and Italian past participles in compound tenses.
- **Predicate adjectives about the bunny.** *Zarchiwizowany* guesses masculine.
  `bunny_lives_with_alone` is *"Mieszka **samotnie**"* — an adverb — precisely because the adjective *sam*
  would have to pick a gender.

**A parenthesised suffix is not a solution.** *sam(a)*, *Prosiłeś(-aś)*, *sûr(e)*, *uložil(a)* — these read
as a form the app is apologising for. Both Polish instances were rewritten during Phase 8's read-through
for exactly this reason; do not reintroduce the pattern in your language. Reach for an impersonal
construction, a noun phrase, an imperative or the present tense instead.

Two worked Polish rewrites:

| Was | Now | Why |
| --- | --- | --- |
| *Prosiłeś(-aś) o przypomnienie.* | *Przypomnienie na Twoją prośbę.* | Noun phrase — nothing to inflect |
| *…i sam(a) decydujesz…* | *…i to Ty decydujesz…* | Present tense carries no gender |

### 7.4 Names the app cannot decline

Bunny names are typed by the owner. The app substitutes them raw and can never inflect them.

English `home_about_bunny` is *"About %1$s"*. Polish cannot write *"O %1$s"* — the preposition governs the
locative and *Bijou* has no locative the app can produce — so it **reorders to put the name first**:
*"%1$s — informacje"*. Do the same wherever a name would need a case your language cannot manufacture.
Affected: `home_about_bunny`, `archive_dialog_title`, `delete_dialog_title`, `watch_start_title`,
`watch_expired_title`, `trend_flag_ask_age`, `healthy_day_logged`, `photo_gallery_title`,
`documents_title`, `observation_excluded_archived`, `observation_excluded_watch`.

### 7.5 Plurals

Counts go through `<plurals>`, never through concatenation. **Your file must declare every category CLDR
gives your language** — a missing one is not an error, it silently falls back to `other` and renders a
grammatically wrong sentence.

| Locale | Categories |
| --- | --- |
| `de`, `es`, `fr`, `it`, `pt-BR` | one, many, other |
| `cs`, `pl`, `uk` | one, few, many, other |

Declare `many` for the romance languages even though it is unreachable for the integer counts this app
produces — it is a large-number form, and Android resolves against the declaration. **Verify your row
against CLDR when your language starts**; this table is written from the rules, not from the tool.

Check your plurals on a real **1, 2, 5 and 22** of something. That is where a wrong `few`/`many` split
shows.

`photo_import_partial` is a `<plurals>` whose two English items are **identical**. That is deliberate: the
quantity governs the *unreadable* count, which inflects in Polish (*1 pliku* / *2 plików*) where English has
nothing to vary. Do not collapse it to a `<string>`.

### 7.6 Format arguments

Every `%1$s`, `%2$d` and bare `%d` in the English string must appear in yours. A dropped argument does not
crash anything — the sentence just renders without the bunny's name, forever. A test catches it.

**You may reorder them**, and often should: that is what the positional `%1$s` notation is for. Polish
reorders `home_about_bunny` and `weight_chart_single_in_range` for exactly this reason.

**Keeping the argument is not the same as keeping its job**, and this is the one failure the test cannot
see. Polish `photo_gallery_empty_help` carried its `%1$s` faithfully and moved it from the thing the photos
are *of* to the gallery they land *in* — *"Zostaną na tym telefonie, w galerii: Bijou"*, which describes a
folder that does not exist. Every assertion passed. **Re-read your sentence with a real value substituted
in**, not with the placeholder still in it.

Escaping: apostrophes are `\'`, and `&` is `&amp;`. Newlines are `\n`.

---

## 8. What happens to your draft

1. It lands in **`translations/<locale>/strings.xml`** — *not* in `res/`. A `values-de/` directory in `res/`
   means every German phone gets those strings the moment it exists, reviewed or not; `locales_config.xml`
   is a *picker* list, not a delivery filter.
2. `TranslationTest` validates it in place: no orphans, format arguments, plural categories per CLDR.
   **Completeness is checked at the merge boundary instead**, by `scripts/translation-gate.py` — run
   `python3 scripts/translation-gate.py --report` any time to see what a language still owes.
3. **A native speaker reads every screen** in the running app, with no English left behind. This is the gate.
   A language ships on a person's word, because no test can hold tone.
4. Only then is it promoted into `res/`, with its `locales_config.xml` line, its `AppLanguage` entry and its
   endonym label — four edits, one commit, one language.

If a string cannot be translated well without breaking a rule in §2, **say so instead of translating it**.
The English is allowed to change; the rules are not.
