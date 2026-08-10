# Pin the guide *test* set, not just the guide *file* set

**Date:** 2026-08-10
**Status:** approved, ready for implementation
**Scope:** one test class. No production change.

## The defect

`GuideEventMapTest.guideDirHasExactlyOneFileForEachDocumentedStructure` (`:263`) pins the set of
files under `docs/guide/` to `{list, map, treeset, trie}.md`. It was added in PR #48 to stop a fifth
guide shipping with no scenario behind it, and for that direction it works.

It does not pin the **test** set. Each of the four guides is asserted by its own `@Test` wrapper
(`:156`, `:201`, `:225`, `:254`), and deleting one of those leaves the file set at four. The
directory guard stays green, the deleted guide is no longer verified against the code it describes,
and the only visible trace is the suite count falling from 571 to 570 — which nothing watches.

Recorded as an open minor at the end of PR #48. This closes it.

## Why a guard is the wrong shape here

The obvious fix is a second guard: reflect over the class's `@Test` methods and assert one exists per
guide. That detects the hole rather than removing it, and it couples the check to method *names*, so
renaming a test either breaks the guard or — worse — silently stops matching.

This repo has now been bitten three times by guards that pass vacuously when their discovery step
goes quiet: the `Page.guide()` 404 and the `index.html` filename contract in #49, and this morning's
`ReadmeSiteLinksTest`, which needed an explicit count assertion precisely because both of its
iterating checks pass over an empty match set. Adding a fourth guard of that shape is the wrong
lesson to draw.

The registry makes the defect unrepresentable instead. There is no discovery step to go quiet,
because the thing that drives the assertions is the same thing the directory is compared against.

## Design

### The registry

```java
/**
 * One guide and the scenario that proves it. The supplier is lazy on purpose: a scenario that
 * throws must fail its own case only, leaving the directory guard free to report what is actually
 * missing rather than dying alongside it.
 */
record GuideCase(String file, Class<?> type, Supplier<Map<String, Set<String>>> emitted) {
    @Override
    public String toString() {
        return file;   // the parameterized display name — failures name the guide, not a record dump
    }
}

static Stream<GuideCase> cases() {
    return Stream.of(
            new GuideCase("list.md",    TeachingArrayList.class, () -> listScenario().emitted()),
            new GuideCase("map.md",     TeachingHashMap.class,   () -> mapScenario().emitted()),
            new GuideCase("treeset.md", TeachingTreeSet.class,   () -> treeSetScenario().emitted()),
            new GuideCase("trie.md",    RadixTrie.class,         () -> trieScenario().emitted()));
}
```

### The assertions

```java
@ParameterizedTest(name = "{0}")
@MethodSource("cases")
void guideMatchesWhatTheStructureEmits(GuideCase c) throws IOException {
    assertGuideMatches(GUIDE_DIR.resolve(c.file()), c.type(), c.emitted().get());
}
```

The single existing directory guard is replaced by **two** tests, one per direction, so a failure
says which way the two sides disagree rather than merely that they do:

```java
@Test void everyGuideFileHasACase()          // directory → cases
@Test void everyCaseNamesARegularGuideFile() // cases → directory
```

- **directory → cases**: every `.md` under `GUIDE_DIR` is named by some case. Catches a new guide
  shipping unverified — the direction #48 already covered.
- **cases → directory**: every case names a file that is present *and is a regular file*. Catches a
  case pointing at nothing, and a case pointing at a directory.

`Files.isRegularFile`, never `Files.exists`. That is not a stylistic preference: Copilot's one
confirmed finding on #49 was exactly this, and the probe showed a directory named `docs/guide/trie.md`
satisfying `Files.exists` and producing a real false green.

`everyCaseNamesARegularGuideFile` also asserts the registry is non-empty. Both directions iterate,
so both pass vacuously on an empty registry; that assertion lives with the cases-side test because
an empty `cases()` is precisely what would hollow it out. Same reasoning as the count assertion in
`ReadmeSiteLinksTest`, and mutation 6 below is what proves it fires.

### What is not touched

The four scenario methods and all their commentary, `assertGuideMatches`, `parseGuide`,
`assertMethodExists`, `Runner`, and every other test in the repo. `src/main` is byte-unchanged.

Four `@Test` methods become four parameterized invocations — no change there. The one directory
guard becomes two, so the suite goes **571 → 572**. A count that moves by exactly one, in a change
that adds exactly one test, is itself a small check that nothing was silently dropped.

## Failure modes, each to be mutation-proven

Green is not evidence. Every row below must be demonstrated RED before the work is considered done,
and reverted afterwards.

| mutation | expected failure |
| --- | --- |
| delete a `cases()` entry | directory holds a guide no case names — **the #48 hole itself** |
| add a fifth `.md` under `docs/guide/` | file with no case |
| repoint a case at `nosuch.md` | case names a file that is not there |
| replace `trie.md` with a directory of that name | the `isRegularFile` guard |
| break one scenario | that case only; the directory guard still reports cleanly |
| empty the registry *and* `docs/guide/` | the non-empty assertion, not silent mutual agreement |

Row 5 is what justifies the `Supplier`. If the registry evaluated scenarios eagerly, a single broken
scenario would take down the directory guard too, and the failure output would describe the wrong
problem. Row 6 is what justifies the non-empty assertion: without it both directions agree perfectly
about nothing.

## Success criteria

- All six mutations above proven RED, then reverted.
- Full suite **572/572** on a clean build (571 + 1: one directory guard becomes two).
- `git diff --stat src/main` empty.
- The deleted-`@Test` defect from #48 is no longer expressible: coverage can only be removed by
  removing a `cases()` entry, which the directory guard rejects.

## Out of scope

Anything touching `docs/guide/` content, the guides' event tables, or the structures themselves.
This is a test-structure change that closes a known hole and nothing else.
