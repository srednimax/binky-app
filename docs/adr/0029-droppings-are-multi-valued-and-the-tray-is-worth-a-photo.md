# Droppings are multi-valued, and the tray is worth a photo

Two reports arrived on 2026-08-14, and they are one shape. `droppingsForm` is a single nullable column, so
a tray holding round pellets **and** soft ones — the commonest early sign of a gut going wrong — forces the
owner to pick one and file the rest as prose. That is precisely what the field exists to prevent: its own
doc says *"a form that can be counted over time is worth more than prose."* **The model forces a lie on the
one field built to make the truth countable.** And an observation carries no media at all, while a photo of
the tray is the single most useful thing an owner can hand a vet about a problem that has already changed by
the time of the appointment.

**Decided**: appearance and size become **multi-valued**, amount stays single, the vocabulary gains six
values, and a tray gains **one photo**. This is the phase's schema bump — **schema 7**.

## What is multi-valued, and what is not

**Appearance and size are mixtures; amount is a quantity.** *Small and normal* and *round and soft* are both
things an owner can actually see in one tray. `FEW` **and** `MANY` is a contradiction about one tray, and
multiselecting it would not make the field awkward — it would make it meaningless. The split is by whether
the field describes a quantity or a mixture, and getting it wrong in that direction is the expensive
mistake.

## The multi-valued fields hang off the row, because there is no group

`groupId` is a bare nullable column, **not a table**, and ADR-0008 forbids stamping one on a solo
observation — *"it would make it read [as shared]"* (`ObservationRepository.kt:52`). So neither the join
tables nor the photo can belong to "the group": that is a model this app does not have.

**Tray-level here means denormalised onto every row and propagated on edit** — which is already how the four
droppings fields work (`updateTray`, group-wide or to the one row when solo). The multi-valued fields
therefore become join tables keyed on **`observationId`**, the photo becomes a **path column on the row**,
and both ride the `TrayFacts` propagation that already exists. ADR-0008 is untouched.

The cost of that propagation is worth stating, because it is not free the way the columns were:
`withTrayFacts` is a `copy()` of one row and can no longer carry the whole tray fact by itself. `add`,
`addParticipant` and `updateTray` each write the join rows for every participant inside the transaction they
already open, and the sets are **replaced, not merged**, on the same grounds as the symptom links — the
picker's state is the whole answer, so a value the owner unticked has to disappear.

## Two typed tables, not one generic one

`observation_droppings_appearance` and `observation_droppings_sizes`, each `PRIMARY KEY(observationId,
value)` with `ON DELETE CASCADE`, mirroring `observation_symptoms` — the composite key makes a double-tick
impossible and gives the foreign key its index for free.

The generic alternative — one `observation_droppings(observationId, kind, value)` — saves one `CREATE TABLE`
and buys a database that can hold `kind = SIZE, value = ROUND`. **A representable nonsense state is worse
than a duplicated table definition**, and the Kotlin side loses with it: two typed tables map to two enums
and stay exhaustive under `when`, where a discriminator column means filtering and casting at every read.

## The cheap answer was a joined string, and it loses to the field's own contract

A `Set<DroppingsAppearance>` `TypeConverter` writing `"ROUND,SOFT"` into the existing `TEXT` columns would
have needed **no migration at all**: the column type is unchanged, so the schema stays at 6, and the fixture,
the rebuild and the `connectedAndroidTest` run — the largest cost in Phase 7.5 — all disappear. It was
considered seriously for exactly that reason.

It loses on **countability**, which is the only thing the field was ever justified by. `WHERE value = 'SOFT'`
becomes `LIKE '%SOFT%'`, which is a substring match over a closed vocabulary and a trap the first time two
values share a prefix; `GROUP BY` returns `"ROUND,SOFT"` as its own bucket, so *how often were there soft
pellets this month* is not a query any more. The house rule — *enums with `TypeConverter`s, not loose
strings* — is about exactly this, and a comma-joined list is a loose string wearing a converter.

