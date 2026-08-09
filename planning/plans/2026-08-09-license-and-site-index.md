# Licence and site index — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an MIT `LICENSE` and a generated, golden-pinned `docs/index.html`, so the repo can be
made public and GitHub Pages serves a real landing page instead of a 404.

**Architecture:** `RegenerateDocs` already bakes five self-contained HTML pages into `docs/viz/` and
`DocsPagesGoldenTest` pins each to its generator. This plan promotes that list into a `Page` record
carrying display metadata, re-keys the map by path relative to `docs/` so the index joins the same
guard, and adds a `SiteIndex` generator that builds the landing page from the same list. Two
filesystem-vs-page checks close the orphan direction the golden cannot see.

**Tech Stack:** Java 21, Maven, JUnit 5 (the only dependency, test-scoped). No new dependencies.

**Spec:** `planning/specs/2026-08-09-license-and-pages-design.md`

## Global Constraints

- **Baseline is 554 tests, 0 failures** on merged main. Every task ends green, and the count only
  goes up.
- **No new dependencies.** The repo has exactly one (`junit-jupiter`, test-scoped) and that is a
  feature of it.
- **`docs/viz/*.html` bytes must not change.** Prove with `git diff --stat docs/viz/` after every
  regeneration; empty output is the pass condition.
- **The README must contain no `com.gimlism.translucent.*` string outside `Launcher.CATALOG`.**
  `LauncherReadmeTest.readmeNamesNoDemoThatIsNotInTheCatalog` regex-scans the whole file. In
  particular, never write `RegenerateDocs` as an FQCN in the README.
- **Every new guard is proven red by mutation before commit**, then reverted. Listing a mutation is
  not running it.
- **Generated pages are self-contained**: no external requests, no CDN, no web fonts. They must work
  from `file://` off a clone.
- **Line endings are LF**, enforced by `.gitattributes` (`*.html text eol=lf`). Do not change it.
- **Do not run `gh repo edit --visibility`, and do not enable Pages.** Both are the maintainer's,
  and both are covered by Task 6's handover, which hands over commands rather than running them.

---

## File Structure

| file | responsibility |
| --- | --- |
| `LICENSE` | The MIT grant. Repo root, covers code and prose. |
| `src/test/java/com/gimlism/translucent/LicenseTest.java` | Pins LICENSE's existence and content, and pins the README's licence claim *to the LICENSE file* rather than to a hardcoded string. |
| `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java` | Gains `injectToken`; `injectStatic` delegates to it. Stays the only classpath-template reader. |
| `src/main/java/com/gimlism/translucent/RegenerateDocs.java` | Gains the `Page` record and `vizPages()`; `DIR` becomes `docs`; `pages()` keys become relative paths. |
| `src/main/resources/web/site-index.html` | The landing-page template. One token, `<!--__PAGES__-->`. |
| `src/main/java/com/gimlism/translucent/SiteIndex.java` | Renders the `Page` list into that token. Owns the GitHub guide-URL base. |
| `docs/index.html` | Generated, committed build output. |
| `src/test/java/com/gimlism/translucent/SiteIndexTest.java` | Filesystem → index: no orphan viz page, no orphan guide. |
| `README.md` | Index promoted, `planning/` pointer, licence line. |

---

### Task 1: MIT licence, pinned to the README claim

**Files:**
- Create: `LICENSE`
- Create: `src/test/java/com/gimlism/translucent/LicenseTest.java`
- Modify: `README.md` (append a licence section only — do not touch the demo grid)

**Interfaces:**
- Consumes: nothing.
- Produces: `LICENSE` at the repo root whose **first line is exactly `MIT License`**. Task 5 relies
  on the README licence line format defined here; do not reword it.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/LicenseTest.java`:

```java
package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A public repo with no licence is not open source: default copyright reserves every right, so a
 * student who clones this to learn from it has no grant to do so. These pin the two halves that can
 * drift apart — the file itself, and the README's claim about what it says.
 */
class LicenseTest {

    private static final Path LICENSE = Path.of("LICENSE");

    /**
     * The README's licence sentence, captured rather than searched for. A bare
     * {@code readme.contains("MIT")} passes on any README mentioning MIT anywhere — including in a
     * sentence about something else — so it would survive the very mutation meant to disprove it.
     */
    private static final Pattern README_LICENCE_LINE = Pattern.compile(
            "^Licensed under the (.+?) License — see \\[LICENSE]\\(LICENSE\\)\\.$", Pattern.MULTILINE);

    private static String license() throws IOException {
        return Files.readString(LICENSE, StandardCharsets.UTF_8);
    }

    private static String readme() throws IOException {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    }

    @Test
    void licenseFileExists() {
        assertTrue(Files.exists(LICENSE), "LICENSE is missing — the repo cannot be published without it");
    }

    @Test
    void licenseCarriesTheMitGrantAndDisclaimer() throws IOException {
        String text = license();
        assertTrue(text.contains("Permission is hereby granted, free of charge"),
                "LICENSE does not carry the MIT permission grant");
        assertTrue(text.contains("WITHOUT WARRANTY OF ANY KIND"),
                "LICENSE does not carry the MIT warranty disclaimer");
    }

    @Test
    void licenseNamesACopyrightHolderAndYear() throws IOException {
        assertTrue(Pattern.compile("^Copyright \\(c\\) \\d{4} \\S.*$", Pattern.MULTILINE)
                        .matcher(license()).find(),
                "LICENSE has no 'Copyright (c) <year> <holder>' line");
    }

