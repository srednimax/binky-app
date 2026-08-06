# Phase 7 design brief — the visual language, and two screens

Input for the mockup work described in [`phase-7.md`](phase-7.md). Read that file for why the phase
exists; this one is what to draw and what may not change.

**This is a mockup surface, not a codegen path.** Nothing produced here ships as-is: the app is Kotlin and
Jetpack Compose, and everything below is translated by hand into `Modifier` chains and
`MaterialTheme.colorScheme` roles. So the output is judged on whether it *translates*, not on whether it
runs. The last section says what shape makes that cheap.

## The app in a paragraph

Binky tracks the health of pet rabbits: weight over time, observations of droppings and wellbeing, vet
visits, medications, scanned documents, photos, and reminders for recurring care. Free, ad-free, no server,
everything on the device. It is used by one person, often one-handed, often while holding a rabbit, and
sometimes at a vet's counter. It is not a social app and has no feed, no streaks and no gamification.

The reader is an owner who is *worried about an animal*. That is the emotional register: calm, factual,
never alarming, never cute about a health signal.

## What is being replaced

The current look is the `android create` scaffold, unchanged. `Color.kt` is still `Purple40`/`Purple80`,
three roles deep. Until this phase, `dynamicColor = true` meant the palette came from the user's wallpaper
on Android 12+, so the purple was rarely even visible — [ADR-0027](adr/0027-binky-owns-its-palette-material-you-is-opt-in.md)
turns that off and is why a brand is now owed.

**There is no existing identity to preserve.** Nothing in the before screenshots is a deliberate choice
except the layout and the words.

## Non-negotiables

These are load-bearing somewhere, and most of them are one ADR away from a real user harm. A prettier
screen that breaks one of them is a regression, not a redesign.

**Weight changes are always shown in grams**, whatever the display unit. `−0.04 kg` hides the signal that
`−40 g` makes obvious. The absolute weight is shown in the user's chosen unit (kg by default) and the
*change* is always grams — you can see both on the Weight screen's list rows, and that mix is deliberate.

**The weight chart plots real timestamps, not list index.** Weighings are irregular. An evenly spaced chart
is a better-looking lie about the trend.

**Nothing infers a health problem from missing data.** Silence means nobody looked. This breaks most often
in **empty states**, which is exactly what this phase rewrites — "no observations yet" must never become
"nothing to worry about", and an empty screen may not be reassuring, because it has no grounds to be.

**No *missed* or *overdue*** anywhere except care reminders. A dose with nothing recorded against it is
**unanswered** — a fact about the record, not about the rabbit. No red, no exclamation, no badge that codes
a gap as a failure. The owner gets to decide what a gap means.

**Health features observe; they never advise.** No diagnosis, no interpretation, no "this looks concerning".
The app reports what was written down.

**Every user-visible string is a resource, in English and Polish.** New copy is fine and expected — but
every new string is two strings, and Polish runs materially longer than English. A layout that only fits
English is a bug the test suite will catch and the design should not create.

**Missing media renders as a placeholder, never a crash.** A restored backup may legitimately lack photos.

## What to produce

### 1. Brand seed colours — *not* a full scheme

Two to four seed colours, with a sentence each on what they are for. **Do not hand-pick the ~36 individual
Material 3 roles** (`primaryContainer`, `onSurfaceVariant`, `outlineVariant`, `surfaceContainerHigh`…):
they stand in fixed tonal relationships and must hold contrast in both light and dark, and a hand-picked
set fails that quietly on one screen out of thirty. The full scheme is generated from the seeds.

What is wanted is the *judgement* — what should Binky feel like — expressed as seeds plus a short rationale.

Constraints on the seeds: it must survive being **desaturated to a chart line on a white and a near-black
background**, and it must not read as clinical. It is a rabbit health app, not a hospital.

### 2. Type scale, mapped to Material 3's named styles