**Two honest caveats.** Nothing in the app queries these columns today: the DAO writes them and reads them
back as row fields (`ObservationDao.kt:107`, `:143`), so countability is a stated purpose rather than an
exercised one, and this decision is a bet on the field's own contract. And the bet is cheap to lose but
expensive to defer — ADR-0023 stopped the database being disposable at 1.0, so the same migration is owed
later with more rows in it.

## The rename is free, and this is the only free moment

`DroppingsForm` becomes **`DroppingsAppearance`**. Its own doc defines it as shape, and `CONTEXT.md` puts
*amount, size, form* in the ubiquitous language — but of the values it is about to gain, `BLOOD` and `MUCUS`
are contents, `VERY_DARK` is colour and `DRY` is moisture. Only `DOUBLED` is a shape. `DroppingsForm.BLOOD`
would read as a claim that blood is a shape, at every call site, forever.

**Free twice over**: only value names are ever stored, never the type name, and the `droppingsForm` column
disappears in the rebuild anyway, so nothing in SQL carries the old name forward either. What it does cost is
five resource keys renamed (`droppings_form_*` → `droppings_appearance_*`) in both locales, and one section
label reworded from *Shape* to *Appearance*.

Splitting into two fields — `DroppingsForm` for shape, `DroppingsContents` for blood and mucus — was
rejected: a clean split needs three or four fields, not two (`DRY` is moisture, `VERY_DARK` is colour), it
costs a third join table and a second chip group, and it makes the owner classify their own observation
before recording it. One glance at a tray is one description.

## The vocabulary gaps, and the one that is a trap

Checked against veterinary guidance and against the app's *other* vocabulary. What is covered is covered
well: `NONE` amount is the stasis emergency, and `SOFT` and `DIARRHOEA` are separate values, which avoids the
classic owner error of filing uneaten caecotrophs as diarrhoea. `symptom_dirty_bottom` already covers the
smeared presentation.

**`STRUNG_TOGETHER` silently absorbs a different sign, and that is worse than a gap.** The value means strung
*with fur* — moulting. Mucus presents identically: thick pale goop strung between the pellets, often
enclosing them. An owner seeing gut irritation would reasonably record moulting.

**Adding `MUCUS` to the enum closes nothing on its own**, which is the part worth writing down: with two
plausible chips on screen the owner still guesses. So the existing label is reworded to name the fur — *"Strung
together with fur"* — and the new one reads *"Mucus or slime"*. The enum name and the resource key are
unchanged; only the English and Polish values move. **Rows already recorded migrate unchanged and go on
meaning whatever their owner meant**: this fixes the next observation, never the last one.

**Blood is absent, and the absence is asymmetric.** The seeded symptom list carries `symptom_blood_in_urine`,
and red rabbit urine is usually harmless porphyrins. There is nowhere to record blood in droppings, which is
the one that is always serious. **The app has a field for the false alarm and none for the real one.**

**Blood cannot live in the symptoms table**, which is the first question a reviewer will ask, because
symptoms are the obvious home — owner-extensible, zero schema (ADR-0010). They fail for one reason:
**symptoms are individual and droppings are tray-level.** Blood in a shared tray cannot be attributed to a
bunny, so recording it as a symptom would force precisely the lie ADR-0008 exists to prevent.

So: `DroppingsAppearance` gains **`MUCUS`, `BLOOD`, `VERY_DARK`** (melena — *very dark, tarry*), **`DOUBLED`**
(fused pellets are specifically a slowing-motility sign, not general misshapenness) and **`DRY`**
(dehydration, today only inferrable from `SMALL`). `Cecotropes` gains **`EXCESS`**, which its own doc already
anticipates.

**Pale and greenish stay out on triage, not on cost.** Values on a multiselect take no height when unused, so
form length cannot tell the values kept from the values dropped. The line is whether a sign changes what an
owner does: blood, mucus and melena do; pale and greenish are the two weakest and most ambiguous signals in
the set, and a vocabulary that records everything nameable trains the owner to record nothing carefully.

**No per-value urgency copy.** Several of these values are alarming by nature and the app records them
without comment — no *see a vet now* attached to `BLOOD`. That is advice, and ADR-0026 forbids it. The
register is the trend flag's: state the fact, and let the existing *not a diagnosis; if you are worried, ask
a vet* do the rest.

