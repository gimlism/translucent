# PR 2 — StandardTrie as a first-class structure: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Date:** 2026-08-15
**Spec:** `planning/specs/2026-08-11-standard-trie-web-arc-design.md` (approved)
**Base:** `57470fd` (merge of PR #53 — PR 1, the rename). Branch off that commit.
**Suite at base:** 573/573.

**Goal:** Make `StandardTrie` a first-class fifth structure — six demos, a sixth hosted page, a fifth
guide — so a student can watch the one-node-per-character chain grow and the `Prune` cascade unwind
it, beside the radix trie that compresses it away.

**Architecture:** The visualisation stack is already implementation-agnostic (everything downstream
of the event stream is typed on `TrieEvent`). So this branch is **wiring, one decoupling and
hand-written surfaces**, not new rendering. Three enabling changes come first — a narrow
`PrefixMap<V>` capability interface so the REPL interpreter stops naming `RadixTrie`, and a
`STRUCTURE` template token so one shared `trie-viz.html` can title itself per trie — then six mirrored
demo classes, then the hand-written surfaces (Launcher, README, page list, guide) that cannot
self-heal.

**Tech Stack:** Java 21, Maven, JUnit 5 (`junit-jupiter`, incl. `@ParameterizedTest`/`@MethodSource`).
No new dependencies.

---

## Global Constraints

Copied from the spec. Every task's requirements implicitly include this section.

1. **Keep the public URLs.** `docs/viz/trie.html` and `docs/guide/trie.md` **keep their paths** — they
   are live public URLs since 2026-08-10 and GitHub Pages serving a committed `/docs` has no redirect
   mechanism. New paths are `docs/viz/standard-trie.html` and `docs/guide/standard-trie.md`.
2. **Full parity.** All six demo modes for the standard trie; the grid goes 4×6 → 5×6. An incomplete
   row is itself a defect.
3. **The standard demos mirror the radix scenario exactly**: `put shore, she, shell` then
   `remove shell, she`. The two replay pages must differ in exactly one variable — the structure.
4. **Launcher ordering is standard-first** (naive → optimised → measured): 19–24 `Trie (standard)`,
   25–30 `Trie (radix)`, 31 `Trie (compression)`. **This is decided** (spec §3.3, approved; the
   roadmap records "asked explicitly twice… treat as decided, do NOT re-litigate"). Do not re-ask.
5. **`DEFAULT_PORT` stays 7070** on all live demos — a student learns one port. The consequence (the
   radix and standard live demos cannot run concurrently, because `main` ignores `args`) is a
   documented known limitation, not a bug to fix here.
6. **Prose is rewritten, never copied.** The radix demos narrate *"edges split and branch"* and
   *"single-child edges merge"* — both actively false for a standard trie.
7. **Never** call `WebVizTemplate.inject(...)` and then `.replace(STRUCTURE_TOKEN, name)` on the
   result. That substitutes into a string already carrying user frame data and reintroduces the
   map-side ordering bug `WebVizTemplate`'s Javadoc records as fixed. Fixed flags first, user data
   last, inside `WebVizTemplate`.
8. **`mvn clean test`** — `clean` is mandatory after a branch switch (recorded stale-bytecode
   variant). The IDE/JDT language server throws phantom project-wide errors after bulk changes;
   **mvn is the source of truth.**
9. **Record the test count after every task.** It is this repo's primary overreach detector. Base is
   573; each task below states its expected delta and the implementer records the actual.
10. Commit messages follow the repo's existing conventional-commit style (`feat:`, `test:`, `docs:`,
    `refactor:`).

---

## File structure

**New production files**

| File | Responsibility |
|---|---|
| `src/main/java/com/gimlism/translucent/trie/core/PrefixMap.java` | The one capability the REPL needs beyond `Map` — `keysWithPrefix`. Not a shared-implementation extract. |
| `src/main/java/com/gimlism/translucent/trie/demo/StandardTrieDemo.java` | text log |
| `…/demo/StandardTrieVizDemo.java` | ASCII replay |
| `…/demo/StandardTrieWebVizDemo.java` | web replay + `public static String buildHtml()` (the generator `RegenerateDocs` bakes) |
| `…/demo/StandardTrieLiveWebVizDemo.java` | live web |
| `…/demo/StandardTrieLiveReplDemo.java` | terminal REPL |
| `…/demo/StandardTrieLiveControlsDemo.java` | browser REPL |
| `docs/guide/standard-trie.md` | the fifth guide — method → events, with the contrast explicit |
| `docs/viz/standard-trie.html` | **generated**, never hand-edited |

**New test files** — one per demo that has a testable seam, mirroring the radix five:
`StandardTrieDemoTest`, `StandardTrieVizDemoTest`, `StandardTrieWebVizDemoTest`,
`StandardTrieLiveReplDemoTest`, `StandardTrieLiveControlsEndToEndTest`.

**Modified production files**

| File | Change |
|---|---|
| `trie/core/RadixTrie.java:39` | `implements PrefixMap<V>` — one line, no bodies move |
| `trie/core/StandardTrie.java:38` | `implements PrefixMap<V>` — one line, no bodies move |
| `trie/repl/TrieCommandInterpreter.java:25,88,120` | `execute(String, PrefixMap<Integer>)`; `helpText(PrefixMap<?>)` derives the structure name |
| `substrate/viz/WebVizTemplate.java` | new `STRUCTURE_TOKEN` + `injectNamed(...)` |
| `src/main/resources/web/trie-viz.html:6,43` | `<title>`/`<h1>` carry the token |
| `trie/viz/TrieWebExporter.java` | **all four** entry points gain a `structure` parameter |
| `trie/demo/RadixTrie{WebViz,LiveWebViz,LiveRepl,LiveControls}Demo.java` | pass `"RadixTrie"` |
| `RegenerateDocs.java` | import + sixth `Page` (before the radix entry); radix `Page` title `"Trie"` → `"Radix trie"` |
| `Launcher.java:42–72` | six `Trie (standard)` entries **before** the radix block |
| `README.md` | prose line 5, hosted-links table, new demo section, renumbering |
| `docs/index.html` | **generated** |

**Modified test files:** `TrieCommandInterpreterTest`, `WebVizTemplateTest`, `TrieWebExporterTest`,
`TrieWebExporterLiveTest`, `TrieWebExporterControlsTest`, `TrieLiveVizEndToEndTest`,
`RadixTrieLiveControlsEndToEndTest`, `LauncherCatalogTest`, `LauncherMenuTest`, `GuideEventMapTest`.

---

## Two decisions this plan makes that the spec left open

Both are recorded here so a reviewer does not read them as oversights.

