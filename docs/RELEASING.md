# Versioning & releasing

Versions are automated from git — you never hand-edit them. This mirrors what
`standard-version` does in the JS world, adapted to Android's two version fields.

## The two Android version fields (they are not the same thing)

| Field | Type | Who sets it | What it is |
| --- | --- | --- | --- |
| `versionCode` | `Int` | **git commit count**, computed in `app/build.gradle.kts` | The build's true identity. Must strictly increase or the installer/Play Store refuses it. Not semver. |
| `versionName` | `String` | **release-please**, from Conventional Commits | The `1.4.2` semver users see. The one you think of as "the version". |

Both are derived; neither is edited by hand.

## Writing commits (this is the part that requires discipline)

Every commit subject must be a [Conventional Commit](https://www.conventionalcommits.org):

```
<type>[optional scope][!]: <description>
```

- `feat: …`  → next release bumps the **minor** (1.0.0 → 1.1.0)
- `fix: …`   → next release bumps the **patch** (1.0.0 → 1.0.1)
- `feat!: …` or a `BREAKING CHANGE:` footer → bumps the **major** (1.0.0 → 2.0.0)
- `docs / style / refactor / perf / test / build / ci / chore` → no version bump, but `chore`/`docs` etc. still show in history

A committed hook (`.githooks/commit-msg`) rejects messages that don't match, so a
bad message can't sneak in. **Activate it once after cloning:**

```bash
git config core.hooksPath .githooks
```

(It's plain shell — no husky/Node. Bypass in an emergency with `git commit --no-verify`.)

## How a release happens

1. You push normal Conventional Commits to `main`.
2. The **Release Please** GitHub Action keeps a PR open titled like
   `chore(main): release 1.1.0`. That PR bumps `versionName` and updates
   `CHANGELOG.md` — the changelog is generated from your commit subjects.
3. When you want to cut the release, **merge that PR**. release-please then tags
   the repo (`v1.1.0`) and creates a GitHub Release. Merge whenever you like;
   nothing ships until you do.

Config lives in `release-please-config.json` + `.release-please-manifest.json`
(the manifest holds the current version — release-please rewrites it).

## Gotchas

- **`versionCode` in CI debug builds is `1`.** GitHub's checkout is shallow, so the
  commit count can't be read. That's fine for debug. For a *release* build, do a full
  checkout (`fetch-depth: 0`) or build locally so the count is real.
- **The release workflow needs a `RELEASE_PLEASE_TOKEN` secret** — a fine-grained PAT
  with *contents: write* and *pull-requests: write* on this repo. The built-in
  `GITHUB_TOKEN` can't be used: GitHub won't run workflows on a PR that `GITHUB_TOKEN`
  opened, so the release PR's CI sits at `action_required` with zero jobs, and the
  `main: require CI` ruleset (no bypass actors) then blocks the merge forever. If the
  secret is missing or expired, the workflow fails and no release PR appears at all.
- **We start at `0.1.0`** on purpose — the app is pre-1.0 until all phases ship.
  While the major is `0`, release-please stays in pre-release: `fix:` → `0.1.1`,
  `feat:` → `0.2.0`, and even a breaking `feat!:` bumps the *minor* (`0.2.0`), it
  does **not** auto-jump to `1.0.0`.
- **Cutting `1.0.0`** is a deliberate act when the phases are done: put a footer in
  a commit body —

  ```
  feat: finish phase N

  Release-As: 1.0.0
  ```

  — and the next release PR targets `1.0.0`.
