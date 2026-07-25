# Version 1.0 ships at the end of Phase 3

The roadmap previously held that "there is no intermediate public release: the app ships to end users only
once every phase is complete." That rule is replaced by a narrower one: **no release before the data is
safe.** Phase 3 satisfies it, so Phase 3 is where 1.0 ships.

The old rule looked conservative and was not. Every genuinely hard or externally-dependent piece of work
sits in the back half — the custom `BackupAgent` whose failure mode is silence (ADR-0005), WorkManager
surviving Xiaomi HyperOS, exact alarms behind an **overnight Doze gate that ADR-0003 itself concedes may
not be passable**, the ML Kit scanner's Play-services dependency (ADR-0009), and Polish plurals
(ADR-0013). Holding the entire product behind all of that means the app's one load-bearing safety signal —
the weight trend flag — reaches nobody until a document scanner works.

Phases 1-3 already contain the whole thesis: bunnies, weight, the trend flag, observations, and a backup
that makes the data safe. That is a coherent and honest app. Everything after it is additive.

The plan had in fact already drawn this line and not noticed. ADR-0007 attaches the migration obligation at
Phase 3, because that is when the app "keeps real bunny history once backup exists". The moment data becomes
real is the moment the app becomes releasable; those are the same moment for the same reason.

## Consequences

**Phase 4 becomes 1.1** (care reminders and watch) and **Phase 5 becomes 1.2** (vet, medications,
documents, dose reminders). Phase 6's release work moves forward to the end of Phase 3 and is repeated per
release rather than performed once.

**Shipped stubs are no longer free.** ADR-0012 #5 and ADR-0015 fix the top-level destinations up front and
build them as stubs — safe while nothing shipped, and not safe now: a public 1.0 would carry a
bottom-navigation tab opening onto "Phase 4". Deciding the structure and rendering the destination are
different claims, and only the first is what ADR-0012 #5 requires. Each top-level destination therefore
carries a **visibility state — `Hidden` / `ComingSoon` / `Live`** — while every nav key and route exists in
code from Phase 1 as before. A dead **tab** is hidden, because it is a fifth of primary navigation; a dead
**row** inside More or Settings may show as "coming soon", because it costs a line in a list. Promoting a
destination in 1.1 is a one-value change, not a navigation restructure — which is precisely the cost
ADR-0012 #5 exists to avoid.

**The migration obligation starts at 1.0**, while the medication and vet tables are still being designed.
ADR-0007 already covers this: schema churn for those features happens on a throwaway debug database, and a
single consolidated, tested migration from the last released version is written once the feature settles.
Nothing about that changes — it simply starts being load-bearing earlier than the roadmap assumed.

**The Doze reliability gate leaves the critical path.** Dose reminders are 1.2 work, so the hardest
reliability problem in the project no longer blocks any release and can be solved on its own terms rather
than under shipping pressure. ADR-0003's best-effort fallback stands.

**ADR-0011's donation prompt goes live earlier** than the old roadmap implied. No change is needed — it is
already gated on roughly 30 days *and* meaningful data, which is exactly the right gate for a 1.0 audience.