**A. The `STRUCTURE` token stays JS-comment shaped: `/*__STRUCTURE__*/`.** The repo's stated
convention (`WebVizTemplate.injectToken`'s Javadoc, and `SiteIndex.PAGES_TOKEN = "<!--__PAGES__-->"`)
is that a replacement landing *in markup* uses an HTML-comment token. The `STRUCTURE` token does land
in markup — `<title>` and `<h1>` — so that convention appears to apply, and it does not, for one
decisive reason: **`<title>` is an RCDATA element.** Its content model is text; markup, including
comments, is not parsed inside it. `<title><!--__STRUCTURE__--> — replay</title>` would render the
comment *literally in the browser tab* if it ever survived. Neither form vanishes on failure, so the
tiebreaker is guard coverage: `/*__` is already asserted absent by
`RadixTrieWebVizDemoTest:18`, `TrieWebExporterTest`, `TrieWebExporterLiveTest` and
`TrieWebExporterControlsTest`, so the JS-comment form inherits every existing surviving-token guard
for free. Task 2 additionally makes the *missing*-token direction throw, via `require()`.

**B. `runRepl` and `commandHandler` keep their concrete trie types.** `RadixTrieLiveReplDemo.runRepl`
takes `RadixTrie<Integer>`; the new `StandardTrieLiveReplDemo.runRepl` takes `StandardTrie<Integer>`.
Same for `commandHandler`. They are **not** reseated to `PrefixMap`, and the two loops are not merged.
This is deliberate: #41's whole-branch reviewer ruled the ~70% plumbing parallel between the two tries
to be *justified duplication for a teaching library* — each structure must read standalone — and that
ruling stands. `TrieCommandInterpreter` is reseated because it is a shared component with one
implementation, not a per-structure teaching surface.

---

## Task 1: `PrefixMap<V>` and the interpreter reseat

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/core/PrefixMap.java`
- Modify: `src/main/java/com/gimlism/translucent/trie/core/RadixTrie.java:39`
- Modify: `src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java:38`
- Modify: `src/main/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreter.java:9,25,88,120–122`
- Test: `src/test/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreterTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `public interface PrefixMap<V> extends Map<String, V> { List<String> keysWithPrefix(String prefix); }`
    in package `com.gimlism.translucent.trie.core`.
  - `public CommandResult TrieCommandInterpreter.execute(String line, PrefixMap<Integer> trie)`
  - `public String TrieCommandInterpreter.helpText(PrefixMap<?> trie)` — the old no-arg `helpText()`
    is **gone**, not overloaded.

**Expected test-count delta: +2** (one `@ParameterizedTest` with two invocations).

- [ ] **Step 1: Write the failing test**

Add to `TrieCommandInterpreterTest` (and add the imports listed under the snippet):

```java
    /**
     * The help line's structure name is derived from the trie it was handed, not hardcoded. Nothing
     * pinned that before: helpListsEveryCommandWord asserts only that each command WORD appears, so
     * a help text naming the wrong trie would ship green. Parameterised over both implementations
     * because a derivation that is wrong for one and right for the other is the failure shape.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("bothTries")
    void helpNamesTheTrieItWasHanded(String expectedName, PrefixMap<Integer> trie) {
        String help = interp.execute("help", trie).message();
        assertTrue(help.startsWith(expectedName + " live REPL"),
                "help must name the structure it was handed; got: " + help.lines().findFirst().orElse(""));
    }

    static Stream<Arguments> bothTries() {
        return Stream.of(
                Arguments.of("RadixTrie", new RadixTrie<Integer>()),
                Arguments.of("StandardTrie", new StandardTrie<Integer>()));
    }
```

New imports for that file:

```java
import com.gimlism.translucent.trie.core.PrefixMap;
import com.gimlism.translucent.trie.core.StandardTrie;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q clean test -Dtest=TrieCommandInterpreterTest`
Expected: **compile failure** — `PrefixMap` does not exist, and `execute(String, StandardTrie)` has no
matching method. In Java a missing type is the RED for a new-interface step; that is this step's red,
and it must be observed before writing any production code.

- [ ] **Step 3: Create `PrefixMap`**

```java
package com.gimlism.translucent.trie.core;

import java.util.List;
import java.util.Map;

/**
 * The one capability a trie offers beyond {@link Map}: listing every key under a prefix. Both tries
 * already declare exactly this method, so implementing it moves no code — the interface simply names
 * what {@code TrieCommandInterpreter} needs, so the interpreter can drive either trie without naming
 * one of them.
 *
 * <p><b>This is deliberately NOT a shared-implementation extract.</b> PR #41's whole-branch review
 * ruled the {@code AbstractTrie} transport extract not actionable: the ~70% plumbing parallel between
 * {@link RadixTrie} and {@link StandardTrie} is justified duplication for a teaching library, because
 * each structure must read standalone and their node types differ fundamentally. That ruling stands
 * and both tries keep every line they have. Without this note, a reader (or a Copilot pass) sees an
 * interface over two tries and reasonably concludes the ruled-out extract was started and abandoned.
 *
 * <p>Note also that this is the repo's first <i>structure-capability</i> interface — every other
 * interface here ({@code TrieEventListener}, {@code StructureEventListener}, {@code EventRenderer})
 * is an observer interface, and the other three command interpreters take a concrete structure type.
 * The deviation is justified by tries being the only structure with two implementations.
 */
public interface PrefixMap<V> extends Map<String, V> {

    /** Every key beginning with {@code prefix}, or every key when {@code prefix} is empty. */
    List<String> keysWithPrefix(String prefix);
}
```

- [ ] **Step 4: Declare both tries as `PrefixMap`**

`RadixTrie.java:39` — `public class RadixTrie<V> extends AbstractMap<String, V> {`
becomes:

```java
public class RadixTrie<V> extends AbstractMap<String, V> implements PrefixMap<V> {
```

`StandardTrie.java:38` — `public class StandardTrie<V> extends AbstractMap<String, V> {`
becomes:

```java
public class StandardTrie<V> extends AbstractMap<String, V> implements PrefixMap<V> {
```

No import is needed in either — `PrefixMap` is in the same package. No method bodies move: both
already declare `public List<String> keysWithPrefix(String)` (`RadixTrie:269`, `StandardTrie:217`),
and the interpreter's other five operations (`put`, `remove`, `get`, `containsKey`, `size`) are `Map`
methods inherited from `AbstractMap<String,V>`.

- [ ] **Step 5: Reseat the interpreter**

In `TrieCommandInterpreter.java`, replace the import on line 4:

```java
import com.gimlism.translucent.trie.core.PrefixMap;
```

Line 25 signature:

```java
    /** Parse {@code line}, apply it to {@code trie}, and return the result to show the user. */
    public CommandResult execute(String line, PrefixMap<Integer> trie) {
```

Line 88, inside `case "help"`:

```java
            case "help":
                return CommandResult.of(helpText(trie));
```

Lines 119–122, the help text:

```java
    /**
     * One-line overview plus the grammar, one line per command. The structure name is derived from
     * {@code trie} rather than hardcoded, so it follows a class rename and cannot name the wrong trie
     * once two implementations share this interpreter — the alternative, a hand-written name per
     * demo, is a copy of a string that can drift from the class it describes. Takes the trie rather
     * than being no-arg (unlike the three sibling interpreters) because {@code case "help"} is its
     * only caller and already holds one.
     */
    public String helpText(PrefixMap<?> trie) {
        return String.join("\n",
                trie.getClass().getSimpleName()
                        + " live REPL — type commands; mutations render live in the browser.",
```

(the remaining eight grammar lines are unchanged).

Also update the class Javadoc's first line, which currently names `RadixTrie`:

```java
 * Parses one command line and applies it to a {@link PrefixMap} — either trie implementation —
 * returning a {@link CommandResult}.
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q clean test -Dtest=TrieCommandInterpreterTest`
Expected: PASS. Every existing test in the class still compiles unchanged, because its `trie` field is
a `RadixTrie<Integer>` and `RadixTrie` is now a `PrefixMap<Integer>`.

- [ ] **Step 7: Run the whole suite**

Run: `mvn clean test`
Expected: **575/575** (573 + 2). Record the actual. Any other number means this task reached further
than intended — investigate before committing.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/core/PrefixMap.java \
        src/main/java/com/gimlism/translucent/trie/core/RadixTrie.java \
        src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java \
        src/main/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreter.java \
        src/test/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreterTest.java
git commit -m "feat(trie): add PrefixMap so the REPL interpreter drives either trie"
```

---

## Task 2: the `STRUCTURE` template token

**Files:**
- Modify: `src/main/resources/web/trie-viz.html:6,43`
- Modify: `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java`
- Modify: `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java` (**all four** entry points)
- Modify: `src/main/java/com/gimlism/translucent/trie/demo/RadixTrieWebVizDemo.java:49`
- Modify: `…/demo/RadixTrieLiveWebVizDemo.java:23`, `…/demo/RadixTrieLiveReplDemo.java:28`,
  `…/demo/RadixTrieLiveControlsDemo.java:28`
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java`
- Test (call-site updates): `…/trie/viz/TrieWebExporterTest.java:18,38,39`,
  `…/trie/viz/TrieWebExporterLiveTest.java:13,25`, `…/trie/viz/TrieWebExporterControlsTest.java:11,19,26,35`,
  `…/trie/viz/TrieLiveVizEndToEndTest.java:25`, `…/trie/demo/RadixTrieLiveControlsEndToEndTest.java:32`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `public static String WebVizTemplate.injectNamed(String resource, String framesReplacement, String liveReplacement, String controlsReplacement, String structureReplacement)`
  - `public static String TrieWebExporter.toHtml(String framesJson, String structure)`
  - `public static String TrieWebExporter.liveHtml(String structure)`
  - `public static String TrieWebExporter.controlsHtml(String structure)`
  - `public static void TrieWebExporter.writeHtml(String framesJson, String structure, Path out)`
  - No overloads and no defaults are kept: a shared exporter silently defaulting to one
    implementation is the ambiguity this arc removes, and every call site must state which trie it is
    exporting.

**The free proof this task rests on:** filling the new token with the identical string `"RadixTrie"`
must leave `docs/viz/trie.html` **byte-for-byte unchanged**, which `DocsPagesGoldenTest` already
asserts against the committed public file. A green golden *without regeneration* is therefore positive
evidence the extraction preserved behaviour, not merely an absence of complaints.

**Expected test-count delta: +2** (the two `WebVizTemplateTest` cases in Step 1).

- [ ] **Step 1: Write the failing tests**

Add to `WebVizTemplateTest`:

```java
    @Test
    void injectNamedSubstitutesTheStructureNameAndTheThreeStandardTokens() {
        String html = WebVizTemplate.injectNamed("/web/trie-viz.html", "{\"frames\":[]}",
                "false", "false", "StandardTrie");

        assertTrue(html.contains("<title>StandardTrie — replay</title>"), "the page title names the trie");
        assertTrue(html.contains("<h1>StandardTrie — web replay</h1>"), "the heading names the trie");
        assertFalse(html.contains("/*__"), "no template token may survive");
    }

    /**
     * The other direction. Only trie-viz.html carries a STRUCTURE token, so injectNamed must reject a
     * template that lacks one rather than returning a page silently missing its name — the same
     * contract inject() already enforces for FRAMES/LIVE/CONTROLS.
     */
    @Test
    void injectNamedRejectsATemplateWithNoStructureToken() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.injectNamed("/web/map-viz.html", "{\"frames\":[]}",
                        "false", "false", "TeachingHashMap"));
        assertTrue(e.getMessage().contains("__STRUCTURE__"), e.getMessage());
    }
```

No new imports are needed — `assertFalse`, `assertThrows` and `assertTrue` are already statically
imported in that file. Both cases deliberately use the **real** templates rather than the class's
`/web/webviztemplate-test.html` fixture: `trie-viz.html` is the only template that will carry a
STRUCTURE token, and `/web/map-viz.html` (confirmed present) is a real template that must never carry
one, so the pair pins the actual contract rather than a fixture's.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q clean test -Dtest=WebVizTemplateTest`
Expected: compile failure — `injectNamed` does not exist.

- [ ] **Step 3: Put the token in the template**

`src/main/resources/web/trie-viz.html` line 6:

```html
<title>/*__STRUCTURE__*/ — replay</title>
```

line 43:

```html
  <h1>/*__STRUCTURE__*/ — web replay</h1>
```

Change **nothing else** in that file. (On the token's shape, see "Decision A" above: `<title>` is an
RCDATA element, so an HTML-comment token would render literally in the browser tab; the JS-comment
form also inherits the suite's existing `assertFalse(html.contains("/*__"))` guards.)

- [ ] **Step 4: Add `injectNamed` to `WebVizTemplate`**

Add the constant beside the other three:

```java
    private static final String STRUCTURE_TOKEN = "/*__STRUCTURE__*/";
```

and the method, after `inject`:

```java
    /**
     * As {@link #inject}, plus a {@code STRUCTURE} token naming the structure the page depicts, for a
     * template shared by more than one implementation. Substitutes STRUCTURE, LIVE and CONTROLS —
     * all fixed, caller-chosen strings — and the user-controlled {@code FRAMES} blob LAST, preserving
     * the ordering invariant this class exists to centralise.
     *
     * <p>Deliberately a distinct name rather than a five-argument overload of {@link #inject}: two
     * same-typed positional overloads differing only in arity is a call-site trap.
     *
     * <p><b>Only {@code /web/trie-viz.html} carries the STRUCTURE token.</b> The map, list, treeset
     * and compression-compare templates do not, because each depicts exactly one implementation. A
     * later "unification" of {@link #inject} and this method would therefore make {@code require()}
     * throw for those four pages.
     */
    public static String injectNamed(String resource, String framesReplacement,
            String liveReplacement, String controlsReplacement, String structureReplacement) {
        String template = read(resource);
        require(template, resource, FRAMES_TOKEN);
        require(template, resource, LIVE_TOKEN);
        require(template, resource, CONTROLS_TOKEN);
        require(template, resource, STRUCTURE_TOKEN);
        return template
                .replace(STRUCTURE_TOKEN, structureReplacement)
                .replace(LIVE_TOKEN, liveReplacement)
                .replace(CONTROLS_TOKEN, controlsReplacement)
                .replace(FRAMES_TOKEN, framesReplacement); // user data LAST
    }
```

`inject` itself is **not** changed — the other four templates still use it.

- [ ] **Step 5: Run the template tests to verify they pass**

Run: `mvn -q clean test -Dtest=WebVizTemplateTest`
Expected: PASS.

- [ ] **Step 6: Widen all four `TrieWebExporter` entry points**

```java
    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson, String structure) {
        return WebVizTemplate.injectNamed(TEMPLATE_RESOURCE, framesJson, "false", "false", structure);
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml(String structure) {
        return WebVizTemplate.injectNamed(TEMPLATE_RESOURCE, "null", "true", "false", structure);
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml(String structure) {
        return WebVizTemplate.injectNamed(TEMPLATE_RESOURCE, "null", "true", "true", structure);
    }

    /** Write {@link #toHtml(String, String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, String structure, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson, structure));
    }
```

Update the class Javadoc: it currently says "Assembles the self-contained RadixTrie HTML page…".
Replace with wording that says it serves **both** tries and that the caller names which:

```java
/**
 * Assembles a self-contained trie HTML page from {@code /web/trie-viz.html} by delegating token
 * substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link TrieJsonSerializer}), the {@code LIVE} and {@code CONTROLS} mode flags, and the
 * {@code STRUCTURE} name shown in the page title and heading. The template is shared by both trie
 * implementations — the event stream is typed on {@code TrieEvent}, not on the trie that emitted it —
 * so every entry point takes the structure name explicitly and none defaults: a shared exporter
 * quietly labelling every page with one implementation is precisely the ambiguity this page set
 * removes.
 */
```

- [ ] **Step 7: Update every call site**

Production (4):

| File:line | New call |
|---|---|
| `RadixTrieWebVizDemo.java:49` | `TrieWebExporter.toHtml(TrieJsonSerializer.toJson(rec.events()), "RadixTrie")` |
| `RadixTrieLiveWebVizDemo.java:23` | `new LiveServer(TrieWebExporter.liveHtml("RadixTrie"), "127.0.0.1", DEFAULT_PORT)` |
| `RadixTrieLiveReplDemo.java:28` | `new LiveServer(TrieWebExporter.liveHtml("RadixTrie"), "127.0.0.1", DEFAULT_PORT)` |
| `RadixTrieLiveControlsDemo.java:28` | `new LiveServer(TrieWebExporter.controlsHtml("RadixTrie"), "127.0.0.1", DEFAULT_PORT, handler)` |

The name is a literal at each call site rather than `trie.getClass().getSimpleName()`: the three live
demos construct their `LiveServer` before their trie, deriving it would force a reordering for no
gain, and Task 3's event-stream guard — not this string — is what catches a demo whose page label and
actual structure disagree.

Tests (11 occurrences across 5 files) — pass `"RadixTrie"` at each, except
`TrieWebExporterTest:38–39` where `writeHtml` gains the argument in the middle position:

```java
        TrieWebExporter.writeHtml(frames, "RadixTrie", out);
        assertEquals(TrieWebExporter.toHtml(frames, "RadixTrie"), Files.readString(out));
```

Find them all with:

```bash
grep -rn "TrieWebExporter\." src/main src/test | grep -v Compression
```

Expected: **15** lines, all updated — 4 in `src/main` and 11 in `src/test`, matching the two tables
above. (`CompressionCompareWebExporter` is untouched — it uses `injectStatic` and has its own
template.)

- [ ] **Step 8: Run the whole suite and check the golden did not move**

Run: `mvn clean test`
Expected: **577/577** (575 + 2). Then, the proof:

```bash
git diff --stat -- docs/
```

Expected: **empty**. `DocsPagesGoldenTest`'s six cases passed **without regeneration**, which means
`docs/viz/trie.html` is byte-identical after the extraction. If it is not empty, the extraction
changed output — stop and find out why rather than regenerating.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/web/trie-viz.html \
        src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java \
        src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java \
        src/main/java/com/gimlism/translucent/trie/demo/ \
        src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java \
        src/test/java/com/gimlism/translucent/trie/viz/ \
        src/test/java/com/gimlism/translucent/trie/demo/
git commit -m "refactor(viz): name the structure in the shared trie page template"
```

---

## Task 3: the six `StandardTrie*Demo` classes

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/demo/StandardTrieDemo.java`
- Create: `…/StandardTrieVizDemo.java`, `…/StandardTrieWebVizDemo.java`,
  `…/StandardTrieLiveWebVizDemo.java`, `…/StandardTrieLiveReplDemo.java`,
  `…/StandardTrieLiveControlsDemo.java`
- Test: `src/test/java/com/gimlism/translucent/trie/demo/StandardTrieDemoTest.java`,
  `…/StandardTrieVizDemoTest.java`, `…/StandardTrieWebVizDemoTest.java`,
  `…/StandardTrieLiveReplDemoTest.java`, `…/StandardTrieLiveControlsEndToEndTest.java`

**Interfaces:**
- Consumes: `TrieCommandInterpreter.execute(String, PrefixMap<Integer>)` (Task 1);
  `TrieWebExporter.{toHtml(String,String), liveHtml(String), controlsHtml(String)}` (Task 2).
- Produces:
  - `public static String StandardTrieWebVizDemo.buildHtml()` — **public**, matching the widening #47
    applied to the other four `WebVizDemo`s, because `RegenerateDocs` (Task 5a) bakes it and
    `DocsPagesGoldenTest` pins the committed bytes to it.
  - `static void StandardTrieDemo.run(PrintStream out)` and `static void StandardTrieVizDemo.run(PrintStream out)`
  - `static void StandardTrieLiveReplDemo.runRepl(BufferedReader in, PrintStream out, StandardTrie<Integer> trie, TrieCommandInterpreter interpreter)`
  - `static Function<String, String> StandardTrieLiveControlsDemo.commandHandler(StandardTrie<Integer> trie, TrieCommandInterpreter interpreter)`

**The guard that matters here.** Six near-identical classes is this branch's highest-risk shape, and
the highest-probability defect is a `StandardTrie*Demo` that still constructs `new RadixTrie<>()`.
Asserting the page says "StandardTrie" does **not** catch it: that yields a page *titled* StandardTrie
holding a *radix* replay — title assertion green, and the Task 5a golden green too, because
regenerating and committing makes generated and committed bytes agree with each other regardless of
which structure produced them. **The discriminating assertion is on the event stream:** `CreateNode`
present, `SplitEdge` and `MergeEdge` absent. That is exactly the property distinguishing the two
tries, it is already baked into the JSON as each frame's `"type"`, and no golden can see it.

**Expected test-count delta: +8** — the `@Test` counts of the radix five this task mirrors:
`RadixTrieDemoTest` 1, `RadixTrieVizDemoTest` 1, `RadixTrieWebVizDemoTest` 1,
`RadixTrieLiveReplDemoTest` 3, `RadixTrieLiveControlsEndToEndTest` 2.

- [ ] **Step 1: Write the failing test for the text-log demo**

`src/test/java/com/gimlism/translucent/trie/demo/StandardTrieDemoTest.java`:

```java
package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StandardTrieDemoTest {

    /**
     * The absences are the point: a standard trie never splits or merges an edge, so a demo that
     * quietly constructed a RadixTrie would still print CREATE/DESCEND/PUT/REMOVE/PRUNE and pass a
     * presence-only check. SPLIT and MERGE are what separate the two implementations.
     */
    @Test
    void runShowsTheChainGrowingAndPruningWithNoSplitOrMerge() {
        var buffer = new ByteArrayOutputStream();
        StandardTrieDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CREATE"), out);
        assertTrue(out.contains("DESCEND"), out);
        assertTrue(out.contains("PUT"), out);
        assertTrue(out.contains("REMOVE"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertFalse(out.contains("SPLIT"), "a standard trie never splits an edge: " + out);
        assertFalse(out.contains("MERGE"), "a standard trie never merges an edge: " + out);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q clean test -Dtest=StandardTrieDemoTest`
Expected: compile failure — `StandardTrieDemo` does not exist.

- [ ] **Step 3: Write `StandardTrieDemo`**

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.ConsoleTrieEventLogger;
import com.gimlism.translucent.trie.core.StandardTrie;
import java.io.PrintStream;

/**
 * Scripted demonstration of the teaching standard-trie event stream — the same keys, in the same
 * order, as {@link RadixTrieDemo}, so the only difference you see is the structure itself.
 */
public class StandardTrieDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var trie = new StandardTrie<Integer>();
        trie.addListener(new ConsoleTrieEventLogger(out));

        out.println("== inserting keys with shared prefixes (watch one node appear per character) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) { // create a chain, then descend it
            trie.put(key, v++);
        }

        out.println("== removing keys (watch the prune cascade unwind the chain) ==");
        for (String key : new String[]{"shell", "she"}) { // prune back to the last surviving branch
            trie.remove(key);
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -q clean test -Dtest=StandardTrieDemoTest`
Expected: PASS.

- [ ] **Step 5: Write the failing test for the ASCII demo**

`StandardTrieVizDemoTest.java` — same package and imports as Step 1, plus `assertFalse`:

```java
class StandardTrieVizDemoTest {
    @Test
    void runRendersLabelledTreeFramesWithCreatePruneAndHighlight() {
        var buf = new ByteArrayOutputStream();
        StandardTrieVizDemo.run(new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CREATE"), out);
        assertTrue(out.contains("DESCEND"), out);
        assertTrue(out.contains("PUT"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertTrue(out.contains("(root)"), out);   // a rendered tree frame
        assertTrue(out.contains("> "), out);        // a path highlight
        assertFalse(out.contains("SPLIT"), out);
        assertFalse(out.contains("MERGE"), out);
    }
}
```

- [ ] **Step 6: Run it (fails), write `StandardTrieVizDemo`, run it (passes)**

Run first: `mvn -q clean test -Dtest=StandardTrieVizDemoTest` → compile failure.

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.viz.AsciiTrieVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link StandardTrieDemo}, rendered as live ASCII trees instead of a text
 * log: every event prints the indented N-ary trie with the affected node (by path) highlighted. Run
 * it beside {@link RadixTrieVizDemo} — same keys, same order — and the depth difference is the whole
 * argument for compression.
 */
public class StandardTrieVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var trie = new StandardTrie<Integer>();
        trie.addListener(new AsciiTrieVisualizer(out));

        out.println("== inserting keys with shared prefixes (watch one node appear per character) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) {
            trie.put(key, v++);
        }

        out.println("== removing keys (watch the walk, then the prune cascade unwind the chain) ==");
        for (String key : new String[]{"shell", "she"}) {
            trie.remove(key);
        }
    }
}
```

Run again: PASS.

- [ ] **Step 7: Write the failing test for the web demo — the discriminating guard**

`StandardTrieWebVizDemoTest.java`:

```java
package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandardTrieWebVizDemoTest {

    /**
     * The title assertions below catch a mislabelled page; the EVENT assertions catch the defect a
     * golden provably cannot see. A StandardTrie demo that still constructed a RadixTrie would ship a
     * page titled "StandardTrie" holding a radix replay, and regenerating would make the committed
     * bytes agree with the generated bytes either way. SplitEdge/MergeEdge absence is the property
     * that actually separates the two implementations.
     */
    @Test
    void buildHtmlIsSelfContainedAndReplaysAStandardTrieNotARadixOne() {
        String html = StandardTrieWebVizDemo.buildHtml();

        // self-contained baked document, no unsubstituted tokens
        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        assertTrue(html.contains("\"frames\":["), "expected an injected frames blob");
        assertFalse(html.contains("/*__"), "no template token should survive");

        // the page names the structure it depicts
        assertTrue(html.contains("<title>StandardTrie — replay</title>"), "expected the StandardTrie title");
        assertFalse(html.contains("RadixTrie"), "the standard page must not name the radix trie");

        // the story ran and emitted the standard trie's vocabulary
        assertTrue(html.contains("\"type\":\"CreateNode\""), "expected one node created per character");
        assertTrue(html.contains("\"type\":\"Descend\""), "expected a walk down the chain");
        assertTrue(html.contains("\"type\":\"Put\""), "expected inserts");
        assertTrue(html.contains("\"type\":\"Remove\""), "expected removes");
        assertTrue(html.contains("\"type\":\"Prune\""), "expected the prune cascade");

        // and NOT the radix trie's — this is the assertion that discriminates
        assertFalse(html.contains("\"type\":\"SplitEdge\""), "a standard trie never splits an edge");
        assertFalse(html.contains("\"type\":\"MergeEdge\""), "a standard trie never merges an edge");

        // every edge label is a single character — the defining shape of an uncompressed trie
        assertFalse(html.contains("\"label\":\"sh\""), "labels are one character, never a compressed run");
    }
}
```

- [ ] **Step 8: Run it (fails), write `StandardTrieWebVizDemo`, run it (passes)**

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.viz.TrieJsonSerializer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records the same story as {@link RadixTrieWebVizDemo} — the same keys in the same order — against
 * an uncompressed trie, and writes it out as a self-contained HTML web replay. Opened beside
 * {@code trie.html}, the two pages differ in exactly one variable, so every difference a student sees
 * is caused by compression and nothing else. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class StandardTrieWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/standard-trie-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /**
     * The self-contained HTML replay for the standard story, built without touching the filesystem.
     * Public because {@code RegenerateDocs} bakes it into {@code docs/viz/} and {@code
     * DocsPagesGoldenTest} pins the committed bytes to it — the seam has a production consumer now,
     * not just a test.
     */
    public static String buildHtml() {
        var trie = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);

        int v = 1;
        // insert keys sharing the "sh" prefix — one node appears per character, no edge is compressed
        for (String key : new String[] {"shore", "she", "shell"}) {
            trie.put(key, v++);
        }
        // remove keys — the prune cascade unwinds each dead chain a node at a time
        for (String key : new String[] {"shell", "she"}) {
            trie.remove(key);
        }

        return TrieWebExporter.toHtml(TrieJsonSerializer.toJson(rec.events()), "StandardTrie");
    }
}
```

- [ ] **Step 9: Write the three live demos**

These three have no new test of their own beyond Steps 10–11; write them together.

`StandardTrieLiveWebVizDemo.java`:

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;

/**
 * The student sandbox: a running {@link StandardTrie} whose every mutation renders live in the
 * browser. Write your own put/remove calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveWebVizDemo}
 * and watch the chain grow a node per character as your code runs. The server keeps running after
 * your code finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 *
 * <p><b>Known limitation:</b> every live demo in this repo binds {@value #DEFAULT_PORT} and none of
 * them parses {@code args}, so this demo and {@link RadixTrieLiveWebVizDemo} cannot run at the same
 * time — the second to start fails to bind. Run them one after the other, or compare the two static
 * replay pages ({@code docs/viz/standard-trie.html} and {@code docs/viz/trie.html}), which are plain
 * files and open side by side.
 */
public class StandardTrieLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml("StandardTrie"), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var trie = new StandardTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        int v = 1;
        for (String key : new String[] {"shore", "she", "shell"}) { // one node per character
            trie.put(key, v++);
        }
        for (String key : new String[] {"shell", "she"}) { // the prune cascade unwinds the chain
            trie.remove(key);
        }
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
```

`StandardTrieLiveReplDemo.java` — identical in shape to `RadixTrieLiveReplDemo`, with
`StandardTrie<Integer>` throughout (see Decision B: `runRepl` keeps the concrete type):

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link StandardTrie}: type commands (see {@code help}) and
 * watch each mutation render live in the browser — no recompile per change. The same
 * {@link TrieCommandInterpreter} drives both tries, so the grammar you learn here is the grammar
 * {@link RadixTrieLiveReplDemo} takes. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveReplDemo}
 * The trie starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 *
 * <p><b>Known limitation:</b> binds {@value #DEFAULT_PORT} and ignores {@code args}, as every live
 * demo here does, so it cannot run alongside another live demo.
 */
public class StandardTrieLiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml("StandardTrie"), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var trie = new StandardTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, trie, new TrieCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, StandardTrie<Integer> trie,
            TrieCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, trie);
            if (!r.message().isEmpty()) {
                out.println(r.message());
            }
            if (r.quit()) {
                return;
            }
        }
    }
}
```

`StandardTrieLiveControlsDemo.java`:

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live. The trie lives server-side; the browser is the REPL over HTTP,
 * running the SAME {@link TrieCommandInterpreter} as {@link StandardTrieLiveReplDemo}. No stdin. Run
 * with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveControlsDemo}
 * The trie starts empty; the server keeps running until Ctrl-C.
 *
 * <p><b>Known limitation:</b> binds {@value #DEFAULT_PORT} and ignores {@code args}, as every live
 * demo here does, so it cannot run alongside another live demo.
 */
public class StandardTrieLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var trie = new StandardTrie<Integer>();
        Function<String, String> handler = commandHandler(trie, new TrieCommandInterpreter());

        LiveServer server = new LiveServer(TrieWebExporter.controlsHtml("StandardTrie"), "127.0.0.1",
                DEFAULT_PORT, handler);
        server.start();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The trie-specific command seam (tested): applies each POSTed line to {@code trie} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link StandardTrie}. {@code quit}/{@code exit} return their message but do NOT
     * stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(StandardTrie<Integer> trie,
            TrieCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, trie).message();
            }
        };
    }
}
```