**No forbidden combinations either.** `ROUND` with `DIARRHOEA` is not a contradiction — it is a tray with
both in it, which is the whole reason this ADR exists.

## Absence keeps one spelling

With a join table, "not checked" becomes **zero rows** — the exact shape that forced `symptomsChecked` into
existence, because *looked, none seen* and *never opened the picker* were otherwise indistinguishable
(ADR-0010).

**No `droppingsChecked` flag is owed, and the difference is worth stating.** There is no affirmative "I
looked and the pellets had no appearance": if the owner looked, at least one value is true. The one real edge
— an empty tray, nothing to describe — is already carried by `amount = NONE`, which is the alarming fact
anyway. And the ambiguity is **unchanged** by this decision: a nullable column and an empty set say exactly
as much. Symptoms are the exception in this database, not the precedent.

The app still never reads an empty set as *normal* (ADR-0001).

## The migration rewrites a table holding owner data, and can eat the symptom ticks

This is the risk in the phase, and it was found while writing this ADR rather than while running it.

Dropping `droppingsForm` and `droppingsSize` off `observations` needs a **create-copy-drop-rename rebuild**:
SQLite's `ALTER TABLE … DROP COLUMN` arrived in 3.35 and `minSdk` is 26, and Room validates the migrated
database against `schemas/7.json`, so a vestigial column is not an option either. `MIGRATION_5_6` added a
column to `weights`; **this is the first migration in the project that rewrites a table full of an owner's
history.**

**`DROP TABLE observations` performs an implicit delete of every row, which fires `observation_symptoms`'
`ON DELETE CASCADE`.** Foreign keys are enabled on the connection, and Room emits `PRAGMA defer_foreign_keys
= TRUE` in exactly one place — `RoomDatabase.performClear`, i.e. `clearAllTables` — never around a migration.
`PRAGMA foreign_keys = OFF` is a no-op inside the transaction Room has already begun, so it is not available
either.

**The recipe therefore stages the links and puts them back**, and is safe whether or not foreign keys are
enforced:

1. `CREATE TABLE symptoms_backup AS SELECT * FROM observation_symptoms` — a constraint-free copy.
2. Create `observations_new` from `schemas/7.json`: no `droppingsForm`, no `droppingsSize`, plus
   `trayPhotoPath`.
3. Copy the rows, drop `observations`, rename, recreate both indices.
4. Create the two join tables and fill them from the staged old values — **one row per non-null value**.
5. Restore the links from `symptoms_backup`, then drop it.

**The test has to assert rows, not shape.** `runMigrationsAndValidate` compares the schema and passes
happily on a database whose every symptom tick has been cascaded away, so the instrumented test reads the
rows back: an existing single droppings value survives as **one row in the join table**, and the symptom
links are still there afterwards. A migration that silently dropped either would erase exactly the history
ADR-0023 stopped the database being disposable for.

Schema 7 is **not frozen until the phase closes** — ADR-0007's pending-migration rule means further changes
inside Phase 7.5 are folded into `MIGRATION_6_7` rather than added as an eighth version.

## The photo is record-grade, and its directory is permanent while its pixels are not

The tray photo goes through `MediaFiles` per the house rule, under a **new `MediaKind.Observation`** writing
to `observations/`.

**The directory is the permanent half.** Relative paths are stored on rows and `MediaFiles` keeps no
original, so a kind chosen now cannot be changed later without rewriting rows and moving files. The
**numbers are not permanent**: a spec change affects only writes made after it, which is why the downsample
figures are judged on the phone beside the document spec (Phase 7.5 §2) rather than settled here. Pellet
shape is closer to *Document*'s "small detail matters" than to *Photo*'s gallery cap, and that is the
starting hypothesis the judgement tests.

**A new kind is in no backup at all until it is put in one**, and that is the failure this section exists to
prevent. `ArchiveEntries.kt:111` maps a restored file back to its kind by **directory name** across
`MediaKind.entries`, so restore works for free — but `BackupScope` and `AutoBackup` enumerate kinds by hand.
So the kind is added to **`Records` and `Everything`**, and joins the newest-first cloud admission queue
beside documents: a photo of a symptomatic tray is evidence for a vet, not a gallery snapshot, and the
gallery's flat cloud exclusion would mean a lost phone loses it.