    /**
     * The load-bearing one. Compares the README's claim against what the file declares itself to be,
     * so changing either alone goes red — no hardcoded "MIT" for a mutation to slip past.
     */
    @Test
    void readmeNamesTheLicenceTheFileDeclaresItselfToBe() throws IOException {
        String declared = license().lines().findFirst().orElse("").trim();
        Matcher m = README_LICENCE_LINE.matcher(readme());
        assertTrue(m.find(), "README.md has no 'Licensed under the … License — see [LICENSE](LICENSE).' line");
        assertEquals(declared, m.group(1) + " License",
                "README.md names a different licence than LICENSE declares itself to be");
    }
}
```

- [ ] **Step 2: Run it and verify all four fail**

```bash
mvn -q test -Dtest=LicenseTest
```

Expected: 4 tests run, 4 failures — `LICENSE is missing`, then `NoSuchFileException` on the reads.

- [ ] **Step 3: Write the LICENSE**

Create `LICENSE` — first line exactly `MIT License`:

```
MIT License

Copyright (c) 2026 Paul Storer-Martin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 4: Add the README licence section**

Append to the very end of `README.md`, after the "Running the tests" section:

```markdown

## Licence

Licensed under the MIT License — see [LICENSE](LICENSE).

The data structures are reimplementations written from the published algorithms, not adaptations of
any JDK source: the thresholds are deliberately scaled down so a treeify or a resize is reachable in
a demo you can watch.
```

- [ ] **Step 5: Run the tests and verify green**

```bash
mvn -q test -Dtest='LicenseTest+LauncherReadmeTest'
```

Expected: all pass. `LauncherReadmeTest` is included because this step edits the file it parses.

- [ ] **Step 6: Prove the guard is not vacuous — run three mutations**

`LICENSE` is not committed yet, so `git checkout` cannot restore it. Take a copy first and restore
from that:

```bash
cp LICENSE /tmp/LICENSE.bak

# (a) THE ONE THAT MATTERS. README names a licence the file is not — and the word "MIT" survives
#     in the paragraph below the licence line, which is exactly what a contains("MIT") check
#     would sail past.
sed -i '' 's/Licensed under the MIT License/Licensed under the Apache-2.0 License/' README.md
mvn -q test -Dtest=LicenseTest   # EXPECT RED: readmeNamesTheLicenceTheFileDeclaresItselfToBe
git checkout README.md           # README IS committed (Task 1 edits a tracked file)

# (b) LICENSE absent
mv LICENSE /tmp/LICENSE.moved
mvn -q test -Dtest=LicenseTest   # EXPECT RED: licenseFileExists
mv /tmp/LICENSE.moved LICENSE

# (c) LICENSE gutted of its grant
printf 'MIT License\n\nCopyright (c) 2026 Paul Storer-Martin\n' > LICENSE
mvn -q test -Dtest=LicenseTest   # EXPECT RED: licenseCarriesTheMitGrantAndDisclaimer
cp /tmp/LICENSE.bak LICENSE

# Confirm restored, then clean up
mvn -q test -Dtest=LicenseTest   # EXPECT GREEN
rm /tmp/LICENSE.bak
```

If (a) passes, stop: the assertion is comparing against a hardcoded string rather than against the
LICENSE file, and the guard is worth nothing.

Note (a) reverts with `git checkout` because Task 1 edits `README.md`, which is tracked. If you have
not yet made the Step 4 edit when you run this, re-apply it afterwards.

- [ ] **Step 7: Full suite, then commit**

```bash
mvn -q test && git add LICENSE README.md src/test/java/com/gimlism/translucent/LicenseTest.java && git commit -F - <<'EOF'
feat: licence the repo under MIT

A public repo with no LICENSE is not open source — default copyright
reserves every right, so a student who clones this to learn from it has no
grant to do so. This is the blocker for publishing, not a formality.

MIT rather than Apache-2.0: the patent grant and NOTICE machinery buy
nothing for teaching implementations of algorithms published decades ago,
and brevity is worth real points in a repo whose thesis is approachability.

LicenseTest pins the README's claim against what LICENSE declares itself to
be, rather than against a hardcoded "MIT" — a contains("MIT") assertion
survives renaming the licence line while the word survives elsewhere in the
file, which is exactly the mutation run to disprove it.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
```

Expected: 558 tests, 0 failures.

---

### Task 2: `WebVizTemplate.injectToken`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `public static String WebVizTemplate.injectToken(String resource, String token, String
  replacement)` — reads a classpath resource, throws `IllegalStateException` naming the token if
  absent, returns the substituted text. Task 4's `SiteIndex` calls exactly this.

- [ ] **Step 1: Write the failing tests**

Add to `WebVizTemplateTest` (keep the six existing tests untouched):

```java
    @Test
    void injectTokenSubstitutesAnArbitraryToken() {
        String out = WebVizTemplate.injectToken("/web/webviztemplate-static.html", "/*__DATA__*/", "9");
        assertTrue(out.contains("DATA=9"), out);
        assertFalse(out.contains("/*__"), "no token left behind");
    }

    @Test
    void injectTokenMissingTokenThrowsNamingIt() {
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.injectToken("/web/webviztemplate-static.html", "<!--__PAGES__-->", "x"));
        assertTrue(e.getMessage().contains("<!--__PAGES__-->"), e.getMessage());
    }

    /**
     * The delegation is the point: one classpath reader, one missing-token message. If these two
     * ever diverge, the generic helper has grown a second behaviour and the five baked viz goldens
     * are no longer guaranteed byte-identical.
     */
    @Test
    void injectStaticIsInjectTokenOnTheDataToken() {
        String viaStatic = WebVizTemplate.injectStatic("/web/webviztemplate-static.html", "{\"x\":1}");
        String viaToken = WebVizTemplate.injectToken("/web/webviztemplate-static.html", "/*__DATA__*/", "{\"x\":1}");
        assertEquals(viaToken, viaStatic);
    }
