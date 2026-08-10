# Pin the Guide Test Set — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make it impossible for a guide under `docs/guide/` to exist without a test asserting it, closing the minor left open at the end of PR #48.

**Architecture:** Replace four hand-written `@Test` wrappers with one `@ParameterizedTest` driven by a `cases()` registry, then pin that registry against the directory in both directions. Per-guide drift becomes unrepresentable rather than merely detected: one guide cannot go unverified while the others stay checked, because removing its coverage means removing its registry entry, and the directory guard rejects that. (Removing the single parameterized driver still drops all four guides' coverage at once — that residual is inherent to not reflecting over `@Test` methods, and is out of scope here.)

**Tech Stack:** Java 21, Maven, JUnit Jupiter 5.10.2 (the `junit-jupiter` aggregate already provides `-params`).

## Global Constraints

- **No production change.** `git diff --stat src/main` must be empty at every commit. This is test-structure work only.
- **JDK 21 + Maven.** Build with `mvn`, never a language server — the JDT/Eclipse LSP throws phantom project-wide errors in this repo and `mvn` is the source of truth.
- **`mvn clean` after any branch switch** that adds or removes sources. Maven's incremental compiler never deletes class files whose source disappeared, which produces build failures on a branch that is actually fine.
- **Surefire test selectors are comma-separated.** `-Dtest='A+B'` matches nothing and silently skips; use `-Dtest=A,B`. (This exact defect was caught mid-flight in PR #49's plan.)
- **Tests may read `docs/**` and `README.md` by relative path.** Surefire sets the working directory to `${basedir}`.
- **Every mutation must be reverted** and the revert verified before moving on.
- Plans and specs live in `planning/`, never `docs/` — `docs/` is served publicly by GitHub Pages as of 2026-08-10.
- Branch: `test/pin-guide-test-set`. Spec: `planning/specs/2026-08-10-pin-guide-test-set-design.md`.

## File Structure

| File | Responsibility | Change |
| --- | --- | --- |
| `src/test/java/com/gimlism/translucent/GuideEventMapTest.java` | Drives every documented guide row against the real structure and compares both ways | **Modify only** — the sole file this plan touches |

No new files. The class already holds the scenarios, the parser, and the assertions; the registry belongs beside them.

Current landmarks in that file (line numbers as of `fd7d63c`):

- `:44` class declaration, `:46` `GUIDE_DIR`
- `:112` `assertGuideMatches`, `:127` `assertMethodExists`
- `:142` `listScenario`, `:156` list `@Test`
- `:170` `mapScenario`, `:201` map `@Test`
- `:207` `treeSetScenario`, `:225` treeset `@Test`
- `:231` `trieScenario`, `:254` trie `@Test`
- `:259`–`:274` the directory guard being replaced

---

### Task 1: Registry and parameterized case

Behaviour-preserving restructure. The old directory guard stays untouched in this task, so the #48 hole is still open at the end of it — Task 2 closes it. Suite count does not move.

**Files:**
- Modify: `src/test/java/com/gimlism/translucent/GuideEventMapTest.java` (imports at `:18`–`:31`; delete the four `@Test` wrappers at `:156`, `:201`, `:225`, `:254`; insert the registry after `assertMethodExists` ends at `:140`)

**Interfaces:**
- Consumes: existing `assertGuideMatches(Path, Class<?>, Map<String, Set<String>>)`, and the four existing private scenario methods `listScenario()`, `mapScenario()`, `treeSetScenario()`, `trieScenario()`, each returning `Runner<E>` with an `emitted()` accessor returning `Map<String, Set<String>>`.
- Produces: `record GuideCase(String file, Class<?> type, Supplier<Map<String, Set<String>>> emitted)` and `static Stream<GuideCase> cases()`. Task 2 consumes both, using `GuideCase::file` and `Files.isRegularFile`.

- [ ] **Step 1: Add the three new imports**

In the import block, add alongside the existing ones (keep alphabetical order within their groups):

```java
import java.util.function.Supplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
```

`java.util.function.Supplier` goes after `java.util.function.Consumer` (`:27`). The two `org.junit.jupiter.params.*` imports go after `import org.junit.jupiter.api.Test;` (`:31`).

- [ ] **Step 2: Insert the registry and the parameterized case**

Immediately after the closing brace of `assertMethodExists` (`:140`) and before `private static Runner<ListEvent> listScenario()` (`:142`), insert:

```java
    /**
     * One guide and the scenario that proves it.
     *
     * <p>The supplier is lazy on purpose. A scenario that throws must fail its own case only,
     * leaving the directory guards free to report what is actually missing — eager evaluation would
     * take them down alongside it and describe the wrong problem.
     */
    record GuideCase(String file, Class<?> type, Supplier<Map<String, Set<String>>> emitted) {
        @Override
        public String toString() {
            return file;   // the parameterized display name, so a failure names the guide
        }
    }

    /**
     * Every guide and how it is proven. Deliberately the single source of truth: the parameterized
     * case below runs exactly these, and the directory guards compare exactly these against disk, so
     * coverage cannot be dropped without the registry shrinking and the guards noticing.
     */
    static Stream<GuideCase> cases() {
        return Stream.of(
                new GuideCase("list.md", TeachingArrayList.class, () -> listScenario().emitted()),
                new GuideCase("map.md", TeachingHashMap.class, () -> mapScenario().emitted()),
                new GuideCase("treeset.md", TeachingTreeSet.class, () -> treeSetScenario().emitted()),
                new GuideCase("trie.md", RadixTrie.class, () -> trieScenario().emitted()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void guideMatchesWhatTheStructureEmits(GuideCase c) throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve(c.file()), c.type(), c.emitted().get());
    }
```

- [ ] **Step 3: Delete the four `@Test` wrappers**

Delete these four methods entirely, including their `@Test` annotations. Leave every `*Scenario()` method and every comment attached to a scenario exactly as it is — those carry the reasoning and are not being changed.

Delete `listGuideMatchesWhatTheListEmits` (was `:156`–`:160`):

```java
    @Test
    void listGuideMatchesWhatTheListEmits() throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve("list.md"), TeachingArrayList.class,
                listScenario().emitted());
    }
```

Delete `mapGuideMatchesWhatTheMapEmits` (was `:201`–`:205`):

```java
    @Test
    void mapGuideMatchesWhatTheMapEmits() throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve("map.md"), TeachingHashMap.class,
                mapScenario().emitted());
    }
```

Delete `treeSetGuideMatchesWhatTheSetEmits` (was `:225`–`:229`):

```java
    @Test
    void treeSetGuideMatchesWhatTheSetEmits() throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve("treeset.md"), TeachingTreeSet.class,
                treeSetScenario().emitted());
    }
```

Delete `trieGuideMatchesWhatTheTrieEmits` (was `:254`–`:257`):

```java
    @Test
    void trieGuideMatchesWhatTheTrieEmits() throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve("trie.md"), RadixTrie.class, trieScenario().emitted());
    }
```

- [ ] **Step 4: Run the class and confirm four invocations plus the old guard**

Run: `mvn clean test -Dtest=GuideEventMapTest`

Expected: `Tests run: 5, Failures: 0` — four parameterized invocations named `list.md`, `map.md`, `treeset.md`, `trie.md`, plus the still-present `guideDirHasExactlyOneFileForEachDocumentedStructure`.

If you see `Tests run: 1`, the `@MethodSource` name does not match the method name `cases` — fix that rather than proceeding.

- [ ] **Step 5: Run the full suite and confirm the count has NOT moved**

Run: `mvn clean test`

Expected: `Tests run: 571, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

571 is the pre-change total. This task is behaviour-preserving, so a count that moved means a test was lost or duplicated.

- [ ] **Step 6: Mutation — prove each case really runs**

A `@MethodSource` that quietly yields fewer cases than intended would still pass. Prove the parameterization drives each guide independently by corrupting exactly one guide and checking that the failure names *that* guide.

```bash
cp docs/guide/treeset.md /tmp/treeset.md.bak
# rename a documented method so the guide's row set no longer matches what the code emits
perl -pi -e 's/`floor\(/`flooor(/' docs/guide/treeset.md
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|treeset\.md"
```

Expected: `Tests run: 5, Failures: 1`, and a message naming `docs/guide/treeset.md` (the `grep` will match this, not the invocation label — Surefire's console/XML output shows `guideMatchesWhatTheStructureEmits(GuideCase)[3]`, not the guide filename; only IDE runners display the `{0}` name). The other three invocations must still pass — that is the part being proven.

Revert and confirm:

```bash
cp /tmp/treeset.md.bak docs/guide/treeset.md && rm /tmp/treeset.md.bak
git diff --stat docs/guide/treeset.md    # expect: no output
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep "Tests run:"
```

Expected after revert: `Tests run: 5, Failures: 0`.

- [ ] **Step 7: Confirm no production change, then commit**

```bash
git diff --stat src/main        # expect: no output
git add src/test/java/com/gimlism/translucent/GuideEventMapTest.java
git commit -m "$(cat <<'EOF'
test(guides): drive the guide checks from a case registry

Four near-identical @Test wrappers became one parameterized case over a
cases() registry. Pure restructure: the scenarios, the parser and the
assertions are untouched, and the suite stays at 571.

This is setup. The registry is what the next commit pins against the
directory, which is what actually closes the hole.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Directional guards that close the hole

Replaces the single file-set guard with two directional ones. This is the task that makes the #48 defect unrepresentable, and it is where the suite count moves 571 → 572.

**Files:**
- Modify: `src/test/java/com/gimlism/translucent/GuideEventMapTest.java` (delete the old guard, formerly `:259`–`:274`, now shifted by Task 1; add two tests in its place; add one import; remove one)

**Interfaces:**
- Consumes: `cases()` and `GuideCase::file` from Task 1; `GUIDE_DIR` (`:46`).
- Produces: nothing consumed downstream — this is the terminal task.

- [ ] **Step 1: Write the two failing guards**

Delete the existing guard entirely — the comment above it and the method:

```java
    // The four tests above each hard-code one filename under GUIDE_DIR. Nothing forces that list to
    // stay exhaustive if a fifth guide is added later, so enumerate the directory here and pin its
    // contents directly — a new guide with no scenario would otherwise ship untested.
    @Test
    void guideDirHasExactlyOneFileForEachDocumentedStructure() throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(GUIDE_DIR)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md"))
                    .forEach(names::add);
        }
        assertEquals(Set.of("list.md", "map.md", "treeset.md", "trie.md"), names,
                GUIDE_DIR + " contains a different set of guide files than this test expects — "
                        + "add a *GuideMatchesWhatTheXEmits test (and scenario) for any new guide, "
                        + "then update this test's expected set");
    }
```

Put these two in its place:

```java
    /**
     * Direction one: every guide on disk is claimed by a case. A guide added without a scenario
     * behind it would otherwise ship unverified — the direction PR #48 already covered, restated
     * against the registry rather than against a hardcoded set that had to be edited by hand.
     */
    @Test
    void everyGuideFileHasACase() throws IOException {
        Set<String> claimed = cases().map(GuideCase::file).collect(Collectors.toSet());
        try (Stream<Path> files = Files.list(GUIDE_DIR)) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md"))
                    .forEach(n -> assertTrue(claimed.contains(n),
                            GUIDE_DIR.resolve(n) + " has no GuideCase — add one, with a scenario, "
                                    + "so the guide is checked against the code it describes"));
        }
    }

    /**
     * Direction two, and the hole PR #48 left open: a case may only name a guide that is really
     * there. Together with direction one this makes an unpinned guide unrepresentable — dropping
     * coverage means dropping a case, and direction one then reports the orphaned file.
     *
     * <p>{@code isRegularFile}, never {@code exists}: a DIRECTORY named {@code trie.md} satisfies
     * {@code exists} and produced a real false green in {@code SiteIndexTest} on #49.
     *
     * <p>The emptiness check is not ceremony. Both directions iterate, so both pass over an empty
     * registry, agreeing perfectly about nothing.
     */
    @Test
    void everyCaseNamesARegularGuideFile() {
        assertTrue(cases().findAny().isPresent(),
                "the case registry is empty — every other check in this class would pass vacuously");
        cases().forEach(c -> {
            Path md = GUIDE_DIR.resolve(c.file());
            assertTrue(Files.isRegularFile(md),
                    md + " is named by a GuideCase but is not a regular file");
        });
    }
```

- [ ] **Step 2: Fix the imports**

**Keep `import java.util.TreeSet;`.** An earlier draft of this step said to remove it, on the belief that the deleted guard was its only user. That was wrong: `Runner.call` (`:74`) and `parseGuide` (`:103`) both construct `new TreeSet<>()`, and removing the import fails compilation with two `cannot find symbol` errors. Verify the import is still needed rather than assuming either way:

```bash
grep -nE '(^|[^g])\bTreeSet\b' src/test/java/com/gimlism/translucent/GuideEventMapTest.java \
  | grep -viE 'teachingtreeset|treeSetScenario|treeset\.md'
```

Expected: the import line plus the two `new TreeSet<>()` constructions. If only the import appears, then it really has become unused and should go.

Add, in alphabetically sorted position among the `java.util.stream.*` imports (this file's import block is strictly sorted, so `Collectors` goes *before* `Stream`, not after `Supplier`):

```java
import java.util.stream.Collectors;
```

- [ ] **Step 3: Run the class and confirm six tests**

Run: `mvn clean test -Dtest=GuideEventMapTest`

Expected: `Tests run: 6, Failures: 0` — four parameterized invocations plus the two new guards.

- [ ] **Step 4: Mutation A — delete a case (this is the #48 hole itself)**

```bash
cp src/test/java/com/gimlism/translucent/GuideEventMapTest.java /tmp/gemt.bak
perl -0pi -e 's|\n *new GuideCase\("trie\.md", RadixTrie\.class, \(\) -> trieScenario\(\)\.emitted\(\)\)|)|' src/test/java/com/gimlism/translucent/GuideEventMapTest.java
```

If that substitution leaves the file uncompilable, edit by hand instead: remove the `trie.md` entry from `cases()` and close the preceding entry's parenthesis.

Run: `mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|has no GuideCase"`

Expected: a failure in `everyGuideFileHasACase` reading `docs/guide/trie.md has no GuideCase — add one, with a scenario, …`, and `Tests run: 5` (one fewer parameterized invocation).

**This is the exact defect from #48.** Before this change the suite went green here.

Revert: `cp /tmp/gemt.bak src/test/java/com/gimlism/translucent/GuideEventMapTest.java`

- [ ] **Step 5: Mutation B — a fifth guide with no case**

```bash
printf '# Deque\n\n## What each method makes you see\n\n| method | events |\n| --- | --- |\n| `add(e)` | `Append` |\n' > docs/guide/deque.md
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|has no GuideCase"
```

Expected: `everyGuideFileHasACase` fails naming `docs/guide/deque.md`.

Revert: `rm docs/guide/deque.md`

- [ ] **Step 6: Mutation C — a case pointing at nothing**

```bash
cp src/test/java/com/gimlism/translucent/GuideEventMapTest.java /tmp/gemt.bak
perl -pi -e 's/new GuideCase\("trie\.md"/new GuideCase("nosuch.md"/' src/test/java/com/gimlism/translucent/GuideEventMapTest.java
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|not a regular file|has no GuideCase"
```

Expected: `everyCaseNamesARegularGuideFile` fails with `docs/guide/nosuch.md is named by a GuideCase but is not a regular file`, **and** `everyGuideFileHasACase` fails naming the now-orphaned `docs/guide/trie.md`. Both directions firing at once is correct here — one case is wrong in both senses.

Revert: `cp /tmp/gemt.bak src/test/java/com/gimlism/translucent/GuideEventMapTest.java`

- [ ] **Step 7: Mutation D — a directory where a guide should be**

This is the one `Files.exists` would miss.

```bash
mv docs/guide/trie.md /tmp/trie.md.real
mkdir docs/guide/trie.md
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|not a regular file"
```

Expected: `everyCaseNamesARegularGuideFile` fails with `docs/guide/trie.md is named by a GuideCase but is not a regular file`.

Sanity-check the mechanism while you are here — swap `Files.isRegularFile(md)` for `Files.exists(md)`, re-run, and confirm it goes **green** (the false green). Then restore `isRegularFile` before reverting.

Revert:

```bash
rmdir docs/guide/trie.md && mv /tmp/trie.md.real docs/guide/trie.md
git status --short docs/guide/    # expect: no output
```

- [ ] **Step 8: Mutation E — an empty registry, isolated**

Emptying `cases()` alone fires both guards. To prove the emptiness assertion specifically, empty the directory too, so the two would otherwise agree about nothing.

```bash
cp src/test/java/com/gimlism/translucent/GuideEventMapTest.java /tmp/gemt.bak
mkdir -p /tmp/guides.bak && mv docs/guide/*.md /tmp/guides.bak/
```

Edit `cases()` by hand to `return Stream.of();`, then:

```bash
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|registry is empty"
```

Expected: `everyCaseNamesARegularGuideFile` fails with `the case registry is empty — every other check in this class would pass vacuously`. Note that `everyGuideFileHasACase` **passes** here — that is precisely the vacuity being guarded against.

Revert:

```bash
cp /tmp/gemt.bak src/test/java/com/gimlism/translucent/GuideEventMapTest.java
mv /tmp/guides.bak/*.md docs/guide/ && rmdir /tmp/guides.bak
git status --short docs/guide/    # expect: no output
```

- [ ] **Step 9: Mutation F — a broken scenario must not take the guards down**

This is what the lazy `Supplier` buys.

```bash
cp src/test/java/com/gimlism/translucent/GuideEventMapTest.java /tmp/gemt.bak
perl -pi -e 's/RadixTrie<Integer> trie = new RadixTrie<>\(\);/RadixTrie<Integer> trie = new RadixTrie<>(); if (true) throw new IllegalStateException("probe");/' src/test/java/com/gimlism/translucent/GuideEventMapTest.java
mvn clean test -Dtest=GuideEventMapTest 2>&1 | grep -E "Tests run:|probe|registry is empty"
```

Expected: exactly one failure, the `trie.md` parameterized invocation, with `IllegalStateException: probe`. **Both guards must still pass** — they never call the supplier. If a guard also fails, the registry is being evaluated eagerly somewhere and that must be fixed.

Revert: `cp /tmp/gemt.bak src/test/java/com/gimlism/translucent/GuideEventMapTest.java && rm /tmp/gemt.bak`

- [ ] **Step 10: Full suite at the new count**

Run: `mvn clean test`

Expected: `Tests run: 572, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

572 = 571 + 1, the one added guard. A different number means a mutation was not fully reverted — check `git status --short` before going further.

- [ ] **Step 11: Confirm a clean tree and no production change, then commit**

```bash
git status --short              # expect: only GuideEventMapTest.java modified
git diff --stat src/main        # expect: no output
git add src/test/java/com/gimlism/translucent/GuideEventMapTest.java
git commit -m "$(cat <<'EOF'
test(guides): pin the registry to the guide directory, both directions

Closes the minor left open at the end of #48. The old guard pinned which
guide FILES exist, not which are asserted, so deleting a @Test wrapper
left a guide unverified with the suite green — visible only as the count
dropping by one, which nothing watches.

Dropping coverage now means dropping a cases() entry, and the
directory-to-registry direction reports the orphaned file by name. The
hole is not detected, it is unrepresentable.

isRegularFile rather than exists, because a directory satisfies exists
and produced a real false green in SiteIndexTest on #49. The emptiness
assertion is there because both directions iterate, so both pass over an
empty registry, agreeing perfectly about nothing.

Six mutations proven RED and reverted: delete a case; add a fifth guide;
point a case at nothing; replace a guide with a directory of that name;
empty the registry with the directory also emptied, isolating the
vacuity check; and throw from a scenario, which must fail its own case
while both guards still report cleanly.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage.** Every section of `planning/specs/2026-08-10-pin-guide-test-set-design.md` maps to a step:

| Spec requirement | Task/step |
| --- | --- |
| `GuideCase` record with lazy `Supplier` and `toString` | T1 S2 |
| `cases()` registry | T1 S2 |
| `@ParameterizedTest(name = "{0}")` over `cases()` | T1 S2 |
| Two directional guards replacing the one | T2 S1 |
| `Files.isRegularFile`, never `exists` | T2 S1, mechanism proven T2 S7 |
| Non-empty assertion | T2 S1, isolated T2 S8 |
| Scenarios/`assertGuideMatches`/`parseGuide` untouched | T1 S3 states it explicitly |
| No production change | Global constraint; checked T1 S7, T2 S11 |
| Suite 571 → 572 | T1 S5 (571), T2 S10 (572) |
| Six mutations proven RED | T2 S4–S9 |

**Placeholder scan.** No TBD/TODO. Every code step carries the literal code; every command carries expected output. Task 2's steps repeat full snippets rather than referring back to Task 1, since an implementer may read them out of order.

**Type consistency.** `GuideCase(String file, Class<?> type, Supplier<Map<String, Set<String>>> emitted)` is declared once in T1 S2 and used in T2 S1 as `GuideCase::file` and `c.file()`. `cases()` returns `Stream<GuideCase>` in both. `emitted()` returns `Map<String, Set<String>>`, matching what `assertGuideMatches` takes as its third parameter and what `Runner.emitted()` already returns.

**One deviation from the spec, deliberate.** The spec's mutation table lists six rows; this plan adds a seventh in T1 S6 — corrupting one guide to prove each parameterized case runs independently. Without it, a `@MethodSource` yielding fewer cases than intended would pass unnoticed, which is the same vacuity class the rest of the plan is about. It costs one step.