Give sizes, weights and line heights against these names, because they are what Compose's `Typography`
takes and the mapping is then transcription rather than interpretation:

`displayLarge/Medium/Small`, `headlineLarge/Medium/Small`, `titleLarge/Medium/Small`,
`bodyLarge/Medium/Small`, `labelLarge/Medium/Small`.

Not every one has to change. Say which do, and leave the rest at M3 defaults.

### 3. Spacing rhythm

On a **4dp grid**, expressed as a small set of named steps rather than per-screen values. Include the
vertical rhythm between a section header, its content, and the next section — inconsistency there is the
most visible defect in the before set.

### 4. List row and card treatment

The app is mostly lists and cards. Decide once: row height, internal padding, where a secondary value sits,
how a divider or elevation separates rows, and what a *tappable* row looks like versus a static one.

### 5. Empty states

The shape of one: what it says, how much space it takes, whether it offers the action that would fill it.
See the non-negotiables — this is where ADR-0001 gets broken.

### 6. The shape of a primary action

The app uses a filled button, a FAB, and text buttons, currently without a rule for which goes where.

### 7. `Home` and `Weight`, drawn in full — light **and** dark

Dark is not a variant of light and does not review as one. Contrast, elevation and the surface roles land
differently, which is why the before set was captured in both.

## What not to produce

- **Compose code.** It cannot be used; it is translated by hand.
- **The other 24 routes.** They go straight to Compose against the language these two fix. Mocking a screen
  in HTML that will be hand-written in Kotlin anyway is work paid for twice.
- **New features, new screens, new navigation.** Same functionality, every route, every table. If a layout
  seems to need a new screen, the layout is wrong.
- **Illustration or mascot art.** Out of scope for this pass.

## The two hero screens, as they are today

### `Home` (a bunny selected)

Top bar with a bunny switcher (avatar, name, dropdown). Then: large avatar, name, "Lives with Nugget". A
**trend flag card** — "Worth a closer look", the change in grams, the two weights, two lines of disclaimer,
and the actions *I have seen this* / *Start a watch*. A **watch row** — "Watch active · 3 days left" with
*Close it*. Then a facts list: Last weighing, Last observation, Sex, Neutered. Then *Edit / Archive /
Delete*. A `+` FAB for the global observation entry. Five-tab bottom bar.

Under "All bunnies" instead of one rabbit, it becomes "Tap a bunny to open it." and a card per rabbit
showing last weighing and last observation.

### `Weight`

Same top bar. The **same trend flag card**. Then a range selector (30 days / 90 days / 1 year / All), the
**chart**, a *Record a weighing* button, and the list of weighings — absolute weight on the left, change in
grams on the right, timestamp beneath.

## Two observations from the before set worth responding to

Not instructions — the design may disagree, but it should disagree deliberately.

**The trend card eats the Weight screen.** It occupies more than half the viewport, so the chart — the
entire reason the screen exists — starts below the fold. The same card appears on `Home`, so an owner reads
it twice. It carries five distinct things: a headline, a number, a comparison, two disclaimers and two
actions. The disclaimers are required by ADR-0001 and cannot simply be deleted, but nothing says they must
be equally loud on both screens, or at full length before the owner has asked.

**Two different buttons say "Open".** On the Care screen a permission banner and a medication-course row are
both labelled *Open*, going to system Settings and to a course respectively. This broke the screenshot
automation by matching the wrong one — weak evidence, but evidence, that a person scanning quickly could
make the same mistake.

## Output shape that translates cheaply

Colour as **seeds plus role intent** ("cards use `surfaceContainer`, the flag card uses `errorContainer`"),
never as raw hex sprinkled through CSS. Naming a role means the translation is a lookup; naming a hex means
someone has to reverse-engineer which role was meant, and get it wrong in dark mode.

Type as the **M3 style names** above. Spacing as **dp on the 4dp grid**. Anything stated as "16px" has to be
converted and re-checked; stated as `16.dp` it is already the answer.