- [ ] **Step 10: Write `StandardTrieLiveReplDemoTest`**

Mirror `RadixTrieLiveReplDemoTest` exactly, substituting `StandardTrie` for `RadixTrie` and
`StandardTrieLiveReplDemo` for `RadixTrieLiveReplDemo`. All three of its cases
(`loopExecutesEachLineAndStopsOnQuit`, `loopEndsAtEofWithoutQuit`, `blankLinesProduceNoOutput`) carry
over unchanged — the interpreter's messages do not depend on which trie it drives.

- [ ] **Step 11: Write `StandardTrieLiveControlsEndToEndTest`**

Mirror `RadixTrieLiveControlsEndToEndTest` exactly, substituting `StandardTrie`,
`StandardTrieLiveControlsDemo` and `TrieWebExporter.controlsHtml("StandardTrie")`. Both cases
(`aPostedCommandMutatesTheTrieAndSurfacesAsALiveFrame`, `quitReturnsItsMessageWithoutStoppingOrMutating`)
carry over. The first put into an empty standard trie emits **three** leading `CreateNode` frames for
`"cat"` rather than the radix trie's one, so keep the existing loop that reads *past* leading frames
until the `Put` frame arrives — do not assert on the first frame.

- [ ] **Step 12: Run the whole suite**

