# PR 2 — Data layer, media, wipe guard

**Branch:** `data-layer` · **Depends on:** PR 1 merged (the package is `app.bunny.tracker`) ·
**Decisions and reasoning:** [`README.md`](README.md)

No screens, so the instrumented tests are the review.

---

## Dependencies

`gradle/libs.versions.toml` + `app/build.gradle.kts`:

- **Room 2.8.4** — `room-runtime`, `room-ktx`, `ksp(room-compiler)`, `room-testing` (androidTest)
- **KSP 2.3.10** — plugin `com.google.devtools.ksp`. Verified working against Kotlin 2.3.20; see the
  README's probe results
- **Coil 3.5.0** — `coil-compose`. Used in PR 3, added here with the rest
- **DataStore Preferences** — for the onboarding flag

Photo Picker (`PickVisualMedia`) and `TakePicture` come from `activity-compose`, already present. No
`kotlinx-serialization-json` — `-core` is already on the classpath transitively.

Set `room.schemaLocation` to `$projectDir/schemas` via KSP args and **commit the JSON**. ADR-0007 lets us
wipe until Phase 3, but the exported schema is what makes Phase 3's first real migration reviewable.

## Entities

UUID string primary keys — they survive export and restore across installs, which autoincrement ints do
not, and that matters for ADR-0005.

```
BunnyEntity
  id: String            @PrimaryKey
  name: String
  avatarPath: String?           // relative, avatars/<uuid>.jpg
  birthDate: LocalDate?
  birthDateApproximate: Boolean = false
  sex: Sex?                     // enum, stored by NAME
  neutered: Boolean?            // null = unknown, not false
  microchip: String?
  breed: String?
  colour: String?
  note: String?
  fluffleId: String?    @ForeignKey(FluffleEntity, onDelete = SET_NULL), indexed
  archivedAt: Instant?          // ADR-0004: archive is not delete
  createdAt: Instant

FluffleEntity   id, name

PhotoEntity     id, bunnyId @ForeignKey(CASCADE) indexed,
                path (relative, photos/<uuid>.jpg),
                caption: String?, takenAt: Instant?, createdAt: Instant
```

`Sex` is an enum with a `TypeConverter` storing `name`, never `ordinal` — a house rule, so adding a value
later cannot rewrite history.

**Converters:** `Instant ↔ Long` (epoch millis), `LocalDate ↔ Long` (epoch day), `Sex ↔ String`.
`java.time` is fine at `minSdk` 26.

## DAOs, database, repositories

**DAOs** return `Flow` for reads and `suspend` for writes — screens collect, nothing hand-rolls a refresh.
`BunnyDao` needs: active (`archivedAt IS NULL`), archived, by id, by fluffle, and a **record-count query**
for ADR-0004's second confirmation. In Phase 1 that count is photos only; extend it as later entities land.

> **Kotlin note** (worth a line in the commit message): a `Flow` is a *cold* stream — nothing runs until
> something collects it, unlike a Promise, which is already in flight the moment you hold it.
> `stateIn(viewModelScope, …)` is what makes it hot and replayable for the UI.

**Database** `BunnyDatabase`, version 1, `exportSchema = true`, destructive fallback per ADR-0007.

**Repositories** `BunnyRepository`, `FluffleRepository`, `PhotoRepository` — thin over the DAOs, and the
owner of "delete the bunny's media files too", because Room's `CASCADE` deletes rows but not files.

**`AppPreferences`** wraps DataStore. Phase 1 stores one key, `onboardingCompleted: Boolean`, exposed as a
`Flow<Boolean>` like everything else. It lives here and not in the database on purpose — see the README.

**`AppContainer`** — manual DI, not Hilt. Holds the database, the repositories, `AppPreferences` and
`MediaFiles`. Constructed in a new `BunnyTrackerApplication : Application`, registered via `android:name`
in the manifest, which currently has none. ViewModels receive it through a `viewModelFactory`.

## `media/MediaFiles.kt`

The single path for persisting images. Named to avoid colliding with `android.provider.MediaStore`.

```kotlin
enum class MediaKind(val directory: String, val maxEdgePx: Int) {
  AVATAR("avatars", 512),      // Essential backup scope — must stay small
  PHOTO("photos", 2048),       // Everything scope
  DOCUMENT("documents", 2048), // Records scope — Phase 5
}
```

- `suspend fun persist(source: Uri, kind: MediaKind): String` — decodes with `inSampleSize` downsampling
  to the kind's max edge, re-encodes JPEG ~85, writes `<directory>/<uuid>.jpg`, **returns the relative
  path**.
- `fun resolve(relativePath: String): File` — `File(filesDir, relativePath)`, used at read time.
- `suspend fun delete(relativePath: String)`.
- All of it off the main thread (`withContext(Dispatchers.IO)`).

**Nothing else in the app may write an image** — bypassing this puts full-resolution bitmaps in memory and
blows up the photo grid. "Set as avatar" from the gallery calls `persist(uri, AVATAR)`, producing a
**copy** at avatar size, so deleting the gallery photo can never break the avatar.

## Wipe guard (ADR-0007)

SQLite stores `user_version` at **byte offset 60** of the file header, so this is a 4-byte read before
Room is ever involved:

```
AppContainer.init:
  onDisk = readUserVersion(databaseFile)          // 0 if the file does not exist
  if (onDisk in 1..<APP_SCHEMA_VERSION && no migration covers it)
      copy databaseFile -> preserved/bunny-<ISO timestamp>.db
      expose state = WipeWarning(preservedPath)   // MainActivity blocks on it in PR 3
  else open Room
```

The preserved file is a recovery artifact, **not** a restore — ADR-0007 is explicit that reading old data
into a new schema *is* a migration, so it cannot be re-imported automatically. `preserved/` sits in
`filesDir` alongside the media directories.

## Tests

**JVM** (`app/src/test/`):

- `MediaFiles`' sample-size and target-dimension math
- `readUserVersion` against handcrafted header bytes, including a missing file and a truncated one
- the ADR-0004 decision of one confirmation dialog versus two

**Instrumented** (`app/src/androidTest/`), in-memory Room:

- bunny round-trip; `archivedAt` keeps archived bunnies out of the active `Flow` but retrievable
- deleting a bunny cascades its photos (ADR-0004)
- deleting a fluffle `SET_NULL`s its members' `fluffleId` and deletes no bunny
- `Sex` stored by name — assert the column reads `"FEMALE"`, not `1`

---

## Verify

```bash
./gradlew assembleDebug test lint
./gradlew connectedAndroidTest
```

`connectedAndroidTest` split-APK installs **prompt on the Xiaomi** — accept on the phone, or the run fails
with `INSTALL_FAILED_USER_RESTRICTED`.

Confirm `app/schemas/…/1.json` was generated and is committed.
