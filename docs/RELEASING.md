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

The same setting turns on `.githooks/pre-push`, which prints what the branch still
owes the translation gate before you push. It is **advisory and cannot fail a push**:
completeness is a merge boundary, not a work boundary, and a branch mid-translation is
ordinary (`docs/phase-8.md`). It exists only so you hear it in a second rather than
from CI four minutes later. Silent when there is nothing outstanding.

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

### Merge pull requests with **rebase**, never a merge commit

The repo allows rebase merging only, and that setting is load-bearing for the
changelog rather than a matter of taste.

GitHub writes the PR title into a merge commit's *body*. `conventional-commits-parser`
strips the `Merge pull request #N from …` header and parses what follows as the
commit — so a PR titled `feat: …` whose branch also carries that `feat: …` commit
is counted **twice**, and the release notes list the feature twice. That is exactly
what 1.1.0's first draft did, for all five of its features; the four `fix:` entries
escaped only because those PR titles happened to be `chore:` or plain prose, which
is luck, not a rule.

Rebase replays the branch commits onto `main` with no merge commit in between, so
every conventional subject is counted exactly once and each commit keeps its own
changelog line. Squash would also de-duplicate, but it folds a PR's individual
subjects into a single entry taken from the PR title — and this repo writes a
meaningful subject per commit (PR #72 alone contributed three separate `fix:`
lines), so squashing would throw away detail the changelog exists to carry.

There is no release-please option for this. The merge strategy *is* the fix, which
is why it is enforced by the repo setting instead of by remembering.

## Checking the artifact before it reaches Play

`bundleRelease`, never `assembleRelease` — Play wants an AAB and an AAB can't be
`adb install`ed, so the only build ever put on the phone is the one Play delivers.
Then read the version fields back **out of the artifact**, not out of the config
that was supposed to produce it:

```bash
./gradlew bundleRelease
python3 scripts/aab-version.py        # versionCode/versionName vs. git
python3 scripts/aab-permissions.py    # the <uses-permission> set, vs. an allowlist
python3 scripts/aab-locale.py         # every string of every shipped locale, vs. the resource table
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

All three scripts **exit non-zero** rather than printing and leaving you to read.
Each exists because the corresponding claim was once wrong in a shipped artifact
while every source-side check was green: `versionCode` 1 on a signed bundle (3a),
Polish missing from the build that went up (fixed in 1.0.1), and a permission set
that had quietly grown from two to six (found at 4h). The pattern is the same
every time — the config said one thing, the artifact said another, and nothing
compared them.

Don't reach for `aapt2 dump xmltree` here. An AAB stores its manifest as
**protobuf**, not the binary XML aapt2 reads, so it prints nothing and exits `0` —
it doesn't fail, it just declines to answer. That silence is how PLAN.md 3a
produced a *signed* bundle carrying `versionCode` 1 and didn't find out until
later. `scripts/aab-version.py` decodes the protobuf and asserts the count matches
`git rev-list --count HEAD`.

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
