# Licence and site index — the last slice before the repo goes public

**Date:** 2026-08-09
**Slice:** Student on-ramp arc, PR E (final slice; closes the arc)
**Depends on:** #45 (demo grid), #46 (Launcher + README), #47 (baked `docs/viz/` pages), #48 (docs
hub) — all merged
**Branch:** `feat/license-and-site-index`

## Why

The arc built two student tiers — open an HTML file with no toolchain, or run a demo with a JDK —
and #48 connected both to the source. What is left is the step the whole arc was staged for: making
the repo reachable by someone who is not the maintainer.

That step has two halves, and only one of them is code:

1. **The repo has no licence.** `gh repo view` reports `licenseInfo: null`. A public repo with no
   LICENSE is not open source — default copyright reserves all rights, so a student who clones it to
   learn from has no grant to do so. This is a blocker for publishing, not a nicety.
2. **GitHub Pages would serve a 404 at its own root.** `docs/` holds `.nojekyll`, five baked viz
   pages and four guides, but no `index.html`. Pages does not generate directory listings, so
   `gimlism.github.io/translucent/` fails while `/translucent/viz/list.html` works. Every entry
   point would have to be a full deep link — including the repo's own homepage field.

## Scope

Ships:

| file | status |
| --- | --- |
| `LICENSE` | new — MIT |
| `src/main/resources/web/site-index.html` | new — landing-page template |
| `src/main/java/com/gimlism/translucent/SiteIndex.java` | new — builds the landing page |
| `src/main/java/com/gimlism/translucent/RegenerateDocs.java` | edited — `Page` record, `DIR`→`docs` |
| `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java` | edited — `injectToken` |
| `docs/index.html` | new — generated, committed |
| `src/test/java/com/gimlism/translucent/DocsPagesGoldenTest.java` | edited — path-keyed |
| `src/test/java/com/gimlism/translucent/SiteIndexTest.java` | new — guide coverage guard |
| `src/test/java/com/gimlism/translucent/LicenseTest.java` | new — LICENSE ↔ README guard |
| `README.md` | edited — licence line, `planning/` pointer, index promoted |

Explicitly out of scope: the visibility flip and the Pages toggle themselves (both are GitHub
settings, run by the maintainer, not commits); the `gimlism.github.io` links (see D6); rendering the
guides to HTML (D3); any change to the five viz pages' bytes.

## Pre-flight audit — what the flip actually exposes

Run before any of the decisions below, because the flip is the arc's only irreversible step: forks,
GitHub's caches and archive crawlers survive a re-privatise. Findings:

| surface | result |
| --- | --- |
| Deleted-file history (`--diff-filter=D` over `--all`) | Six Java sources from refactors. No credentials, no stray docs. |
| Author emails in history | `gimlism@storer-martin.com`, `paulstorermartin@mac.com` — both the maintainer's own. |
| `planning/` (tracked) | 71 markdown files: 35 plans, 35 specs, 1 full-repo review. All technical. |
| AI provenance | 285 `Co-Authored-By: Claude…` trailers. Deliberate, and not removable without a history rewrite. |

**No blocker.** The one judgement call is `planning/`, decided in D5.

★ **Why PR C's fix does not cover this.** #47 moved `docs/superpowers/` → `planning/` because Pages
would have published those docs. That addressed *Pages publication*. Visibility is a second,
independent switch: it exposes everything tracked, `docs/` or not. Treating the earlier move as
having settled the question is the trap this audit exists to avoid.

## Decisions

### D1 — MIT, one LICENSE at the repo root, covering code and prose

The structures are independent reimplementations of published algorithms, not adaptations of
OpenJDK source. The evidence is in the constants: `DEFAULT_TREEIFY_THRESHOLD` is 4 against
OpenJDK's 8, `DEFAULT_MIN_TREEIFY_CAPACITY` 8 against 64, initial capacity 8 against 16 — all
scaled so a treeify is reachable in a demo you can watch. `TeachingHashMap` extends `AbstractMap`
where `java.util.HashMap` implements `Map` directly, and no file carries an "adapted from" marker.
So OpenJDK's GPL-with-Classpath-Exception does not reach this code, and a permissive licence is
available.

MIT over Apache-2.0: the patent grant and NOTICE machinery buy nothing for teaching implementations
of algorithms published decades ago, and brevity is worth real points in a repo whose thesis is
approachability. Rejected: splitting a content licence (CC BY-SA) over `docs/` — it would mean two
licences to reason about, and CC licences lack the warranty and liability terms source needs.