```

Add the import `import static org.junit.jupiter.api.Assertions.assertEquals;` at the top.

- [ ] **Step 2: Run and verify they fail**

```bash
mvn -q test -Dtest=WebVizTemplateTest
```

Expected: compilation failure — `cannot find symbol: method injectToken`. That is the red.

- [ ] **Step 3: Add `injectToken` and make `injectStatic` delegate**

In `WebVizTemplate.java`, replace the existing `injectStatic` method with:

```java
    /**
     * Read {@code resource} and substitute the single {@code DATA} token with {@code data}, for a
     * static page that carries one baked data blob and no frame-replay flags. The three-token
     * {@link #inject} stays the right tool for live (FRAMES/LIVE/CONTROLS) pages.
     */
    public static String injectStatic(String resource, String data) {
        return injectToken(resource, DATA_TOKEN, data);
    }

    /**
     * Read {@code resource}, require {@code token}, and substitute it. The single-token workhorse
     * behind {@link #injectStatic}, exposed for pages that are not visualisations: the site index
     * substitutes an HTML comment token because its replacement lands in markup rather than inside
     * a {@code <script>}.
     *
     * <p>Deliberately single-token. {@link #inject} stays separate because ORDER is its invariant —
     * user data substituted last so a frame containing a token literal cannot be rewritten — and a
     * general n-token helper could not enforce that.
     */
    public static String injectToken(String resource, String token, String replacement) {
        String template = read(resource);
        require(template, resource, token);
        return template.replace(token, replacement);
    }
```

- [ ] **Step 4: Run and verify green**

```bash
mvn -q test -Dtest='WebVizTemplateTest+DocsPagesGoldenTest'
```

Expected: all pass. `DocsPagesGoldenTest` is included because `CompressionCompareWebExporter` routes
through `injectStatic` — if delegation changed a byte, that golden catches it here.

- [ ] **Step 5: Prove the new guard is not vacuous**

```bash
# Break the delegation: injectStatic no longer routes through injectToken's require().
# Temporarily change injectStatic's body to: return read(resource).replace(DATA_TOKEN, data);
mvn -q test -Dtest=WebVizTemplateTest   # EXPECT RED: injectStaticMissingDataTokenThrowsNamingIt
git checkout src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java
```

Then reapply Step 3.

- [ ] **Step 6: Full suite, then commit**

```bash
mvn -q test && git add src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java && git commit -F - <<'EOF'
refactor(viz): extract a generic injectToken behind injectStatic

The site index needs one token substituted in a classpath template — the
same read-and-require logic this class already owns. Adding a private copy
inside SiteIndex would be a second hand-transcribed reader, which is
finding D1 of the Fable review in miniature.

injectStatic now delegates, so its output bytes are unchanged and all five
baked viz goldens stay byte-identical; a test asserts the two paths agree,
so the delegation cannot quietly rot into two behaviours.

inject stays separate on purpose: ORDER is its invariant (user data last,
so a frame containing a token literal is never rewritten) and a general
n-token helper could not enforce that.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
```

Expected: 561 tests, 0 failures.

---

### Task 3: re-key `pages()` by path, add the `Page` record

Pure refactor. **No output byte may change.** The index arrives in Task 4.

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/RegenerateDocs.java`
- Test: `src/test/java/com/gimlism/translucent/DocsPagesGoldenTest.java`

**Interfaces:**
- Consumes: nothing from Tasks 1–2.
- Produces:
  - `static final Path RegenerateDocs.DIR` — now `Path.of("docs")`, was `docs/viz`.
  - `record RegenerateDocs.Page(String path, String title, String blurb, String guide,
    Supplier<String> html)` — package-private. `path` is relative to `DIR`; `guide` is relative to
    `DIR` or `null`.
  - `static List<Page> RegenerateDocs.vizPages()` — the five, in teaching order.
  - `static Map<String, String> RegenerateDocs.pages()` — unchanged signature, keys now
    `viz/list.html` … not `list.html`.

- [ ] **Step 1: Rewrite `RegenerateDocs.java`**

Replace the whole file:

```java
package com.gimlism.translucent;

import com.gimlism.translucent.arraylist.demo.ListWebVizDemo;
import com.gimlism.translucent.hashmap.demo.MapWebVizDemo;
import com.gimlism.translucent.treeset.demo.TreeSetWebVizDemo;
import com.gimlism.translucent.trie.compare.CompressionCompareDemo;
import com.gimlism.translucent.trie.compare.CompressionCompareJsonSerializer;
import com.gimlism.translucent.trie.compare.CompressionCompareWebExporter;
import com.gimlism.translucent.trie.demo.TrieWebVizDemo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bakes the published site into {@code docs/} — a landing page plus one self-contained HTML page per
 * visualisation. These are committed, so a student opens one straight off disk with no JDK, no
 * Maven, and no server. The pages carry their own data and styling; there are no external requests
 * at all, which is what makes {@code file://} work.
 *
 * <p>Run after changing any demo story:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs}
 *
 * <p>{@code DocsPagesGoldenTest} pins the committed bytes to {@link #pages()}, so forgetting to run
 * this fails the build rather than shipping a page that disagrees with the code it depicts.
 */
public final class RegenerateDocs {

    private RegenerateDocs() { }

    /**
     * The published site root — what GitHub Pages serves. Keys in {@link #pages()} are paths
     * relative to this, so one map addresses both the landing page at the root and the
     * visualisations under {@code viz/}, and one golden test covers them all.
     */
    static final Path DIR = Path.of("docs");

    /** Named in every golden failure so the fix is in the error, not in a document somewhere. */
    static final String REGENERATE_HINT =
            "regenerate with: mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs";

    /** The canonical key set for the compression comparison, mirroring the exporter's own demo. */
    private static final List<String> COMPRESSION_KEYS = List.of("she", "shell", "shore", "shy");

    /**
     * One published visualisation: where it lands, how the landing page names and describes it, the
     * guide it belongs to, and the generator that bakes it.
     *
     * @param path  where the page lands, relative to {@link #DIR} — e.g. {@code viz/list.html}
     * @param title the landing page's link text
     * @param blurb the one line under that link
     * @param guide the matching guide relative to {@link #DIR}, or {@code null} where none exists
     * @param html  the generator, invoked per call and never memoised — see {@link #pages()}
     */
    record Page(String path, String title, String blurb, String guide, Supplier<String> html) { }

    /**
     * Every visualisation, in the teaching order {@code Launcher.CATALOG} uses. Deliberately the
     * single source of truth twice over: {@link #pages()} bakes these, and {@code SiteIndex} lists
     * them, so adding a structure is one entry here and both the page and its index row follow.
     */
    static List<Page> vizPages() {
        return List.of(
                new Page("viz/list.html", "ArrayList",
                        "A growable array, copying itself into a bigger one when it fills.",
                        "guide/list.md", ListWebVizDemo::buildHtml),
                new Page("viz/map.html", "HashMap",
                        "Buckets and chains — and a red-black tree once a chain grows too long.",
                        "guide/map.md", MapWebVizDemo::buildHtml),
                new Page("viz/treeset.html", "TreeSet",
                        "A red-black tree rotating and recolouring to stay balanced.",
                        "guide/treeset.md", TreeSetWebVizDemo::buildHtml),
                new Page("viz/trie.html", "Trie",
                        "A radix trie whose edges split and merge as keys arrive and leave.",
                        "guide/trie.md", TrieWebVizDemo::buildHtml),
                new Page("viz/compression-compare.html", "Trie compression",
                        "A fat trie beside its compressed form, and what the compression saves.",
                        null, RegenerateDocs::compressionCompareHtml));
    }

    private static String compressionCompareHtml() {
        return CompressionCompareWebExporter.toHtml(CompressionCompareJsonSerializer.toJson(
                CompressionCompareDemo.compare(COMPRESSION_KEYS)));
    }

    /**
     * Every page by path. Insertion-ordered so regeneration and the goldens agree on order, and
     * deliberately the single source of truth: the test compares against exactly the strings this
     * method produced, so a page cannot be verified against a different generator than the one that
     * wrote it.
     */
    static Map<String, String> pages() {
        var pages = new LinkedHashMap<String, String>();
        for (Page page : vizPages()) {
            pages.put(page.path(), page.html().get());
        }
        return pages;
    }

    public static void main(String[] args) throws IOException {
        for (Map.Entry<String, String> page : pages().entrySet()) {
            Path out = DIR.resolve(page.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, page.getValue(), StandardCharsets.UTF_8);
            System.out.println("wrote " + out);
        }
    }
}
```

- [ ] **Step 2: Update the golden test's display name**

In `DocsPagesGoldenTest.java`, change one line — the keys now carry their subdirectory:

```java
    @ParameterizedTest(name = "docs/{0}")
```

Leave everything else, especially the `pageNames()` comment and its uncached `RegenerateDocs.pages()`
call. That freshness is what catches a generator holding lazily-initialised static state; hoisting it
into a field would compare first-call to first-call and see nothing.

- [ ] **Step 3: Run the goldens and verify green**

```bash
mvn -q test -Dtest=DocsPagesGoldenTest
```

Expected: 5 tests pass, named `docs/viz/list.html` … `docs/viz/compression-compare.html`. They pass
without regenerating because `DIR.resolve("viz/list.html")` is the same path `DIR.resolve("list.html")`
resolved to before.

- [ ] **Step 4: Prove the refactor changed no output bytes**

```bash
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
git diff --stat docs/
```

Expected: **no output at all.** Any diff means the re-keying or the `compressionCompareHtml` extraction
changed a generator's result — stop and find it before continuing.

- [ ] **Step 5: Full suite, then commit**

```bash
mvn -q test && git add src/main/java/com/gimlism/translucent/RegenerateDocs.java src/test/java/com/gimlism/translucent/DocsPagesGoldenTest.java && git commit -F - <<'EOF'
refactor(docs): key the page map by path and give each page a descriptor

Preparation for the landing page, and no output byte changes — DIR moves to
docs/ with the keys carrying their own subdirectory, so DIR.resolve lands on
exactly the paths it did before.

Two things this buys. One map now addresses both the site root and viz/, so
the index inherits DocsPagesGoldenTest rather than needing a parallel guard.
And the new Page record carries what a filename cannot — title, blurb, and
the guide a structure belongs to — so the landing page can be generated from
the same list that bakes the pages, making a new structure one entry rather
than two places to remember.

The generator is held as a Supplier and invoked per call, preserving the
property the golden test's comment depends on: pages() is never memoised, so
first-call output is compared against later-call output.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
```