Reusing an existing kind was cheaper and both readings were wrong. `Document` would inherit every scope for
free, at the price of an export sheet that counts litter trays as documents and a tray photo that can evict
a discharge sheet from the cloud budget. `Photo` would put health evidence in the `Everything`-only tier
that Google's Auto Backup deliberately drops.

**One new rule comes with the duplicated path.** The photo path is written to every row in the group, so
deleting one bonded bunny cascades a row that still references the survivor's file, and `MediaFiles.delete`
is a plain `File.delete()` (`MediaFiles.kt:176`). **The file goes only when no other row references the
path** — one `COUNT(*)` on the observation-delete path, and a gate item that says so.

**The photo remains the phase's release valve, and it is cheap in both directions.** The column rides a
rebuild that is happening anyway, so it costs one line; and because `MIGRATION_6_7` is unshipped until 1.5
is released, cutting it later is one line back out. If it were ever wanted after the release, it arrives as a
plain `ALTER TABLE … ADD COLUMN` — the cheapest migration there is, and the one `MIGRATION_5_6` already
demonstrates on `weights`. **Cutting it does not cut the migration**: the join tables are schema 7 on their
own.

## Considered and rejected

**A real `observation_groups` table**, which is the model you would draw from scratch and would give both the
join tables and the photo an honest parent. Rejected on cost and on ADR-0008: four columns move, every solo
observation needs a group row — which ADR-0008 reads as *shared* — and every timeline query is rewritten. A
phase's work inside a section of one.

**A `Set<Enum>` `TypeConverter`**, the no-migration option, rejected above on countability.

**One generic table with a `kind` discriminator**, rejected above on representable nonsense.

**Two fields, form and contents**, rejected above: the clean split needs three or four.

**A `droppingsChecked` flag**, rejected above: the empty tray is already `amount = NONE`.

**`PRAGMA legacy_alter_table` with a rename**, the shorter migration recipe. Rejected because it depends on
`ALTER TABLE … RENAME` semantics that changed in SQLite 3.25 and the app spans API 26 to 36, and because
what it buys is three fewer statements in the one migration in this project that must not be clever.

**Per-value urgency copy**, rejected on ADR-0026.

## Consequences

**Schema 7**, one hand-written `MIGRATION_6_7`, a schema-7 fixture, and the `connectedAndroidTest` run that
no other item in Phase 7.5 owes. A 1.5 backup will not restore on 1.4 — `BackupRestorer` refuses an archive
whose schema is newer than the build's, read from the file's own header (`BackupRestorer.kt:193-200`). That
is correct and already tested; 1.5 is simply the first release where a tester can meet it.

**Copy footprint**: six new value strings (`MUCUS`, `BLOOD`, `VERY_DARK`, `DOUBLED`, `DRY`, `EXCESS`), two
reworded (*Strung together with fur*, and the section label *Appearance*), five resource keys renamed, plus
the photo's own handful — all in both locales, and all before nine languages rather than after, which is the
tax Phase 7.5 exists to pre-pay.

**`healthyDayFacts()` keeps claiming exactly what it claimed** — one value in each set, `setOf(ROUND)` and
`setOf(NORMAL)`. Making the shortcut assert more because the field now holds more would be the app inventing
observations on the owner's behalf, which is the opposite of what ADR-0001 grants it.

**The default seed is not changed.** A tray with two appearance values, and a tray with a photo, are states
the seed does not contain — and 61 matrix scenes, the before/after comparison and the Play listing
screenshots all rest on it. They are reached through the **seed variants** built in Phase 7.5 §4, which is
the third customer for that mechanism after the gain card and the five-bunny fluffle.

**The values are stored by name, never ordinal**, so this addition cannot rewrite history — which is what
made a sixth, seventh and eighth value safe to add at all.

---

## Amendment (Phase 10, 10d): the tray photo is a set, and the release valve is spent