Run: `mvn clean test`
Expected: **585/585** (577 + 8). Record the actual, and check `git diff --stat -- docs/` is still
**empty** — nothing in this task touches the generated site.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/demo/StandardTrie*.java \
        src/test/java/com/gimlism/translucent/trie/demo/StandardTrie*.java
git commit -m "feat(trie): six StandardTrie demos mirroring the radix scenario"
```

---

## Task 4: Launcher ordering and the README demo tables

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/Launcher.java:35–72`
- Modify: `README.md:5,106–123`
- Modify: `docs/guide/trie.md:59` — **the edit nothing in the suite would catch** (Step 6)
- Test: `src/test/java/com/gimlism/translucent/LauncherCatalogTest.java:43,44,53`
- Test: `src/test/java/com/gimlism/translucent/LauncherMenuTest.java:48`

**Interfaces:**
- Consumes: the six class names from Task 3.
- Produces: `Launcher.CATALOG` of size **31**, with structure labels in order
  `["ArrayList", "HashMap", "TreeSet", "Trie (standard)", "Trie (radix)", "Trie (compression)"]`.

**What this task deliberately does NOT do:** it does not add the `📖 [What each …]` guide link to the
new README section. That link and the guide file land together in Task 5b, so the README never points
at a file that does not exist. It also does not touch the hosted-links table (Task 5a).