Expected: 561 tests, 0 failures.

---

### Task 4: the generated landing page

**Files:**
- Create: `src/main/resources/web/site-index.html`
- Create: `src/main/java/com/gimlism/translucent/SiteIndex.java`
- Modify: `src/main/java/com/gimlism/translucent/RegenerateDocs.java` (one line in `pages()`)
- Create: `src/test/java/com/gimlism/translucent/SiteIndexTest.java`
- Create: `docs/index.html` (generated — do not hand-write)

**Interfaces:**
- Consumes: `WebVizTemplate.injectToken` (Task 2); `RegenerateDocs.Page`, `vizPages()`, `DIR`,
  `REGENERATE_HINT` (Task 3).
- Produces: `static String SiteIndex.build(List<RegenerateDocs.Page>)` and
  `static final String SiteIndex.GUIDE_BASE`. Both package-private — `Page` is package-private, so a
  public signature exposing it would not compile usefully outside this package anyway.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/SiteIndexTest.java`:

```java
package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code DocsPagesGoldenTest} compares committed bytes against generator output, so it only ever
 * asks about files the page list already names. These check the other direction: a file sitting
 * under {@code docs/} that no {@code Page} claims is linked from nowhere, and would sail past
 * everything else in the suite.
 */
class SiteIndexTest {

    private static String index() throws IOException {
        return Files.readString(RegenerateDocs.DIR.resolve("index.html"), StandardCharsets.UTF_8);
    }