### D2 — the landing page is generated from the page list, not hand-written

`docs/index.html` is produced by `RegenerateDocs` from the same list that produces the five viz
pages, and is pinned by `DocsPagesGoldenTest` like they are.

★ **This is the arc's staleness rule applied to the one file that would otherwise escape it.** A
hand-written index is invisible to the build: add a sixth page to `pages()` and `RegenerateDocs`
writes it, the golden passes, and the index silently lists five. Every other student-facing artifact
in this repo is pinned to its generator — decision 8 of the arc put explanation in Javadoc precisely
because Markdown has no verification. A hand-maintained index would reintroduce exactly that hole,
on the commit that turns the site on.

Rejected: deep links only, no index — the root URL is what people reach by trimming a path or
following the repo homepage field, and GitHub's generic 404 is a poor first contact for a repo
selling approachability.

### D3 — guide links point at GitHub blob URLs, absolute

`.nojekyll` means Pages serves `docs/guide/*.md` as raw markdown: browsers show plain text or offer
a download. So the index cannot link them relatively.

Absolute `github.com/gimlism/translucent/blob/main/docs/guide/*.md` links render properly, get
heading anchors and a table of contents for free, and — because they are absolute — behave
identically whether the index was opened from Pages or from `file://` off a clone. The relative viz
links (`viz/list.html`) work in both contexts too, so the generated page has exactly one behaviour.

Rejected: rendering guides to HTML — it means owning a markdown renderer or adding a second
dependency to a repo that currently has exactly one, test-scoped. Rejected: dropping `.nojekyll` and
letting Jekyll render — it puts Jekyll in the path of the five hand-baked self-contained pages that
`.nojekyll` was added to protect, risking what already works to fix prose rendering.

### D4 — `pages()` becomes path-keyed, and gains a `Page` record

Two changes to `RegenerateDocs`, both in service of one map staying the single source of truth:

- `DIR` moves from `docs/viz` to `docs`, and `pages()` keys become paths relative to it
  (`viz/list.html` … `index.html`). `DIR.resolve(key)` then addresses both the viz pages and the
  index, so `main()` keeps one loop and `DocsPagesGoldenTest` keeps one parameterised test — the
  index inherits the byte-for-byte guard with no new test.
- A `Page` record carries what a filename cannot: `path`, display title, one-line blurb, and the
  guide it belongs to (`null` for compression-compare, which has none). `pages()` derives from the
  `Page` list; `SiteIndex` reads the same list. Adding a structure is one entry, and both the baked
  page and its index row follow.

★ **Preserved invariant.** `DocsPagesGoldenTest.pageNames()` carries a comment explaining it calls
`pages()` fresh per assertion so first-call output is compared against later-call output — that gap
is what catches a generator holding lazily-initialised static state. The restructuring keeps
`pages()` a live call per assertion; it must not be hoisted into a cached field.

### D5 — `planning/` stays public, with a README pointer

The 71 design docs are the record of how each slice was decided before it was built. In a repo whose
purpose is teaching, that trail is arguably more instructive than the code, and nothing in it is
sensitive — the one review doc is a technical assessment with no remarks about people.

The pointer exists because the failure mode is misreading, not exposure: a July spec describing a
rejected approach reads as current documentation to someone who does not know it is dated. One
README line naming `planning/` as a historical record fixes that.

Rejected: `git rm --cached planning/` — it cleans the tip only. Every historical commit still
carries the files, so it hides nothing from anyone who clones, while costing the trail's value.

### D6 — the hosted URLs land in a second commit, after Pages is confirmed live

Pages on a private repo is a paid feature, so `gimlism.github.io/translucent/` does not resolve
until after the flip. PR E therefore ships local paths only. Once the maintainer flips visibility
and enables Pages, and the live URLs are verified in a browser, a small follow-up commit swaps in
the hosted links and sets the repo homepage field.

★ **This revises D1 of the docs-hub spec** (2026-08-05), which anticipated E appending the hosted-URL
line in the same commit as the flip. That assumed the flip and the merge were one act. They are not:
the merge is a commit, the flip is a GitHub setting, and between them main would advertise 404s on
its most-read file. Splitting costs one extra commit and removes the dead window entirely.

### D7 — `WebVizTemplate` gains a generic `injectToken`

