# Rename `Trie*Demo` → `RadixTrie*Demo` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the six radix-trie demo classes and their five tests so that `Trie` stops meaning
"the radix one" by convention, clearing the way for `StandardTrie*Demo` in PR 2.

**Architecture:** Pure rename. `git mv` for the eleven files, then one idempotent `perl` pass over
`src/` and `README.md` rewriting all **38 identifier occurrences across 14 files** — a count verified
against the working tree, with **no file under `docs/` or `planning/` matching**. Task 1 itself adds
no files and no tests: the existing suite is its guard, staying at **exactly 572/572**, with all six
`DocsPagesGoldenTest` cases green **without regeneration**. Task 2's fix round then
deliberately added one guard test (`GuideReadmeAnchorTest`, to repair a README anchor the relabel
orphaned), taking the branch total to **573/573** — see Task 2 and the Definition of Done.

**Tech Stack:** Java 21, Maven, JUnit 5 (Surefire 3.2.5), zsh on macOS.

## Global Constraints

- **Java 21, Maven.** Build and test with `mvn`. The JDT/Eclipse language server throws phantom
  project-wide errors after bulk renames — **`mvn` is the source of truth**, ignore the IDE.
- **`mvn clean` after any branch switch** that adds or removes sources. Maven's incremental compiler
  never deletes class files whose source disappeared, which produces build failures on a tree that
  is actually fine.
- **This repo has no CI.** `statusCheckRollup` being empty is by design (goldens, not CI). A
  "mergeable" verdict from `gh pr view` is a *textual* no-conflict signal only.
- **Surefire 3.2.5 uses a comma to separate `-Dtest` values.** `-Dtest='A+B'` matches NOTHING and
  silently runs zero tests. Use `-Dtest='A,B'`.
- **Use `git mv`,** never delete-and-create, so `git log --follow` survives.
- **zsh does not word-split an unquoted `$files`** — a `perl -pi $files` pass silently treats the
  whole list as one filename. Use `find … -exec` as written below.
- **Do not touch anything under `planning/`.** The spec describes this rename in prose and naming
  the old classes there is correct; rewriting it would corrupt the record.
- **Only the *generated* files under `docs/` are protected.** `docs/viz/*.html` and `docs/index.html`
  are what `DocsPagesGoldenTest` pins byte-for-byte; regenerating one would destroy PR 1's central
  proof. `docs/guide/*.md` is hand-written prose, absent from `RegenerateDocs.pages()`, and not
  golden-pinned — it is editable, and Task 2's fix round in fact edited `docs/guide/trie.md` to repair
  a stale anchor. This distinction matters beyond this branch: PR 2 adds a new guide under
  `docs/guide/`.
- Merge convention: `gh pr merge N --merge` (NOT squash), branch **preserved**, on user go-ahead.

---

## File Structure

**Renamed — 6 main classes** (`src/main/java/com/gimlism/translucent/trie/demo/`):

| From | To |
|---|---|
| `TrieDemo.java` | `RadixTrieDemo.java` |
| `TrieVizDemo.java` | `RadixTrieVizDemo.java` |
| `TrieWebVizDemo.java` | `RadixTrieWebVizDemo.java` |
| `TrieLiveWebVizDemo.java` | `RadixTrieLiveWebVizDemo.java` |
| `TrieLiveReplDemo.java` | `RadixTrieLiveReplDemo.java` |
| `TrieLiveControlsDemo.java` | `RadixTrieLiveControlsDemo.java` |

**Renamed — 5 test classes** (`src/test/java/com/gimlism/translucent/trie/demo/`):

| From | To |
|---|---|
| `TrieDemoTest.java` | `RadixTrieDemoTest.java` |
| `TrieVizDemoTest.java` | `RadixTrieVizDemoTest.java` |
| `TrieWebVizDemoTest.java` | `RadixTrieWebVizDemoTest.java` |
| `TrieLiveReplDemoTest.java` | `RadixTrieLiveReplDemoTest.java` |
| `TrieLiveControlsEndToEndTest.java` | `RadixTrieLiveControlsEndToEndTest.java` |

**Modified, not renamed:**
- `src/main/java/com/gimlism/translucent/Launcher.java` — six FQCN string literals (Task 1), six
  `structure` labels (Task 2).