    private static List<Path> filesIn(String subdir, String suffix) throws IOException {
        try (Stream<Path> found = Files.list(RegenerateDocs.DIR.resolve(subdir))) {
            return found.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    @Test
    void everyBakedVizPageIsLinkedFromTheIndex() throws IOException {
        String index = index();
        List<Path> baked = filesIn("viz", ".html");
        assertFalse(baked.isEmpty(), "no pages under docs/viz — this check would be vacuous");
        for (Path page : baked) {
            String href = "viz/" + page.getFileName();
            assertTrue(index.contains("\"" + href + "\""),
                    "docs/index.html does not link " + href + " — " + RegenerateDocs.REGENERATE_HINT);
        }
    }

    @Test
    void everyGuideIsLinkedFromTheIndex() throws IOException {
        String index = index();
        List<Path> guides = filesIn("guide", ".md");
        assertFalse(guides.isEmpty(), "no guides under docs/guide — this check would be vacuous");
        for (Path guide : guides) {
            String href = SiteIndex.GUIDE_BASE + "guide/" + guide.getFileName();
            assertTrue(index.contains("\"" + href + "\""),
                    "docs/index.html does not link " + href
                            + " — add the guide to the matching Page in RegenerateDocs.vizPages()");
        }
    }

    /**
     * The promise the landing page makes to a student opening it off a clone with no network: it
     * fetches nothing. Every absolute URL in the page must be a github.com link the reader chooses
     * to click, never something the browser retrieves to render.
     */
    @Test
    void theIndexFetchesNothing() throws IOException {
        String index = index();
        assertFalse(index.contains("<script"), "the index needs no JavaScript; it must not carry any");
        assertFalse(index.contains("<link rel=\"stylesheet\""), "styles must be inline, not fetched");
        Matcher url = Pattern.compile("https?://[^\"'\\s>]+").matcher(index);
        while (url.find()) {
            assertTrue(url.group().startsWith("https://github.com/"),
                    url.group() + " is not a github.com link — the page must fetch nothing");
        }
    }
}
```

- [ ] **Step 2: Run and verify it fails**

```bash
mvn -q test -Dtest=SiteIndexTest
```

Expected: compilation failure — `cannot find symbol: variable SiteIndex`. That is the red.

- [ ] **Step 3: Write the template**

Create `src/main/resources/web/site-index.html`. The colour variables are lifted from the viz pages
so the landing page and what it links to read as one site:

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>translucent — data structures that narrate themselves</title>
<style>
  :root { color-scheme: light dark; --bg:#f7f7f8; --fg:#1b1b1d; --muted:#777; --card:#fff;
          --cellb:#c8ccd4; --hl:#f5a623; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#16171a; --fg:#e8e8ea; --muted:#999; --card:#1f2126;
            --cellb:#41454d; --hl:#f5a623; }
  }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:15px system-ui,sans-serif; }
  #app { max-width: 720px; margin: 0 auto; padding: 40px 18px 64px; }
  h1 { font-size: 22px; font-weight:600; margin: 0 0 6px; }
  h2 { font-size: 12px; font-weight:600; text-transform:uppercase; letter-spacing:.07em;
       color:var(--muted); margin: 34px 0 12px; }
  p { margin: 0 0 10px; }
  .lede { color:var(--muted); }
  code { font: 13px ui-monospace, SFMono-Regular, Menlo, monospace; }
  ul { list-style:none; margin:0; padding:0; }
  li { margin: 0 0 8px; }
  a.card { display:block; background:var(--card); border:1px solid var(--cellb); border-radius:12px;
           padding:12px 14px; text-decoration:none; color:inherit; }
  a.card:hover { border-color: var(--hl); }
  a.card .t { font-weight:600; }
  a.card .b { color:var(--muted); font-size:13px; margin-top:3px; }
  footer { margin-top:40px; padding-top:16px; border-top:1px solid var(--cellb);
           color:var(--muted); font-size:13px; }
  footer a { color:inherit; }
</style>
</head>
<body>
<div id="app">
  <h1>translucent</h1>
  <p class="lede">Four data structures, each narrating what it does as it does it.</p>
  <p class="lede">The narration is emitted by the real <code>put()</code> and <code>add()</code>
     paths, so what you watch is the algorithm running — not an animation of it.</p>
<!--__PAGES__-->
  <footer>
    <a href="https://github.com/gimlism/translucent">Source on GitHub</a> — MIT licensed.
    Every page here is self-contained: no server, no build, nothing to install.
  </footer>
</div>
</body>
</html>
```

- [ ] **Step 4: Write `SiteIndex`**

Create `src/main/java/com/gimlism/translucent/SiteIndex.java`:

```java
package com.gimlism.translucent;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.util.List;
import java.util.StringJoiner;

/**
 * Builds {@code docs/index.html}, the published site's front door.
 *
 * <p>Generated rather than hand-written because it is the one student-facing file that would
 * otherwise escape the build. Every page under {@code docs/viz/} is pinned to its generator, so a
 * hand-maintained index could list five pages while six existed and nothing in the suite would
 * notice — the exact staleness this repo's goldens exist to prevent, on the file that turns the
 * site on. {@code SiteIndexTest} closes the other direction: no file on disk missing from here.
 *
 * <p>Guide links are absolute GitHub URLs on purpose. {@code docs/.nojekyll} means Pages serves
 * {@code .md} as raw text — a browser shows plain source or offers a download — while GitHub
 * renders it with heading anchors for free. Being absolute, they also behave identically whether
 * this page was opened from the site or from {@code file://} off a clone, which is the same reason
 * the visualisation links stay relative.
 */
final class SiteIndex {

    private SiteIndex() { }

    private static final String TEMPLATE = "/web/site-index.html";
    private static final String PAGES_TOKEN = "<!--__PAGES__-->";

    /** Where a guide under {@code docs/} is rendered, since Pages cannot render it itself. */
    static final String GUIDE_BASE = "https://github.com/gimlism/translucent/blob/main/docs/";

    /** Render {@code pages} into the template, in the order given. */
    static String build(List<RegenerateDocs.Page> pages) {
        var body = new StringJoiner("\n");

        body.add("  <h2>Watch one — nothing to install</h2>");
        body.add("  <ul>");
        for (RegenerateDocs.Page page : pages) {
            body.add(card(page.path(), page.title(), page.blurb()));
        }
        body.add("  </ul>");

        body.add("  <h2>What each method makes you see</h2>");
        body.add("  <ul>");
        for (RegenerateDocs.Page page : pages) {
            if (page.guide() != null) {
                body.add(card(GUIDE_BASE + page.guide(), page.title(),
                        "Every narrating method, and the events it emits."));
            }
        }
        body.add("  </ul>");

        return WebVizTemplate.injectToken(TEMPLATE, PAGES_TOKEN, body.toString());
    }

    private static String card(String href, String title, String blurb) {
        return "    <li><a class=\"card\" href=\"" + esc(href) + "\">"
                + "<div class=\"t\">" + esc(title) + "</div>"
                + "<div class=\"b\">" + esc(blurb) + "</div></a></li>";
    }

    private static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
```

- [ ] **Step 5: Add the index to `pages()`**

In `RegenerateDocs.pages()`, add one line after the loop, and update the surrounding Javadoc
sentence:

```java
    static Map<String, String> pages() {
        var pages = new LinkedHashMap<String, String>();
        for (Page page : vizPages()) {
            pages.put(page.path(), page.html().get());
        }
        pages.put("index.html", SiteIndex.build(vizPages()));   // last: it links everything above
        return pages;
    }
```

- [ ] **Step 6: Generate and inspect the page**

```bash
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
git diff --stat docs/viz/     # EXPECT: empty — the viz pages must be untouched
git status --short docs/      # EXPECT: only ?? docs/index.html
```

- [ ] **Step 7: Run the tests and verify green**

```bash
mvn -q test -Dtest='SiteIndexTest+DocsPagesGoldenTest'
```

Expected: `DocsPagesGoldenTest` now runs **6** cases including `docs/index.html`; `SiteIndexTest`
passes 3.

- [ ] **Step 8: Prove both new guards are not vacuous**

**Both mutations must regenerate before asserting.** Removing a `Page` changes what `SiteIndex`
*would* build, but `docs/index.html` on disk is unchanged until `RegenerateDocs` runs — and it is the
committed file these tests read. Skipping the regeneration step tests the old page and passes for the
wrong reason.

Note also that regeneration never *deletes* a stale file, which is precisely why the orphan can
exist: the `.html` stays on disk with nothing pointing at it.

```bash
# (a) orphan viz page — on disk, claimed by no Page.
#     Delete the compression-compare entry from vizPages(), leaving docs/viz/compression-compare.html
#     in place, then REGENERATE so the index is rebuilt without it:
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
mvn -q test -Dtest=SiteIndexTest
#     EXPECT RED: everyBakedVizPageIsLinkedFromTheIndex names viz/compression-compare.html
#     Restore the entry, regenerate, confirm green.

# (b) orphan guide — set the trie Page's guide to null, regenerate, run:
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs
mvn -q test -Dtest=SiteIndexTest
#     EXPECT RED: everyGuideIsLinkedFromTheIndex names …/docs/guide/trie.md
#     Restore, regenerate, confirm green.

# Finally, make sure the tree is back where it started before committing:
git diff --stat docs/     # EXPECT: empty
```

Both must go red. If (a) passes, the check is reading the `Page` list rather than the filesystem and
has no value — fix it before continuing.

- [ ] **Step 9: Full suite, then commit**

```bash
mvn -q test && git add src/main/resources/web/site-index.html src/main/java/com/gimlism/translucent/SiteIndex.java src/main/java/com/gimlism/translucent/RegenerateDocs.java src/test/java/com/gimlism/translucent/SiteIndexTest.java docs/index.html && git commit -F - <<'EOF'
feat(docs): generate the site's landing page

docs/ held five baked pages and four guides but no index.html, and Pages
does not generate directory listings — so the site root would have 404ed
while every deep link worked, including from the repo's own homepage field.

Generated from the same Page list that bakes the visualisations, so adding a
structure is still one entry and the index follows. Hand-writing it would
have made this the only student-facing file invisible to the build: it could
list five pages while six existed and nothing would fail.

SiteIndexTest checks the direction the golden cannot see. The golden compares
committed bytes to generator output, so it only asks about files the list
already names; an .html sitting in docs/viz/ that no Page claims is linked
from nowhere and passes everything. One case per direction, both reading the
filesystem, both proven red by mutation.

Guide links are absolute GitHub URLs because .nojekyll means Pages serves .md
as raw text, and an absolute link renders the same from the site or from
file:// off a clone.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
```

Expected: 565 tests, 0 failures.

---

### Task 5: README — promote the index, point at `planning/`

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: `docs/index.html` (Task 4); the licence section added in Task 1.
- Produces: nothing code-facing.

- [ ] **Step 1: Promote the index in the zero-toolchain section**

In `README.md`, replace the paragraph under `## Watch one without installing anything`:

```markdown
Download this repo (or clone it) and open any of these files in a browser. They need no JDK, no
Maven, and no server — each one carries its own data and styling:
```

with:

```markdown
Download this repo (or clone it) and open **`docs/index.html`** in a browser — it links everything
below. No JDK, no Maven, no server: each page carries its own data and styling.

Or open one directly:
```

- [ ] **Step 2: Add the `planning/` pointer**

Insert a new section immediately before `## Running the tests`:

```markdown
## How each slice got decided

`planning/` holds the spec and the implementation plan written before each slice was built, plus one
full-repo review. They are a record of how decisions were reached — including approaches that were
considered and rejected — and they are dated for that reason. Where a plan disagrees with the source,
the source is right.
```

- [ ] **Step 3: Verify the FQCN constraint by hand before running anything**

```bash
diff <(grep -oE 'com\.gimlism\.translucent\.[A-Za-z0-9_.]+' README.md | sort -u) \
     <(grep -oE 'com\.gimlism\.translucent\.[A-Za-z0-9_.]+' src/main/java/com/gimlism/translucent/Launcher.java | sort -u)
```

Expected: no output. Any line prefixed `<` is a README FQCN absent from the catalog and will fail
`LauncherReadmeTest` — the likeliest cause is prose naming `RegenerateDocs`, which is a maintainer
tool, not a demo. Regeneration is documented in that class's own Javadoc; it must not appear here.

- [ ] **Step 4: Run the README guards**

```bash
mvn -q test -Dtest='LauncherReadmeTest+LauncherCatalogTest+LicenseTest'
```

Expected: all pass. `LicenseTest` is included because its regex reads the file this task edits.

- [ ] **Step 5: Full suite, then commit**

```bash
mvn -q test && git add README.md && git commit -F - <<'EOF'
docs(readme): lead with the landing page, and say what planning/ is

The zero-toolchain section listed five files without naming the door that
links them. It now leads with docs/index.html and keeps the table, which is
still the map once you know what you want.

The planning/ pointer exists because the failure mode after publishing is
misreading, not exposure: a dated spec describing a rejected approach reads
as current documentation to someone who does not know it is a record. The
section says so, and says the source wins.

Deliberately does not name RegenerateDocs — LauncherReadmeTest asserts every
com.gimlism.translucent.* in this file is a catalogued demo, and it is a
maintainer tool. Regeneration stays documented in its own Javadoc.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
```

Expected: 565 tests, 0 failures.

---

### Task 6: browser verification, PR, and the handover

No code. This task ends with a PR open and a handover comment the maintainer acts on.

**Files:** none modified.

- [ ] **Step 1: Verify the generated page in a browser, both themes**

Open `docs/index.html` from the filesystem — **this cannot be automated**, since Chrome MCP refuses
the `file://` scheme. The maintainer runs it:

```
open docs/index.html
```

Check: the page renders; each of the five cards opens its visualisation and steps through it; each
guide card opens GitHub and renders; the console is clean; light and dark both read correctly
(toggle the OS appearance). This is the same manual gate #47 used, for the same reason.

- [ ] **Step 2: Confirm the tree is clean and the suite is green**

```bash
mvn -q test
git status --short          # EXPECT: empty
git diff --stat main...HEAD
```

Expected: 565 tests, 0 failures; nothing uncommitted.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin feat/license-and-site-index
gh pr create --title "PR E: MIT licence and a generated site index" --body "$(cat <<'EOF'
Closes the student on-ramp arc. Two blockers to publishing, both now removed.

**No licence.** `gh repo view` reported `licenseInfo: null`. A public repo with no LICENSE is not
open source — default copyright reserves every right, so a student who clones it to learn from it
has no grant to do so. MIT, one file at the root, covering code and prose.

**Pages would 404 at its own root.** `docs/` had `.nojekyll`, five baked pages and four guides but
no `index.html`, and Pages does not generate directory listings. `docs/index.html` is now generated
from the same `Page` list that bakes the visualisations, so adding a structure is one entry and the
index follows.

### Guards

- `LicenseTest` pins the README's licence claim to what LICENSE declares itself to be, not to a
  hardcoded `"MIT"` — a `contains("MIT")` assertion survives renaming the licence line while the
  word survives elsewhere in the file, which is the mutation run to disprove it.
- `SiteIndexTest` checks the direction the golden cannot see. The golden compares committed bytes to
  generator output, so it only asks about files the list already names; an `.html` in `docs/viz/`
  that no `Page` claims is linked from nowhere and passes everything else. One case per direction.
- All new guards proven red by mutation, then reverted.
- The five viz pages are byte-identical: `git diff docs/viz/` is empty after regeneration.

### Deliberately not in this PR

The visibility flip and the Pages toggle are GitHub settings, not commits, and the hosted
`gimlism.github.io` links are held back until those are done and the URLs are seen working. See the
handover comment below.

This revises D1 of the docs-hub spec (2026-08-05), which assumed the merge and the flip were one
act. They are not — between them, main would advertise 404s on its most-read file.

Spec: `planning/specs/2026-08-09-license-and-pages-design.md`
Plan: `planning/plans/2026-08-09-license-and-site-index.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Post the handover as a PR comment — do not run these commands**

The pre-flight audit in the spec must have been read and accepted before step (a). The flip is the
arc's only irreversible step: forks, GitHub's caches and archive crawlers all survive a re-privatise.

```bash
gh pr comment --body "$(cat <<'EOF'
## Handover — after merge, run by the maintainer

These are GitHub settings, not commits. Run them yourself; the `!` prefix works in a Claude Code
session if you want the output in the transcript.

**(a) Flip visibility.** Irreversible in practice — forks and caches survive a re-privatise. The
pre-flight audit is in the spec: history is clean (six deleted Java sources, nothing else), the
author emails going public are `gimlism@storer-martin.com` and `paulstorermartin@mac.com`, and 71
tracked files under `planning/` become readable by decision, not by accident.

```
gh repo edit gimlism/translucent --visibility public --accept-visibility-change-consequences
```

**(b) Enable Pages** on `main` / `/docs`:

```
gh api -X POST repos/gimlism/translucent/pages -f 'source[branch]=main' -f 'source[path]=/docs'
```

**(c) Wait for the first build, then verify:**

```
gh api repos/gimlism/translucent/pages/builds/latest --jq .status
```

Then in a browser: `https://gimlism.github.io/translucent/` renders the index; the five
visualisations load and step; the guide links render on GitHub. Light and dark.

**(d) Only once (c) is confirmed**, a small follow-up commit adds the hosted links to the README and
sets the repo homepage field. Holding them back is why no dead link ever lands on main.
EOF
)"
```

- [ ] **Step 5: Stop**

Do not run any command from step 4. Report the PR URL and wait.

---

## Self-Review

**Spec coverage:**

| spec item | task |
| --- | --- |
| D1 — MIT, one LICENSE at root | 1 |
| D2 — index generated, not hand-written | 4 |
| D3 — guide links are absolute GitHub blob URLs | 4 (`SiteIndex.GUIDE_BASE`) |
| D4 — `pages()` path-keyed, `Page` record, freshness preserved | 3 |
| D5 — `planning/` stays, README pointer | 5 |
| D6 — hosted URLs in a follow-up commit | 6 step 4(d) |
| D7 — `WebVizTemplate.injectToken`, `injectStatic` delegates | 2 |
| Testing — golden covers index | 3 + 4 (re-keying, then the `pages()` line) |
| Testing — `SiteIndexTest` both directions | 4 |
| Testing — `LicenseTest` targets the licence line | 1 |
| Testing — mutations *run*, not listed | 1 step 6, 2 step 5, 4 step 8 |
| Testing — README FQCN grep | 5 step 3 |
| Testing — viz bytes unchanged | 3 step 4, 4 step 6 |
| Handover — flip, Pages, verify, then links | 6 |

No gaps.

**Type consistency:** `Page(path, title, blurb, guide, html)` is defined in Task 3 and consumed in
Task 4 by those exact accessor names. `SiteIndex.build(List<RegenerateDocs.Page>)` and
`SiteIndex.GUIDE_BASE` are declared in Task 4 step 4 and used in Task 4 step 1's test under the same
names. `WebVizTemplate.injectToken(resource, token, replacement)` is declared in Task 2 and called in
Task 4 with that argument order. `RegenerateDocs.DIR` and `REGENERATE_HINT` keep their names and stay
package-private, which is what `SiteIndexTest` (same package) needs.

**One known ordering constraint:** Task 3 must land before Task 4, because Task 4's `pages()` line
depends on `vizPages()` existing and on `DIR` being `docs`. Tasks 1 and 2 are independent of both and
of each other.
