# The bunny profile carries sex and neuter status as health context, not decoration

A `Bunny` record is: `name` (required), an avatar, a nullable `fluffleId` (ADR-0008), `archivedAt`
(ADR-0004), and a small profile — `sex`, `neutered`, `birthdate`, `breed`, `colour/markings`.

Most of that profile is ordinary identification, but two fields are here specifically because this is a
**health** tracker, not a pet scrapbook:

- **`sex`** (enum: `male` / `female` / `unknown`) and **`neutered`** (enum: `yes` / `no` / `unknown`).
  An unspayed female rabbit has a very high lifetime risk of uterine adenocarcinoma — a fact a vet wants
  in front of them, and a reason an owner might act. Encoding sex and neuter status as first-class enums
  (stored by name, per the house rule) lets the app surface that context and lets a future version reason
  about it, rather than leaving it buried in a free-text note or absent entirely.

`birthdate` is nullable and carries an **"approximate" flag**, because rescues and secondhand rabbits
routinely arrive with a guessed age; a false-precision exact date would misrepresent what the owner
actually knows. It drives age display and age-relevant context. `breed` and `colour/markings` are
nullable identification helpers — useful when two bunnies look alike — and carry no health meaning.

## Why there is no target or ideal weight

Deliberately **omitted.** The weight trend flag (ADR-0001) is intentionally *relative* — a drop against
the bunny's own trailing baseline, noise-floored — so an absolute target contributes nothing to the one
safety signal that matters. Worse, an "ideal weight" invites exactly the absolute-thinness judgement
ADR-0001 exists to avoid ("your bunny is underweight"), which the app is not qualified to make. If a
display-only target is ever wanted, it must not feed the flag.

## Consequences

`sex` and `neutered` are `TypeConverter`-backed enums stored by name (house rule). `unknown` is a real,
common value for both, not a missing one — a rescue of unknown history is the normal case, and the schema
must not force a guess.