- `src/main/java/com/gimlism/translucent/RegenerateDocs.java` — import + `TrieWebVizDemo::buildHtml`.
- `src/test/java/com/gimlism/translucent/LauncherMenuTest.java` — the structure-name list (Task 2).
- `src/test/java/com/gimlism/translucent/LauncherCatalogTest.java` — two exact-match label lists
  (Task 2). Discovered during execution; the plan originally missed this file.
- `README.md` — the quick-start example (line 64), the six table rows (110–115), the section
  heading (106, Task 2).
- `docs/guide/trie.md` — the README anchor repair. Discovered during execution: Task 2's heading
  relabel changed the GitHub-generated slug, orphaning this guide's hand-typed link to the top of the
  README instead of its section.
- `src/test/java/com/gimlism/translucent/GuideReadmeAnchorTest.java` — new test, added during Task 2's
  fix round to guard the anchor going forward. Discovered during execution; not in the original plan.

**Explicitly NOT renamed** — `trie/viz/`, `trie/events/`, `trie/repl/`, `trie/compare/` and their
tests (`TrieJsonSerializerTest`, `TrieWebExporterTest`, `TrieEventFormatterTest`,
`AsciiTrieRenderTest`, …). Those are implementation-agnostic and PR 2 makes them serve *both* tries,
so `Trie*` is the correct name for them. Renaming them would be actively wrong.