**2026-08-23, from an owner.** *Several photos per litter tray, not one.* Accepted, and it is the shape this
ADR already argued for twice — the section above calls the single photo "the phase's release valve", cheap
to cut and cheap to re-add, and what it did not anticipate is that the *cardinality* would be the thing that
turned out wrong rather than the field itself.

**One frame does not cover a tray, and the reason is the same one that made droppings multi-valued.** A tray
holds round pellets *and* soft ones, which is why `DroppingsAppearance` is a set; it also holds them in
places one photograph does not reach, and an owner photographing a tray they are worried about is gathering
evidence rather than keeping a memento. The single column asked them to pick the most representative frame,
which is a judgement they were making *because the app could not hold both*.

### It takes the shape the two droppings fields already have

`observations.trayPhotoPath` becomes **`observation_photos`** — a join table keyed on `observationId`,
written for every participant inside the transaction that writes the rows, and **replaced, not merged** on
an edit. Keyed on the observation and not on a group, for the reason this ADR gives at length: there is no
group *table*, so a tray-level set is denormalised onto every participant's row exactly as the tray columns
are.

It carries one column the droppings tables do not: **`position`**, the owner's order. That is a real
difference rather than an inconsistency — a tray either has soft droppings in it or does not, and set
membership is the whole fact, where a strip of photographs the owner arranged has an order that the
composite primary key cannot carry. There is deliberately **no `createdAt`**: nothing would read it,
`observation_symptoms` and both droppings tables carry no timestamp either, and the row it hangs off already
records when the observation was made.

`path` gets its own index, for the same reason `observation_symptoms.symptomId` has one — the composite key
indexes `observationId` first, so the refcount below, a lookup by path alone, could not use it.

### The one new rule is unchanged, and now guards a set

The section above introduced it for a single duplicated path: **a file goes only when no other row
references it.** That survives the change with its wording intact and its query moved —
`SELECT COUNT(*) FROM observation_photos WHERE path = ?` instead of the same count over `observations`.
Every path that leaves an edit is now diffed against what was there, and each orphan is checked
individually, because "the photo changed" has become "these two went and these three stayed".

### Six per tray, and the number is arithmetic

`MediaKind.Observation` writes at a 2048px long edge and quality 88 — around half a megabyte a frame — and
these files sit in Auto Backup's newest-first admission queue against a 20 MB budget shared with document
pages, after the core has taken its share. **Six is about 3 MB for one thorough tray**: enough that a whole
observation is never split across the admission boundary, small enough that a handful of trays still fit
beside the documents.

Nothing silently drops past the cap. The form stops offering *add*, and the exclusion notice this ADR
already built is what tells an owner which record images did not reach the cloud — the honest answer the app
already gives, rather than a new one invented for photos.

### What it costs, and what it does not

**Schema 8**, folded into one `MIGRATION_7_8` with an unrelated `events` table (ADR-0031), because
`observations` has to be rebuilt anyway to lose the column and two migrations would mean testing the
expensive one twice.

⚠️ **The rebuild has three cascade-carrying children now, not one.** `MIGRATION_6_7`'s recipe staged
`observation_symptoms`; since 1.5 there are also `observation_droppings_appearance` and
`observation_droppings_sizes` — created *by this ADR* — and every one of them is emptied by the implicit
delete that `DROP TABLE observations` performs. `runMigrationsAndValidate` cannot see the difference: a
database whose every droppings value has been cascaded away has exactly the right schema. `Migration7To8Test`
therefore counts rows for all three, with values spread across three observations so a partial restore
cannot pass.

**An existing photo migrates as one row at `position = 0`**, and nothing about it re-encodes or moves on
disk — the same string, pointing at the same file under `observations/`.

**The timeline shows the first photo with a `+N` badge** rather than the strip. An entry there is a summary,
and six thumbnails in a feed would make the tray louder than the bunny the entry is about; the set is one
tap away in the editor.

**`healthyDayFacts()` keeps claiming exactly what it claimed** — no photos, on the same grounds as before.
One tap is a claim about what was seen, never a photograph of it.