**Expected test-count delta: 0.** Existing guards cover this; they change value, not count.

- [ ] **Step 1: Update the guards first (they are the failing test)**

`LauncherCatalogTest:43–46`:

```java
        assertEquals(31, Launcher.CATALOG.size(), "5 structures x 6 modes + compression-compare");
        for (String structure : List.of("ArrayList", "HashMap", "TreeSet",
                "Trie (standard)", "Trie (radix)")) {
```

`LauncherCatalogTest:53`:

```java
        assertEquals(List.of("ArrayList", "HashMap", "TreeSet", "Trie (standard)", "Trie (radix)",
                "Trie (compression)"), order);
```

`LauncherMenuTest:48`:

```java
        for (String s : List.of("ArrayList", "HashMap", "TreeSet", "Trie (standard)", "Trie (radix)")) {
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q clean test -Dtest='Launcher*Test'`
Expected: FAIL — `expected: <31> but was: <25>`, plus the order and heading assertions. This is the
RED that proves the guards actually pin the catalog rather than passing over whatever is there.

- [ ] **Step 3: Add the six entries to `Launcher.CATALOG`**

Insert **before** the existing `Trie (radix)` block (the `TRIE` package constant is shared — both
implementations live in `com.gimlism.translucent.trie.demo.`):

```java
            new Entry("Trie (standard)", "text log", TRIE + "StandardTrieDemo"),
            new Entry("Trie (standard)", "ASCII replay", TRIE + "StandardTrieVizDemo"),
            new Entry("Trie (standard)", "web replay (writes .html)", TRIE + "StandardTrieWebVizDemo"),
            new Entry("Trie (standard)", "live web", TRIE + "StandardTrieLiveWebVizDemo"),
            new Entry("Trie (standard)", "terminal REPL", TRIE + "StandardTrieLiveReplDemo"),
            new Entry("Trie (standard)", "browser REPL", TRIE + "StandardTrieLiveControlsDemo"),

```

Standard first is the teaching order the menu is explicitly built on: the standard trie is the naive
form, the radix trie is the optimisation of it, and the comparison is the measurement — naive →
optimised → measured. Extend the `CATALOG` Javadoc's rationale sentence (lines 37–41) to say so.

- [ ] **Step 4: Run the Launcher guards to verify they pass**

Run: `mvn -q clean test -Dtest='Launcher*Test'`
Expected: `LauncherCatalogTest` and `LauncherMenuTest` PASS; **`LauncherReadmeTest` still FAILS** —
the README table does not yet list the six new FQCNs. That is the next step.

- [ ] **Step 5: Update the README's "Every demo" section**

Insert a new section immediately **before** `### Trie (radix) …` (currently line 106):

```markdown
### Trie (standard) — one node per character, and a prune cascade when keys leave

| # | mode | |
| --- | --- | --- |
| 19 | text log | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieDemo` |
| 20 | ASCII replay | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieVizDemo` |
| 21 | web replay | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieWebVizDemo` |
| 22 | live web | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveWebVizDemo` |
| 23 | terminal REPL | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveReplDemo` |
| 24 | browser REPL | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveControlsDemo` |
```

⚠️ **The heading text above is load-bearing** — Task 5b anchors it from `docs/guide/standard-trie.md`
and `GuideReadmeAnchorTest` slugifies it. If you change the wording, recompute the anchor in Task 5b
Step 5 to match.