**Deferred to PR 2** (do NOT do here): README prose line 5 ("Four data structures … a radix
**Trie**"), which stays accurate until a fifth structure exists; and the `Page` display title
`"Trie"` → `"Radix trie"` in `RegenerateDocs.vizPages()`, which would move the `index.html` golden
and so destroy Task 3's proof.

---

## Task 1: Rename all eleven classes and every reference

**Files:**
- Rename: the 11 files listed above
- Modify: `src/main/java/com/gimlism/translucent/Launcher.java`,
  `src/main/java/com/gimlism/translucent/RegenerateDocs.java`, `README.md`
- Test: none new — the existing suite is the guard

**Interfaces:**
- Consumes: nothing.
- Produces: the class names `RadixTrieDemo`, `RadixTrieVizDemo`, `RadixTrieWebVizDemo`,
  `RadixTrieLiveWebVizDemo`, `RadixTrieLiveReplDemo`, `RadixTrieLiveControlsDemo` in package
  `com.gimlism.translucent.trie.demo`, each with an unchanged `public static void main(String[])`.
  `RadixTrieWebVizDemo.buildHtml()` stays `public static String`. PR 2 and Task 2 depend on these
  exact names.

- [ ] **Step 1: Record the baseline**

```bash
mvn clean test 2>&1 | tail -5
git status --short
```

Expected: build succeeds; `git status` clean. Confirm the suite total is **572** and failures are 0:

```bash
python3 - <<'EOF'
import glob, re
tot = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
for p in glob.glob('target/surefire-reports/TEST-*.xml'):
    h = open(p).read(3000)
    for k in tot:
        m = re.search(k + r'="(\d+)"', h)
        if m: tot[k] += int(m.group(1))
print(tot)
EOF
```

Expected: `{'tests': 572, 'failures': 0, 'errors': 0, 'skipped': 0}`

- [ ] **Step 2: Rename the six main classes**

```bash
cd src/main/java/com/gimlism/translucent/trie/demo
git mv TrieDemo.java              RadixTrieDemo.java
git mv TrieVizDemo.java           RadixTrieVizDemo.java
git mv TrieWebVizDemo.java        RadixTrieWebVizDemo.java
git mv TrieLiveWebVizDemo.java    RadixTrieLiveWebVizDemo.java
git mv TrieLiveReplDemo.java      RadixTrieLiveReplDemo.java
git mv TrieLiveControlsDemo.java  RadixTrieLiveControlsDemo.java
cd -
```

- [ ] **Step 3: Rename the five test classes**

```bash
cd src/test/java/com/gimlism/translucent/trie/demo
git mv TrieDemoTest.java                  RadixTrieDemoTest.java
git mv TrieVizDemoTest.java               RadixTrieVizDemoTest.java
git mv TrieWebVizDemoTest.java            RadixTrieWebVizDemoTest.java
git mv TrieLiveReplDemoTest.java          RadixTrieLiveReplDemoTest.java
git mv TrieLiveControlsEndToEndTest.java  RadixTrieLiveControlsEndToEndTest.java
cd -
```

- [ ] **Step 4: Rewrite every identifier occurrence**

Run from the repo root. Scope is deliberately `src` and `README.md` only — never `planning/`.

```bash
find src README.md -type f \( -name '*.java' -o -name '*.md' \) -exec perl -pi -e '
  s/(?<!\w)TrieDemo/RadixTrieDemo/g;
  s/(?<!\w)TrieVizDemo/RadixTrieVizDemo/g;
  s/(?<!\w)TrieWebVizDemo/RadixTrieWebVizDemo/g;
  s/(?<!\w)TrieLiveWebVizDemo/RadixTrieLiveWebVizDemo/g;
  s/(?<!\w)TrieLiveReplDemo/RadixTrieLiveReplDemo/g;
  s/(?<!\w)TrieLiveControlsDemo/RadixTrieLiveControlsDemo/g;
  s/(?<!\w)TrieLiveControlsEndToEndTest/RadixTrieLiveControlsEndToEndTest/g;
' {} +
```

Why each piece matters:

- **`(?<!\w)` and no trailing `\b`.** Dropping `\b` is deliberate: `TrieDemo` must also match as the
  *prefix* of `TrieDemoTest`, which is how four of the five test classes get renamed for free. A
  trailing `\b` would fail against the following `T` and leave them untouched.
- **`TrieLiveControlsEndToEndTest` needs its own rule** — it is the one test class that is not a
  prefix-extension of a demo name, so nothing else reaches it.
- **The pass is idempotent.** Every replacement prefixes `Radix`, so a rewritten token's `Trie…`
  is preceded by `x` — a word character — and `(?<!\w)` blocks a second match. Re-running is safe.
- **It is future-proof against PR 2.** Once `StandardTrieDemo` exists, `(?<!\w)TrieDemo` will not
  match inside it (`d` is a word character), so a stray re-run cannot produce
  `StandardRadixTrieDemo`.
- **The six demo names do not nest** — no `Trie*Demo` name contains another — so replacement order
  is irrelevant. (This differs from #45, where `Demo` ⊂ `VizDemo` ⊂ `WebVizDemo` made longest-first
  ordering insufficient.)
- **`find … -exec`, not an unquoted variable** — zsh does not word-split, so `perl -pi $files` would
  treat the whole list as a single filename.
- **The Launcher's FQCNs are string literals built by concatenation** (`TRIE + "TrieDemo"`). They are
  rewritten by the same pass because `"` is not a word character.

- [ ] **Step 5: Verify no old name survives and no unexpected file changed**

```bash
grep -rnP '(?<!\w)Trie(Viz|WebViz|LiveWebViz|LiveRepl|LiveControls)?Demo' src README.md
grep -rnP '(?<!\w)TrieLiveControlsEndToEndTest' src
```

Expected: **no output from either.**

```bash
git status --short
```

Expected: exactly the 11 renames plus modifications to `Launcher.java`, `RegenerateDocs.java` and
`README.md`. **Nothing under `docs/` or `planning/`.** If `docs/` shows a change, stop — the pass
reached further than intended.

- [ ] **Step 6: Build and run the full suite**

```bash
mvn clean test 2>&1 | tail -5
```

Expected: BUILD SUCCESS. Then re-run the count from Step 1.

Expected: `{'tests': 572, 'failures': 0, 'errors': 0, 'skipped': 0}` — **exactly** 572. A different
total means something was added or lost, not renamed.

- [ ] **Step 7: Verify the goldens did not move**

```bash
git status --short docs/
```

Expected: **empty.** This is the load-bearing check. Class names do not appear in recorded output
bytes (established empirically in #45), so all six `DocsPagesGoldenTest` cases passing in Step 6
*without regeneration* is positive evidence the rename touched only what it should.

- [ ] **Step 8: Commit**

```bash
git add -A src README.md
git commit -m "refactor(demo): rename Trie*Demo to RadixTrie*Demo

Trie has meant the radix trie by convention. PR 2 makes StandardTrie a
first-class structure, at which point a reader of the file listing cannot
tell TrieDemo from StandardTrieDemo. Rename now, separately, so the diff
is reviewable as a rename.

Suite unchanged at 572/572; all six docs goldens green without
regeneration, which is what proves this touched nothing but names."
```

---

## Task 2: Disambiguate the display label

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/Launcher.java` (six `structure` labels)
- Modify: `src/test/java/com/gimlism/translucent/LauncherMenuTest.java:48`
- Modify: `README.md:106` (section heading)

**Interfaces:**
- Consumes: the renamed classes from Task 1.
- Produces: `Launcher.CATALOG` entries whose `structure()` is the exact string `"Trie (radix)"` for
  the six radix demos. PR 2 adds `"Trie (standard)"` alongside.

This is separable from Task 1 because Task 1 leaves the tree green with the label still `"Trie"` —
a reviewer can accept the rename and reject the wording.

- [ ] **Step 1: Change the six catalog labels**

In `Launcher.java`, replace the six radix entries (currently at lines 64–69):

```java
            new Entry("Trie (radix)", "text log", TRIE + "RadixTrieDemo"),
            new Entry("Trie (radix)", "ASCII replay", TRIE + "RadixTrieVizDemo"),
            new Entry("Trie (radix)", "web replay (writes .html)", TRIE + "RadixTrieWebVizDemo"),
            new Entry("Trie (radix)", "live web", TRIE + "RadixTrieLiveWebVizDemo"),
            new Entry("Trie (radix)", "terminal REPL", TRIE + "RadixTrieLiveReplDemo"),
            new Entry("Trie (radix)", "browser REPL", TRIE + "RadixTrieLiveControlsDemo"),
```

Leave the `"Trie (compression)"` entry below them untouched.

- [ ] **Step 2: Run the label guards**

```bash
mvn test -Dtest='LauncherMenuTest,LauncherReadmeTest,LauncherCatalogTest' 2>&1 | tail -12
```

⚠️ **CORRECTED 2026-08-11 — this step's original premise was wrong.** The plan claimed the label was
pinned by nothing and that this command would PASS. It does **not**: `LauncherCatalogTest:44` and
`:53` pin the structure label by exact match (`List.of("ArrayList", "HashMap", "TreeSet", "Trie")`
and an ordered `assertEquals` of the distinct labels), so changing the six entries in Step 1 turns
that file RED with 2 failures.

Expected: **2 failures in `LauncherCatalogTest`.** Update both lists there to `"Trie (radix)"`.

The original claim came from reading `LauncherMenuTest:48` (`out.contains("Trie")`, which
`"Trie (radix)"` satisfies as a substring) and generalising from one guard. `LauncherMenuTest` is
indeed weak here; `LauncherCatalogTest` was already strong. Step 3 still strengthens the menu guard,
but it is closing a *second* hole, not the only one.

**`LauncherCatalogTest.java` is therefore a fourth file this task modifies.**

- [ ] **Step 3: Pin the label in `LauncherMenuTest`**

In `LauncherMenuTest.java`, change the list on line 48:

```java
        for (String s : List.of("ArrayList", "HashMap", "TreeSet", "Trie (radix)")) {
```

- [ ] **Step 4: Prove the new pin actually fails when it should**

⚠️ **CORRECTED 2026-08-11 — reverting a SINGLE entry does not go RED in `LauncherMenuTest`.**
`printMenu` (`Launcher.java:143`) emits a heading whenever `entry.structure()` *changes* from the
previous row. So with one entry reverted, row 19 prints the heading `Trie` and row 20 prints
`Trie (radix)` — the string the test looks for is still present, and `LauncherMenuTest` stays green
while `LauncherCatalogTest` fails instead. The mutation must revert **all six** entries to isolate
the menu guard.

Temporarily revert all six radix labels:

```bash
perl -pi -e 's/new Entry\("Trie \(radix\)"/new Entry("Trie"/g' \
  src/main/java/com/gimlism/translucent/Launcher.java
mvn test -Dtest='LauncherMenuTest' 2>&1 | tail -12
```

Expected: **FAIL** — literally
`LauncherMenuTest.menuNamesEachStructureOnce:49 missing structure heading Trie (radix) ==> expected: <true> but was: <false>`

Restore:

```bash
perl -pi -e 's/new Entry\("Trie", /new Entry("Trie (radix)", /g' \
  src/main/java/com/gimlism/translucent/Launcher.java
mvn test -Dtest='LauncherMenuTest,LauncherCatalogTest' 2>&1 | tail -5
```

Expected: PASS. Then confirm `git diff src/main/java/com/gimlism/translucent/Launcher.java` shows
only the six intended label changes — the restore regex must not have altered
`"Trie (compression)"`.

- [ ] **Step 5: Update the README section heading**

In `README.md`, replace line 106:

```markdown
### Trie (radix) — edges split and merge as keys arrive and leave
```

(The old text read `### Trie — a radix trie whose edges split and merge as keys arrive and leave`;
"a radix trie whose" becomes redundant once the heading says radix.)

- [ ] **Step 6: Run the full suite**

```bash
mvn clean test 2>&1 | tail -5
```

Expected: BUILD SUCCESS, and the count check from Task 1 Step 1 gives exactly
`{'tests': 572, 'failures': 0, 'errors': 0, 'skipped': 0}`.

- [ ] **Step 7: Confirm the goldens still have not moved**

```bash
git status --short docs/
```

Expected: **empty.**

- [ ] **Step 8: Commit**

```bash
git add -A src README.md
git commit -m "refactor(launcher): label the radix trie as Trie (radix)

Plain 'Trie' is ambiguous once StandardTrie becomes a first-class
structure. LauncherMenuTest now pins the label, which it did not before —
contains(\"Trie\") passed for \"Trie (radix)\" as a substring, so the
label was unguarded. Mutation-proven: reverting one entry to \"Trie\"
fails with 'missing structure heading Trie (radix)'."
```

---

## Task 3: Verify the branch and open the PR

**Files:** none modified.

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: an open PR against `main`.

- [ ] **Step 1: Confirm `src/main` semantics are unchanged**

```bash
git diff main --stat
git diff main -- src/main/java/com/gimlism/translucent/trie/core/ \
                 src/main/java/com/gimlism/translucent/trie/viz/ \
                 src/main/java/com/gimlism/translucent/trie/events/ \
                 src/main/java/com/gimlism/translucent/trie/repl/ \
                 src/main/java/com/gimlism/translucent/trie/compare/
```

Expected: the second command produces **no output** — this PR touches no trie logic, only the demo
layer, the launcher and the README.

- [ ] **Step 2: Confirm the generated docs surfaces did not move**

```bash
git diff main --stat -- docs/viz/ docs/index.html
```

Expected: **empty** — this is the central proof that all six `DocsPagesGoldenTest` cases pass without
regeneration. (The broader `git diff main --stat -- docs/` legitimately shows one file,
`docs/guide/trie.md`, one line changed — the anchor repair from Task 2's fix round. That file is
hand-written, outside `RegenerateDocs.pages()`, and not golden-pinned, so its change does not weaken
this proof.)

- [ ] **Step 3: Final clean build**

```bash
mvn clean test 2>&1 | tail -5
```

Expected: BUILD SUCCESS, exactly **573** tests, 0 failures. (572 for the rename, +1 for the
anchor guard added during Task 2 — see the ledger.)

- [ ] **Step 4: Push and open the PR** — ⚠️ **on user go-ahead only.** Pushing a branch and opening
      a PR are outward-facing on a public repo; confirm before running this step.

```bash
git push -u origin feat/rename-radix-trie-demos
gh pr create --title "refactor: rename Trie*Demo to RadixTrie*Demo" --body "$(cat <<'BODY'
`Trie` has meant "the radix trie" by convention. PR 2 of this arc makes `StandardTrie` a
first-class structure with its own six demos, at which point a reader of the file listing
cannot tell `TrieDemo` from `StandardTrieDemo`.

This PR is rename-only and ships separately on purpose: git renders "renamed + modified" as
delete+add once content changes land alongside, so a combined PR could not be reviewed as a
rename.

**Scope:** 6 main classes + 5 test classes renamed via `git mv`; 38 identifier occurrences
rewritten; `Launcher` labels `Trie` → `Trie (radix)`; README quick-start, table rows and section
heading updated. Review turned up a second defect along the way: the heading relabel changed the
GitHub-generated slug for that section, orphaning `docs/guide/trie.md`'s hand-typed anchor back to
the README — it pointed at the top of the file instead of its section. That's fixed here, along with
a new guard, `GuideReadmeAnchorTest`, so it can't happen silently again.

**Not touched:** `trie/viz`, `trie/events`, `trie/repl`, `trie/compare` — those are
implementation-agnostic and PR 2 makes them serve both tries, so `Trie*` is correct for them.

**Evidence:**
- Suite **573/573**: 572 of those are the pre-existing suite, unchanged by the rename itself; the
  remaining 1 is the new `GuideReadmeAnchorTest`.
- The central proof: `git diff main --stat -- docs/viz/ docs/index.html` is empty, so all six
  `DocsPagesGoldenTest` cases pass **without regeneration** — class names do not appear in recorded
  output bytes, so this is what proves the rename touched only names. The one file that *does* differ
  from `main` under `docs/` is `docs/guide/trie.md` (one line — the anchor repair above); it's
  hand-written prose, outside `RegenerateDocs.pages()`, and not golden-pinned, so it does not weaken
  the proof.
- The anchor defect: `docs/guide/trie.md` links back to its README section by a slug hand-typed at
  guide-authoring time, not one recomputed from the heading, so nothing kept it in sync when the
  heading text changed. `GuideReadmeAnchorTest` slugifies every README `### ` heading and checks each
  guide's anchor resolves to one of them. Mutation-proven — reverting the anchor to its pre-rename
  slug fails the guard — and the repaired anchor was independently checked against GitHub's own
  Markdown renderer.
- `LauncherMenuTest` now pins the structure label, which it did not before: `contains("Trie")`
  passed for `"Trie (radix)"` as a substring. Mutation-proven.

Spec: `planning/specs/2026-08-11-standard-trie-web-arc-design.md`
BODY
)"
```

- [ ] **Step 5: Wait for Copilot, then hand over**

Copilot's auto-request is **asynchronous** — an empty `reviewRequests` seconds after `gh pr create`
does NOT mean it is off. Wait, then check:

```bash
gh pr view --json reviewRequests,reviews,comments
```

If Copilot has genuinely not run after ~15 minutes, note that **re-requesting from the CLI is
impossible**: `POST /pulls/N/requested_reviewers` with `reviewers[]=Copilot` returns HTTP 200 but is
a silent no-op, and `gh pr edit N --add-reviewer Copilot` fails with
`GraphQL: Could not resolve user with login 'copilot'`. Only the web UI's ↻ icon works — ask the
user to click it.

**Do not merge.** Merging is on user go-ahead, with `gh pr merge N --merge` (NOT squash, branch
preserved).

- [ ] **Step 6: After merge — the Pages check**

Required even though this PR touches no file under `docs/`: Pages rebuilds on **every** push to
`main`, and #51 confirmed a merge touching no `docs/` file still triggers a rebuild. A failed build
takes the live site down.

```bash
gh api repos/gimlism/translucent/pages/builds/latest \
  --jq '{status,commit,duration,error:.error.message}'
```

Expected: `status: "built"`, `error: null`, commit matching the merge commit. Then confirm the
served bytes still match the committed files:

```bash
for p in index.html viz/list.html viz/map.html viz/treeset.html viz/trie.html \
         viz/compression-compare.html; do
  live=$(curl -sS -o /tmp/pg.$$ -w '%{http_code}' "https://gimlism.github.io/translucent/$p")
  a=$(shasum -a 256 /tmp/pg.$$ | cut -d' ' -f1)
  b=$(shasum -a 256 "docs/$p" | cut -d' ' -f1)
  [ "$a" = "$b" ] && m=IDENTICAL || m='*** DIFFERS ***'
  echo "$p  http=$live  $m"
done; rm -f /tmp/pg.$$
```

Expected: all six `http=200 IDENTICAL`.

---

## Definition of Done

- 11 files renamed with `git mv`; `git log --follow` intact.
- Zero occurrences of the old six names (plus `TrieLiveControlsEndToEndTest`) in `src` or `README.md`.
- Suite at **exactly 573/573**, 0 failures (572 renamed + 1 new anchor guard).
- `git diff main --stat -- docs/viz/ docs/index.html` **empty**; all six golden cases green without
  regeneration. (`git diff main -- docs/` shows one line in `docs/guide/trie.md`, the anchor repair —
  hand-written, ungenerated, not golden-pinned, so it does not weaken the proof.)
- `Launcher` labels read `Trie (radix)`, and `LauncherMenuTest` pins it (mutation-proven).
- `planning/` untouched by the rename pass.
- PR open, Copilot triaged, post-merge Pages check green.