`SiteIndex` needs one token substituted in a classpath template — the same read-and-require logic
`WebVizTemplate` already owns. `injectToken(resource, token, replacement)` is added, and the
existing `injectStatic` delegates to it with `/*__DATA__*/`, leaving its output bytes unchanged and
all five viz goldens byte-identical.

Rejected: a private reader inside `SiteIndex` — cleaner layering on paper (the index is site
furniture, not a visualisation), but it is a second hand-transcribed copy of read-and-require, which
is finding D1 of the Fable review in miniature. One reader, one missing-token error message.

The index's token is `<!--__PAGES__-->`, an HTML comment rather than the viz pages' `/*__DATA__*/`,
because the substitution lands in markup rather than inside a `<script>`.

## The landing page

Self-contained, theme-aware, no external requests — the same constraints as the five viz pages, for
the same reason: it must work from `file://` off a clone, not just from Pages.

Content, in order: the repo name and one line of what it is; the five viz pages with their blurbs,
under a heading naming the no-install promise; the four guides as GitHub links; a link to the
source. Nothing that duplicates the README's demo grid — the index is the zero-toolchain tier's
front door, and a student who wants commands is one click from the README.

## README changes

Additive, in the style #48 established:

1. **`docs/index.html` promoted** as the single "start here" file in the existing zero-toolchain
   section. The five-row table stays — the index is the front door, not a replacement for the map.
2. **A `planning/` pointer**, per D5.
3. **A licence line** naming MIT and linking `LICENSE`.

★ **`LauncherReadmeTest` constrains the prose.** Its second test regex-scans the README for
`com\.gimlism\.translucent\.[A-Za-z0-9_.]+` and asserts every hit is in `Launcher.CATALOG`. So the
README must not name `RegenerateDocs` — it is a tool, not a catalogued demo, and mentioning it to
explain regeneration would fail the build. Regeneration is a maintainer concern; it stays in
`RegenerateDocs`' own Javadoc, where #47 already documented it.

The 25-row demo grid stays byte-unchanged, keeping this PR's diff clear of that guard.

## Testing

- **`DocsPagesGoldenTest`** — now covers six pages including `docs/index.html`, via D4's path keys.
  No new test needed for the index's bytes.
- **`SiteIndexTest`** — every `docs/guide/*.md` present on disk appears on the generated index. This
  is the guard D4's list cannot provide: the `Page` list knows the guides it was told about, so only
  a filesystem check catches a fifth guide being added and never linked.
- **`LicenseTest`** — `LICENSE` exists, is MIT (asserted on the permission grant text, not just the
  title line), and the README's licence claim names the same licence.
- **Non-vacuity by mutation**, per the arc's standing rule. Each new guard must be proven to fail:
  drop a guide from the `Page` list with the file still on disk (expect red); rename `LICENSE`
  (expect red); change the README's licence name (expect red). All reverted before commit.
- **`LauncherReadmeTest` stays green** — it parses the file this PR edits.
- **Byte-identity check on the five viz pages** — `git diff` must show `docs/viz/` untouched after
  regeneration, proving D7's delegation and D4's re-keying changed no output.
- Full suite green. Baseline on merged main at the time of writing: **554 tests, 0 failures**.

## Handover — the steps that are not commits

Run by the maintainer, in this order, after PR E merges. The visibility flip is gated on the
pre-flight audit above having been read and accepted.

1. Flip visibility to public.
2. Enable Pages: source `main`, path `/docs`.
3. Verify in a browser: the site root renders the index, each of the five viz links loads and steps,
   each guide link renders on GitHub. Light and dark.
4. Follow-up commit: hosted links into the README, homepage field set.

## Risks

| risk | mitigation |
| --- | --- |
| Re-keying `pages()` silently changes a viz page's bytes | `git diff docs/viz/` after regeneration must be empty; the five goldens also still compare against their own generators |
| The index looks unlike the pages it links to | Template shares the viz pages' visual language and theme handling; verified in a browser on both themes before merge, as #47's pages were |
| `file://` behaves differently from Pages | Relative viz links and absolute guide links both resolve in either context by construction; the maintainer verifies `file://` (Chrome MCP refuses that scheme, so this check cannot be automated) |
| Pages serves stale content after the flip | Verification step 3 happens before the follow-up commit, so the links only ship once observed working |
| The flip exposes something the audit missed | Audit covers tree, history, deleted paths and authors; `planning/` is the one judgement call and is decided explicitly in D5 rather than by default |