Then renumber the existing radix rows **19–24 → 25–30** and the compression row **25 → 31**. Only the
leading `| N |` cell changes on each; the FQCNs are untouched.

- [ ] **Step 6: Repair `docs/guide/trie.md:59` — the renumbering's silent casualty**

That line currently reads:

```markdown
- demos 19–24 in the [README](../../README.md#trie-radix--edges-split-and-merge-as-keys-arrive-and-leave)
```

Step 5 just moved the radix demos to 25–30, so it now points a student at the *standard* trie's six
rows. Change `19–24` to **`25–30`**; leave the anchor exactly as it is (the `### Trie (radix) …`
heading text is unchanged, so the link still resolves).

⚠️ **Nothing in the suite catches this.** `GuideReadmeAnchorTest` checks only that the anchor resolves
to a heading, and it does. `GuideEventMapTest` parses only the events table. `SiteIndexTest` checks
only that the file exists and is linked. This is exactly the hand-written-surface-points-at-nothing
shape that PR 1 shipped and then had to fix — a number in prose, pinned by nothing. Do not skip it
because the build stays green; the build staying green *is* the problem.

- [ ] **Step 7: Update README prose line 5**

Line 5 currently reads *"Four data structures — **ArrayList**, **HashMap**, **TreeSet** and a radix
**Trie** — implemented for teaching…"*. Replace the first sentence with:

```markdown
Five data structures — **ArrayList**, **HashMap**, **TreeSet**, and two tries: a **standard trie**
and the **radix trie** that compresses it — implemented for teaching, each narrating what it does as
it does it. The narration is emitted by the real
```

Leave the rest of that paragraph (from `` `put()`/`add()`/`remove()` paths, … ``) unchanged.

**Do not touch README line 30** (*"The same five pages are committed under `docs/viz/`"*) here. It
becomes "six" in Task 5a Step 4, in the same commit that actually writes the sixth file — this task
would ship a README claiming six committed pages while `docs/viz/` holds five, and no test reads
prose.

- [ ] **Step 8: Run the whole suite**

Run: `mvn clean test`
Expected: **585/585** — unchanged from Task 3. `LauncherReadmeTest` passes in both directions:
forward (every `CATALOG` FQCN appears in the README) and reverse (every
`com.gimlism.translucent.*` string in the README resolves to a `CATALOG` entry — which is why the
quick-start example at line 64 matters, though it names `RadixTrieVizDemo` and stays valid).

`GuideReadmeAnchorTest` also stays green: the new `### ` heading adds a slug to the reference set, and
`docs/guide/trie.md`'s existing anchor targets the unchanged `### Trie (radix) …` heading.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/gimlism/translucent/Launcher.java README.md docs/guide/trie.md \
        src/test/java/com/gimlism/translucent/LauncherCatalogTest.java \
        src/test/java/com/gimlism/translucent/LauncherMenuTest.java
git commit -m "feat(launcher): list the standard trie's six demos before the radix trie's"
```

---

## Task 5a: publish the sixth page

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/RegenerateDocs.java:9,76–93`
- Modify: `README.md` hosted-links table (lines 19–25) and the "same five pages" sentence if Task 4
  Step 6 left it
- Generated: `docs/viz/standard-trie.html` (new), `docs/index.html` (modified)

**Interfaces:**
- Consumes: `StandardTrieWebVizDemo::buildHtml` (Task 3).
- Produces: `RegenerateDocs.vizPages()` of size **6**, so `pages()` has **7** entries.

**Why the guide is `null` here and set in Task 5b.** These two tasks are split at the only seam where
both halves stay green. `SiteIndexTest.everyGuideIsLinkedFromTheIndex` scans `docs/guide/*.md` on
disk and requires the committed `docs/index.html` to link each one — so creating the guide file
before a `Page` carries it goes RED, and the reverse ordering is the only one that works.
`everyGuideNamesAFileThatExists` filters `page.guide() != null`, which is what makes a null guide a
legal intermediate state (`compression-compare` ships with one permanently).

**Expected test-count delta: +1** (`DocsPagesGoldenTest` is parameterised over `pages()`, so it goes
6 → 7 cases by itself).

- [ ] **Step 1: Run the golden to see it fail after the page list grows**

Add the import and the entry to `RegenerateDocs`:

```java
import com.gimlism.translucent.trie.demo.StandardTrieWebVizDemo;
```

and inside `vizPages()`, **before** the existing `viz/trie.html` entry:

```java
                new Page("viz/standard-trie.html", "Standard trie",
                        "One node per character — the chain a radix trie compresses away.",
                        null, StandardTrieWebVizDemo::buildHtml),
```

and change the radix entry's display title in the same edit:

```java
                new Page("viz/trie.html", "Radix trie",
                        "A radix trie whose edges split and merge as keys arrive and leave.",
                        "guide/trie.md", RadixTrieWebVizDemo::buildHtml),
```

The title change is required, not cosmetic: leaving one card labelled plain "Trie" beside a "Standard
trie" card reintroduces exactly the ambiguity this branch removes. It is display text on the index
card, not a path — decision 2 disambiguates everywhere *except* URLs.

- [ ] **Step 2: Run the suite to verify it fails**

