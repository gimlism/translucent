# translucent

Simple runtime monitoring of internal object state to facilitate teaching sessions.

Five data structures — **ArrayList**, **HashMap**, **TreeSet**, and two tries: a **standard trie**
and the **radix trie** that compresses it — implemented for teaching, each narrating what it does as
it does it. The narration is emitted by the real
`put()`/`add()`/`remove()` paths, so what you watch is the algorithm running, not an animation of
it.

## Watch one without installing anything

**→ [gimlism.github.io/translucent](https://gimlism.github.io/translucent/)**

That landing page links everything below. No JDK, no Maven, no server, nothing to download: each
page carries its own data and styling.

Or open one directly:

| page | structure |
| --- | --- |
| [list](https://gimlism.github.io/translucent/viz/list.html) | ArrayList |
| [map](https://gimlism.github.io/translucent/viz/map.html) | HashMap |
| [treeset](https://gimlism.github.io/translucent/viz/treeset.html) | TreeSet |
| [standard-trie](https://gimlism.github.io/translucent/viz/standard-trie.html) | Trie (standard) |
| [trie](https://gimlism.github.io/translucent/viz/trie.html) | Trie (radix) |
| [compression-compare](https://gimlism.github.io/translucent/viz/compression-compare.html) | a fat trie beside its compressed form |

Each replays a real run: step through it, play it, scrub back. The events were recorded from the
actual `put()`/`add()` path, not scripted for the page.

Already cloned the repo? The same six pages are committed under `docs/viz/`, and `docs/index.html`
is the same landing page — they open straight from disk, offline.

Everything below needs a JDK.

## Quick start

Requires **JDK 21** and **Maven**.

```
mvn exec:java
```

That opens the launcher: a numbered index of every demo below. Pick a number and it runs — and it
prints the direct command first, so you can skip the menu next time.

## Six ways to watch

Each structure offers the same six modes.

| mode | what you get |
| --- | --- |
| text log | one line per event, in the terminal |
| ASCII replay | the structure redrawn in the terminal after every event |
| web replay | a self-contained `.html` file you can step and play through |
| live web | a browser view that follows the structure as the demo mutates it |
| terminal REPL | you type commands; the browser redraws as you go |
| browser REPL | the same, but you type into the page — no stdin |

## Every demo

Prefix each with `mvn exec:java `. For example:

```
mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieVizDemo
```

### ArrayList — a growable array

| # | mode | |
| --- | --- | --- |
| 1 | text log | `-Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListDemo` |
| 2 | ASCII replay | `-Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListVizDemo` |
| 3 | web replay | `-Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListWebVizDemo` |
| 4 | live web | `-Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveWebVizDemo` |
| 5 | terminal REPL | `-Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveReplDemo` |
| 6 | browser REPL | `-Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveControlsDemo` |

📖 [What each ArrayList method makes you see](docs/guide/list.md)

### HashMap — buckets, chains, and a red-black tree when a chain gets long

| # | mode | |
| --- | --- | --- |
| 7 | text log | `-Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapDemo` |
| 8 | ASCII replay | `-Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapVizDemo` |
| 9 | web replay | `-Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapWebVizDemo` |
| 10 | live web | `-Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapLiveWebVizDemo` |
| 11 | terminal REPL | `-Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapLiveReplDemo` |
| 12 | browser REPL | `-Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapLiveControlsDemo` |

📖 [What each HashMap method makes you see](docs/guide/map.md)

### TreeSet — a red-black tree that rotates and recolours to stay balanced

| # | mode | |
| --- | --- | --- |
| 13 | text log | `-Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetDemo` |
| 14 | ASCII replay | `-Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetVizDemo` |
| 15 | web replay | `-Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetWebVizDemo` |
| 16 | live web | `-Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveWebVizDemo` |
| 17 | terminal REPL | `-Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveReplDemo` |
| 18 | browser REPL | `-Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveControlsDemo` |

📖 [What each TreeSet method makes you see](docs/guide/treeset.md)

### Trie (standard) — one node per character, and a prune cascade when keys leave

| # | mode | |
| --- | --- | --- |
| 19 | text log | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieDemo` |
| 20 | ASCII replay | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieVizDemo` |
| 21 | web replay | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieWebVizDemo` |
| 22 | live web | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveWebVizDemo` |
| 23 | terminal REPL | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveReplDemo` |
| 24 | browser REPL | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveControlsDemo` |

### Trie (radix) — edges split and merge as keys arrive and leave

| # | mode | |
| --- | --- | --- |
| 25 | text log | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieDemo` |
| 26 | ASCII replay | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieVizDemo` |
| 27 | web replay | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieWebVizDemo` |
| 28 | live web | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieLiveWebVizDemo` |
| 29 | terminal REPL | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieLiveReplDemo` |
| 30 | browser REPL | `-Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieLiveControlsDemo` |

📖 [What each Trie method makes you see](docs/guide/trie.md)

### Trie compression — how much a radix trie actually saves

| # | mode | |
| --- | --- | --- |
| 31 | standard vs radix, side by side | `-Dexec.mainClass=com.gimlism.translucent.trie.compare.CompressionCompareDemo` |

## How each slice got decided

`planning/` holds the spec and the implementation plan written before each slice was built, plus one
full-repo review. They are a record of how decisions were reached — including approaches that were
considered and rejected — and they are dated for that reason. Where a plan disagrees with the source,
the source is right.

## Running the tests

```
mvn test
```

`LauncherCatalogTest` resolves every class named above, and `LauncherReadmeTest` checks this table
lists them all — so a renamed or missing demo fails the build rather than silently sending you at a
class that isn't there.

## Licence

Licensed under the MIT License — see [LICENSE](LICENSE).

The data structures are reimplementations written from the published algorithms, not adaptations of
any JDK source: the thresholds are deliberately scaled down so a treeify or a resize is reachable in
a demo you can watch.