Run: `mvn clean test`
Expected: FAIL, in three places, and each failure is a guard working:
1. `DocsPagesGoldenTest` — `docs/viz/standard-trie.html` does not exist yet (7th case), and
   `docs/index.html` no longer matches (a new card, plus the radix card's new title).
2. `SiteIndexTest.everyBakedVizPageIsLinkedFromTheIndex` — only after regeneration writes the file.
3. `ReadmeSiteLinksTest` — `readmeLinksEveryPublishedVisualisation` names the missing URL, and
   `everyPublishedPageIsLinkedExactlyOncePlusTheLandingPage` reports `expected <7> but was <6>`.

- [ ] **Step 3: Regenerate the site**

```bash
mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
git status --short docs/
```

Expected: exactly two paths changed — `docs/viz/standard-trie.html` (new) and `docs/index.html`
(modified). If any of `docs/viz/{list,map,treeset,trie,compression-compare}.html` changed, **stop**:
Task 2's byte-identity proof has been broken and the cause must be found, not regenerated away.

- [ ] **Step 4: Add the README hosted-links row**

In the table at README lines 19–25, insert before the `trie` row and relabel the `trie` row:

```markdown
| [standard-trie](https://gimlism.github.io/translucent/viz/standard-trie.html) | Trie (standard) |
| [trie](https://gimlism.github.io/translucent/viz/trie.html) | Trie (radix) |
```

Then change README line 30, *"The same five pages are committed under `docs/viz/`"*, to **six**. This
is the commit that writes the sixth file, so it is the only commit in which that sentence is true.

- [ ] **Step 5: Verify determinism, then run the whole suite**

```bash
mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
git status --short docs/
```

Expected: the second run leaves the tree exactly as the first did — no further modifications. (#47's
method: two consecutive generator runs must be byte-identical.)

Run: `mvn clean test`
Expected: **586/586** (585 + 1). Record the actual.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/RegenerateDocs.java README.md \
        docs/viz/standard-trie.html docs/index.html
git commit -m "feat(docs): bake the standard trie's replay page into the published site"
```

---

## Task 5b: the fifth guide

**Files:**
- Create: `docs/guide/standard-trie.md`
- Modify: `src/main/java/com/gimlism/translucent/RegenerateDocs.java` (the new `Page`'s `guide` field)
- Modify: `README.md` (the `📖` line under the Trie (standard) section)
- Test: `src/test/java/com/gimlism/translucent/GuideEventMapTest.java:172–178` + a new scenario
- Generated: `docs/index.html` (modified — the new card gains its guide link)

**Interfaces:**
- Consumes: the `Page` added in Task 5a; `StandardTrie` (unchanged since PR #41).
- Produces: `GuideEventMapTest.cases()` of size **5**.

**Expected test-count delta: +1** (`guideMatchesWhatTheStructureEmits` is parameterised over
`cases()`, so it goes 4 → 5 invocations).

**The forcing property, and why it constrains the table.** `assertGuideMatches` requires
`documented.keySet()` to equal `emitted.keySet()` **exactly** — so the scenario must call precisely
the `method/arity` keys the table documents and no others — and then compares each row's event-name
set against what actually fired. If a row names an event the scenario cannot reach, **change the
scenario, do not trim the table** (that rule fired three times for real in #48).

- [ ] **Step 1: Write the failing case**

In `GuideEventMapTest`, add the import:

```java
import com.gimlism.translucent.trie.core.StandardTrie;
```

extend `cases()`:

```java
    static Stream<GuideCase> cases() {
        return Stream.of(
                new GuideCase("list.md", TeachingArrayList.class, () -> listScenario().emitted()),
                new GuideCase("map.md", TeachingHashMap.class, () -> mapScenario().emitted()),
                new GuideCase("treeset.md", TeachingTreeSet.class, () -> treeSetScenario().emitted()),
                new GuideCase("trie.md", RadixTrie.class, () -> trieScenario().emitted()),
                new GuideCase("standard-trie.md", StandardTrie.class,
                        () -> standardTrieScenario().emitted()));
    }
```

and add the scenario beside `trieScenario()`:

```java
    /**
     * Deliberately the same keys as {@link #trieScenario()}: the two guides then differ only where
     * the structures do. An uncompressed trie reaches its whole vocabulary on this story — the first
     * put creates a chain from an empty root, later puts descend the shared "sh" prefix before
     * creating, and the removes prune back up it — while SplitEdge and MergeEdge remain unreachable,
     * which is the lesson standard-trie.md is written to teach.
     */
    private static Runner<TrieEvent> standardTrieScenario() {
        StandardTrie<Integer> trie = new StandardTrie<>();
        Runner<TrieEvent> runner = new Runner<>(trie::addListener);
        // Empty root: every character of "shell" is a fresh node, so this call is all CreateNode.
        runner.call("put/2", () -> trie.put("shell", 1));
        // "sh" already exists: Descend twice, then create the rest of the chain.
        runner.call("put/2", () -> trie.put("shore", 2));
        runner.call("put/2", () -> trie.put("shy", 3));
        runner.call("get/1", () -> trie.get("shell"));
        runner.call("containsKey/1", () -> trie.containsKey("shore"));
        // "shy"'s 'y' is a childless non-key leaf once unset, so exactly one Prune fires; "sh" keeps
        // two children, so the cascade stops there. Removing "shore" then prunes 'e','r','o' in turn.
        runner.call("remove/1", () -> trie.remove("shy"));
        runner.call("remove/1", () -> trie.remove("shore"));
        // Absent key -> remove bails out before narrating the buffered walk; unions into the same
        // tally as the removes above, so it can only shrink the documented set if it fires something.
        runner.call("remove/1", () -> trie.remove("nope"));
        return runner;
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q clean test -Dtest=GuideEventMapTest`
Expected: FAIL — `everyCaseNamesARegularGuideFile` reports
`docs/guide/standard-trie.md is named by a GuideCase but is not a regular file`.

- [ ] **Step 3: Write the guide**

`docs/guide/standard-trie.md`:

```markdown
# Standard trie

An uncompressed trie: one node per character, so `shell` is a chain of five nodes and no edge ever
carries more than a single letter. Compare `docs/guide/trie.md`, whose radix trie stores that same
chain as one edge.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `put(key, value)` | `Descend`×n → `CreateNode`×n → `Put` |
| `remove(key)` | `Descend`×n → `Remove`? → `Prune`×n |
| `get(key)` | nothing — reads are silent |
| `containsKey(key)` | nothing — reads are silent |

In the table above, `×n` means the event fires once per step and may not occur at all, while `?` marks conditionally-firing operations.

**There is no `SplitEdge` row and no `MergeEdge` row, and that absence is the lesson.** Those two
events exist because a radix trie stores runs of characters on one edge and must therefore break
edges apart and glue them back together; a standard trie stores one character per node, so there is
nothing to split and nothing to merge. Everything the compression buys — and everything it costs — is
the difference between these two tables.

`StandardTrie.put` consumes the key one character at a time. While the next character already has a
child, it moves down and emits `Descend`; the first character with no child ends the walking phase for
good, and the rest of the key is grown as a chain of fresh nodes, one `CreateNode` each. So every
`Descend` in a single `put` precedes every `CreateNode` in it, never the other way round — and a
`put` of a key already present is all `Descend` and no `CreateNode` at all. `Put` is always last,
whether the walk ended by exhausting the key against existing nodes or by growing new ones.

`StandardTrie.remove` buffers its walk and narrates it as `Descend` only once it knows the removal
will actually happen, then emits `Remove` before changing the tree's shape. What follows is the prune
cascade: starting at the node the key ended on and working back up towards the root, each node that
is now childless, no longer a key, and not the root is unhooked from its parent and emits one
`Prune`. The cascade stops at the first node that fails any of those tests — so removing a key whose
node still has children prunes nothing, and removing the last key down a long chain prunes once per
node the chain no longer needs. This is where the radix trie's `MergeEdge` has no counterpart: an
uncompressed trie does not tidy a surviving single-child chain, it simply keeps it.

Removing a key that isn't there fires nothing at all: if the walk falls off (a child is missing) or
lands on a node that exists but was never a key, `remove` returns before the buffered walk is ever
narrated — no `Descend`, no `Remove`, and so no `Prune` either. The same silence as an absent-key
`RadixTrie.remove` and `TeachingHashMap.remove`; `TeachingTreeSet` is the one structure that narrates
a failed write (see `docs/guide/treeset.md`).

`get` and `containsKey` both delegate to the same silent `find`, which walks children without
narrating any of them.

## Read alongside

- `src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java` — the algorithm
- `src/main/java/com/gimlism/translucent/trie/core/RadixTrie.java` — the compressed form of the same idea
- `src/main/java/com/gimlism/translucent/trie/events/` — the seven event types, of which this trie
  uses five

## Watch it

- `docs/viz/standard-trie.html` — open in a browser, no JDK needed
- `docs/viz/trie.html` — the same keys, stored as a radix trie
- `docs/viz/compression-compare.html` — both at once, with what the compression saves
- demos 19–24 in the [README](../../README.md#trie-standard--one-node-per-character-and-a-prune-cascade-when-keys-leave)
```

- [ ] **Step 4: Run the guide test to verify it passes**

Run: `mvn -q clean test -Dtest=GuideEventMapTest`
Expected: PASS, 5 parameterised invocations plus the two directory guards.

If a row disagrees, the failure message names the exact `method/arity` and both sets. Treat the
**emitted** set as the truth and the table as the claim to fix — unless the emitted set is missing an
event the table legitimately documents, in which case extend the scenario until it fires.

- [ ] **Step 5: Verify the README anchor — by hand and against GitHub's real renderer**

The link in the guide's last line anchors the README heading Task 4 added.
`GuideReadmeAnchorTest.slugify` lowercases, deletes every character that is not a word character,
hyphen or space, then turns spaces into hyphens. Applied to
`Trie (standard) — one node per character, and a prune cascade when keys leave`:

- lowercase → `trie (standard) — one node per character, and a prune cascade when keys leave`
- delete `(`, `)`, `—`, `,` → `trie standard  one node per character and a prune cascade when keys leave`
  (two spaces survive where `) ` and `— ` collapsed)
- spaces → hyphens → **`trie-standard--one-node-per-character-and-a-prune-cascade-when-keys-leave`**

The double hyphen is expected — it matches the shape of the existing, GitHub-verified radix anchor
`trie-radix--edges-split-and-merge-as-keys-arrive-and-leave`.

Now confirm against GitHub's actual slugger, because `GuideReadmeAnchorTest`'s own Javadoc records
that its oracle is *not* GitHub's and that the live verification "does not repeat automatically as
headings change" — this is the exact class of defect PR 1 created:

```bash
gh api -X POST /markdown -f mode=gfm \
  -f text='### Trie (standard) — one node per character, and a prune cascade when keys leave' \
  | grep -o 'id="user-content-[^"]*"'
```

Expected: `id="user-content-trie-standard--one-node-per-character-and-a-prune-cascade-when-keys-leave"`.
If it differs, **use GitHub's slug in the guide link** and note the divergence — the guard's slugifier
is a convenience, the live renderer is the contract.

- [ ] **Step 6: Add the README guide link and set the `Page`'s guide**

In `README.md`, under the Trie (standard) demo table (after row 24), add the line the other four
sections carry:

```markdown
📖 [What each standard-trie method makes you see](docs/guide/standard-trie.md)
```

In `RegenerateDocs.vizPages()`, set the new page's guide:

```java
                new Page("viz/standard-trie.html", "Standard trie",
                        "One node per character — the chain a radix trie compresses away.",
                        "guide/standard-trie.md", StandardTrieWebVizDemo::buildHtml),
```

- [ ] **Step 7: Regenerate and run the whole suite**

```bash
mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
git status --short docs/
```

Expected: only `docs/index.html` modified (the standard-trie card gains its guide link). If
`docs/viz/standard-trie.html` also changed, something in Task 3's generator moved — investigate.

Run: `mvn clean test`
Expected: **587/587** (586 + 1). Record the actual.

- [ ] **Step 8: Verify every conditional marker row-by-row against source**

This is a review step with no test behind it, and it is here because #48's structural lesson is that
the suite compares event **names** only — every ordering claim and every `?`/`×n` marker in the table
above is prose the build cannot check. Read `StandardTrie.put` (`:76–116`) and `StandardTrie.remove`
(`:119–161`) and confirm, one claim at a time:

- `put`: every `Descend` precedes every `CreateNode` (the inner `while` at `:87–95` runs to the end of
  the key and then `break`s out of the outer loop, so the walking phase cannot resume) — ✔/✘
- `put`: `Put` is unconditional and last (`:111`, outside every branch) — ✔/✘
- `put` of an existing key: no `CreateNode` (the outer loop finds a child at every character) — ✔/✘
- `remove` absent key: nothing at all (`:132` and `:138` both `return null` before `:140` narrates the
  buffered walk) — ✔/✘
- `remove`: `Remove` precedes every `Prune` (`:146` before the cascade at `:149–156`) — ✔/✘
- `remove`: the cascade stops at the first node that is a key or still has children (`:151`) — so
  `Prune` can fire zero times on a successful remove — ✔/✘
- no `SplitEdge` or `MergeEdge` is constructed anywhere in `StandardTrie.java` —
  `grep -c "SplitEdge\|MergeEdge" src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java`
  → expected **0** matches in code (the class Javadoc at `:30–32` mentions them in prose; read the
  hits rather than trusting the count).

Fix the guide's prose for anything that comes back ✘.

- [ ] **Step 9: Commit**

```bash
git add docs/guide/standard-trie.md docs/index.html README.md \
        src/main/java/com/gimlism/translucent/RegenerateDocs.java \
        src/test/java/com/gimlism/translucent/GuideEventMapTest.java
git commit -m "docs(guide): add the standard-trie guide and pin it to what the trie emits"
```

---

## Task 6: branch verification and the PR

**Files:** none — this task runs checks and writes the PR body.

- [ ] **Step 1: Full clean run**

```bash
mvn clean test
```
Expected: **587/587** — 573 at base, then +2 (T1) +2 (T2) +8 (T3) +0 (T4) +1 (T5a) +1 (T5b). If the
actual differs, the gap must be *explained* against that chain before the PR opens, not rounded off.

- [ ] **Step 2: Regeneration is a no-op**

```bash
mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
git status --short
```
Expected: **clean tree**. The committed site is provably generator output.

- [ ] **Step 3: The generated-diff scope is exactly what was intended**

```bash
git diff 57470fd --stat -- docs/
```
Expected: exactly **four** paths — `docs/viz/standard-trie.html` (new), `docs/index.html` (modified),
`docs/guide/standard-trie.md` (new), and `docs/guide/trie.md` (modified, **one line**: the demo
renumbering from Task 4 Step 6).

**`docs/viz/{list,map,treeset,trie,compression-compare}.html` and
`docs/guide/{list,map,treeset}.md` must be absent from that list.** The four untouched replays are
Task 2's byte-identity proof, restated at branch scope. Confirm the `trie.md` change really is one
line with `git diff 57470fd -- docs/guide/trie.md` — anything more means the guide was edited beyond
the renumbering.

- [ ] **Step 4: Mutation-prove the two new guards**

Each of these must be reverted immediately after it is observed.

1. **The copy-paste guard.** In `StandardTrieWebVizDemo.buildHtml()`, change `new StandardTrie<>()`
   to `new RadixTrie<>()` (keeping the `"StandardTrie"` label). Run
   `mvn -q test -Dtest=StandardTrieWebVizDemoTest`. Expected: RED on
   `"a standard trie never splits an edge"` — and note that the title assertion stays **green**,
   which is the whole reason the event assertion exists. Revert.
2. **The guide registry.** Delete the fifth `GuideCase` from `cases()`. Run
   `mvn -q test -Dtest=GuideEventMapTest`. Expected: RED on `everyGuideFileHasACase`, naming
   `docs/guide/standard-trie.md`. Revert.
3. **The help-text derivation.** Hardcode `"RadixTrie"` back into `helpText`. Run
   `mvn -q test -Dtest=TrieCommandInterpreterTest`. Expected: RED on the `StandardTrie` invocation
   only. Revert.
4. **The hosted-links count.** Delete the new README hosted URL row. Run
   `mvn -q test -Dtest=ReadmeSiteLinksTest`. Expected: RED twice at once — the named-URL check and
   `expected <7> but was <6>`. Revert.

After all four: `mvn clean test` → back to the recorded total, `git status` → clean.

- [ ] **Step 5: Open the PR**

Write the body against **what was actually observed**, not against this plan's predictions. The #53
review's best catch was in an unrun artifact — a `gh pr create` heredoc whose Evidence section
asserted a test count and an empty diff that were both stale by then. Re-read every number in the
body against the terminal before pressing enter.

```bash
git push -u origin feat/standard-trie-structure
gh pr create --title "feat(trie): make StandardTrie a first-class fifth structure" --body "..."
```

- [ ] **Step 6: After merge — the Pages check, then the browser render check**

Pages rebuilds on **every** push to `main` regardless of whether `/docs` changed, and a failed build
takes the live site down:

```bash
gh api repos/gimlism/translucent/pages/builds/latest --jq '{status,commit,error:.error.message}'
```

Then confirm all **seven** URLs (index + six pages) return 200 **and** are SHA-256 byte-identical to
the committed files — HTTP 200 alone only proves *a* file was served.

Finally, render `https://gimlism.github.io/translucent/viz/standard-trie.html` in the browser and
step the replay — not merely load it. Expect a `CreateNode` frame early and no `SplitEdge` anywhere,
and the deeper one-node-per-character chain beside what `trie.html` shows for the same keys.
⚠️ An in-page `fetch()` of the cross-origin guide links returns "Failed to fetch" — that is CORS, a
recorded measurement artifact, **not** a broken link. Do not re-raise it.

---

## Self-review

**Spec coverage.** Every section of `2026-08-11-standard-trie-web-arc-design.md` §"PR 2" maps to a
task: `PrefixMap` → T1; interpreter reseat + the unguarded `helpText` derivation → T1; the six demo
classes + scenario + rewritten prose + the port limitation → T3; the template token + `injectNamed` +
the ordering warning + the free proof → T2; the copy-paste guard → T3 Step 7 and T6 Step 4;
the page + the `"Trie"` → `"Radix trie"` title change → T5a; the guide → T5b; Launcher ordering → T4;
the "which guards move" table → distributed across T4/T5a/T5b, each with its expected RED named;
the six verification items → T5a Step 5, T5b Steps 4–5 and T6. Nothing in the spec's PR 2 section is
unassigned.

**Two spec details this plan hardens rather than copies:** the exporter has **four** entry points, not
the three the spec lists (`writeHtml` at `TrieWebExporter:38` delegates to `toHtml` and is called by
`TrieWebExporterTest:38–39`); and the `STRUCTURE` token's shape is argued explicitly in Decision A
rather than assumed, because the token lands in markup where the repo's `injectToken` convention would
otherwise point the other way.

**Two things this plan adds that the spec did not call for**, both of the same shape — a hand-written
surface the build cannot see:

1. T5b Step 5's live `gh api /markdown` check on the new heading. PR 1's only real defect was an
   orphaned README anchor, and the guard written to catch it explicitly documents that its slugifier
   is not GitHub's.
2. T4 Step 6's repair of `docs/guide/trie.md:59`. Renumbering the radix demos to 25–30 strands that
   line's "demos 19–24" pointing at the *standard* trie's rows. Every guard stays green through it:
   the anchor still resolves (heading text unchanged), `GuideEventMapTest` parses only the events
   table, `SiteIndexTest` checks only existence and linkage. It is a number in prose, pinned by
   nothing — the identical defect class PR 1 created, found here by asking what T4 breaks that no test
   asserts, rather than by a test.

**One assertion in this plan is inferred, not measured.** `StandardTrieWebVizDemoTest`'s
`assertFalse(html.contains("\"label\":\"sh\""))` reasons from the radix test that `"label"` is a real
JSON field and that a standard trie's labels are single characters. The field name is measured
(`RadixTrieWebVizDemoTest:21`); the absence claim is not. Keep the assertion — it is the right
property — but on the first green run confirm it is *discriminating* rather than trivially true, e.g.
by checking the generated page does contain single-character labels like `"label":"s"`.

**Placeholder scan.** No step says "add appropriate error handling", "similar to Task N" without the
code, or "write tests for the above". The two places that say "mirror X exactly" (T3 Steps 10–11)
name the file, the substitutions, every test method carried over, and the one behavioural difference
to watch (three leading `CreateNode` frames rather than one).

**Type consistency.** `PrefixMap<V>` (T1) is the parameter type in `execute(String, PrefixMap<Integer>)`
and `helpText(PrefixMap<?>)`, and the declared interface on both tries. `injectNamed`'s five
parameters (T2) match the four `TrieWebExporter` entry points that call it, and the argument order
`(framesJson, structure, out)` for `writeHtml` is stated once and used identically in T2 Step 7.
`StandardTrieWebVizDemo.buildHtml()` is declared `public static String` in T3 and referenced as
`StandardTrieWebVizDemo::buildHtml` in T5a. `runRepl`/`commandHandler` take `StandardTrie<Integer>`
in T3 Step 9 and are called with the same type in T3 Steps 10–11 (Decision B).
