# Fable Full-Repo Review — `translucent` @ main

**Date:** 2026-07-04
**Reviewer:** Fable (four parallel reviewers — correctness, consistency, design/architecture, test coverage), synthesized.
**Scope:** the whole repository at `main` after four merged slices (HashMap, ArrayList, generic substrate, radix trie). 80 source files, 179 tests.
**Method:** each dimension reviewed the whole repo through its lens. The correctness reviewer additionally built differential/invariant fuzzers (750 HashMap seeds × 400 ops vs `java.util.HashMap`; 1000 radix-trie seeds vs `TreeMap`; 300 ArrayList seeds vs `java.util.ArrayList`), validating structure invariants and per-event snapshot content after **every** operation.

## Verdict

**No algorithmic correctness bugs.** The red-black engine (`TreeNode` insert/delete/fixup/split) and the radix core (split/merge/`keysWithPrefix`) held every invariant under fuzzing, differentially checked against the JDK. Dependency hygiene is flawless (whole import graph verified one-way: each structure → itself + `substrate`; `substrate` → nothing; no cross-structure imports). The deliberate non-decisions — not unifying the drawing layer, keeping event vocabularies concrete/sealed, the pure-marker `StructureSnapshot` — were all judged correct, and the trie vindicated the marker (`TrieSnapshot(root, size)` has no capacity and fit fine).

The substantive findings are about **listener-exception safety**, **cross-structure consistency drift** (the trie, as the youngest sibling, is the recurring odd-one-out), **one worthwhile extraction** (`EventDispatcher`), and **event-frame test coverage**.

---

## 1. Correctness

### C1 — [Medium] A throwing listener silently and permanently corrupts the structure
`hashmap/core/TeachingHashMap.java` (`emit`) · `arraylist/core/TeachingArrayList.java` (`remove`/`insertInternal`) · `trie/core/RadixTrie.java` (`remove`)

Events are dispatched **mid-operation** by design, and `emit` is **not exception-isolated**. The re-entrancy guard blocks re-entrant *mutation* but not *exceptions*. If a listener throws mid-burst, the operation aborts half-done and the structure is left corrupt with no subsequent fail-fast.

Verified scenarios:
- HashMap: default map, listener throwing on the first `Rotation` during treeify; `put(0);put(8);put(16);put(24)` → the 4th put throws, then `size()==4` but `get(24)==null`/`containsKey(24)==false`, while the entrySet iterator still yields 4 entries incl. 24. Retrying `put(24, …)` appends a **second** node with key 24 (`size()==5`, iterator yields both) — a permanent Map-contract violation, no exception afterwards.
- ArrayList: `[a,b,c,d]`, listener throwing on the first `Shift` of `remove(0)` → `[b,b,c,d]`, `size()==4` ("a" lost, "b" duplicated).
- Trie: throwing on `Remove` aborts prune/merge → a non-canonical trie the events claim can't exist (representational only; reads stay correct).

Triggered only by a *misbehaving* (throwing) listener — user error — but silent, permanent, and undocumented. **Options:** document "listeners must not throw"; or isolate per-listener in `emit` (catch); or emit-after-settle. Apply uniformly across all three cores. (Deferred — batch #3.)

**Everything else in correctness came back clean** (explicitly verified: RB insert/delete/fixup incl. null-`x` tracking and both mirrors, treeify/untreeify/`splitTreeBin`, fail-fast iterators incl. resize-during-iteration and iterator.remove-through-untreeify, ArrayList growth/overflow-clamp, radix split/merge/prefix, substrate covariance, `Palette.decideMode`).

---

## 2. Consistency (the trie is the recurring odd-one-out)

- **CO1 — [High] `Map.Entry.setValue` has three behaviors.** HashMap hands out live nodes → `setValue` *silently lost* after a treeify/untreeify copy (no event); trie throws `UnsupportedOperationException`; ArrayList `set` is fully instrumented. Both map & trie document their limitation but chose **opposite** failure modes. (Deferred — relates to batch #4/coverage.)
- **CO2 — [Medium] Trie `Remove` *leads* its burst; every other logical marker *trails*.** So trie `Remove.after()` shows a pre-compression intermediate while HashMap/ArrayList markers show settled state. Documented, but no rationale for reversing the convention; compounded by the remove *walk* being silent while the put walk narrates via `Descend`. (Deferred — ties to design D3.)
- **CO3 — [Medium] `TrieSnapshot` skips the compact-constructor validation** its siblings enforce (`MapSnapshot`: `buckets.size()==capacity`; `ListSnapshot`: slot count + `0<=size<=capacity`). Accepts null root / negative size. **→ FIXING NOW (batch #1).**
- **CO4 — [Low] Trie logical markers lack a locus.** Every *mechanical* trie event carries a `path`; `Put`/`Remove` don't, so a renderer can highlight the affected node for every event except the two most important. Also `Put.newEntry` (map) vs `newKey` (trie) drift. **→ FIXING NOW (path on Put/Remove; batch #1).**
- **CO5 — [Low] Formatter divergences.** Trie quotes keys/labels (defensible — empty-string labels/keys exist) while map/list don't; `MergeEdge` is the only formatter case that drops a record field (`path`) while its sibling `Prune` prints it; trie formatter lacks the "lives in the events package" layering Javadoc. **→ FIXING NOW (MergeEdge path + layering doc; batch #1).**
- **CO6 — [Low] Console-logger Javadoc now contradicts across siblings.** A prior fix set the trie logger to "formatting only; no invariant checking" while map & list still claim "Validates the stream end to end" (the accurate phrasing is the trie's). **→ FIXING NOW (batch #1).**
- **CO7 — [Low] `modCount` bumped at a different point per structure**, so a listener holding an iterator observes different fail-fast timing. Harmless today. (Deferred — subsumed by `EventDispatcher`, batch #2.)

**Pleasingly consistent** (called out): the re-entrancy guard, non-structural value-replace, listener plumbing, iterator `remove`, and the `EmptyBucket`/`EmptySlot` singletons are near-byte-identical across structures.

---

## 3. Design / Architecture

- **D1 — [Medium-High] Extract an `EventDispatcher<E>` into the substrate.** The listener list + copy-on-dispatch `emit` + `mutating` guard + `beginMutation` is hand-transcribed ~30 lines **verbatim in all three cores** — and it *is* transport, the exact thing the substrate exists to hold. Already drifting (three exception messages; HashMap's `forceResize` test hook emits outside the guard). Recommend **composition** (not a base class — cores already extend AbstractMap/AbstractList): `dispatcher.emit(...)`, `dispatcher.beginMutation(noun)`. Reads better for teaching; a 4th structure stops re-transcribing the subtlest invariants by hand. (Deferred — batch #2.)
- **D2 — [Medium] Naming asymmetry / collision.** HashMap kept unprefixed names (`RecordingListener`, `EventFormatter`, `AsciiRenderer`) — `hashmap.consumer.RecordingListener` *collides* with `substrate.events.RecordingListener` — while list/trie are prefixed. Teaching repo, no external callers → rename to `Map*` for symmetry. (Deferred — batch #5.)
- **D3 — [Medium] Decide what an "event" is.** `Descend` is the repo's only no-delta event (`after()` == prior frame) — narration, contradicting `TrieEvent`'s own "state change" Javadoc — and the put walk narrates while the remove walk is silent. Either embrace narration (document on `StructureEvent`; add `Descend` to the remove walk) or drop it. Leaks upward to every future structure. (Deferred — batch #5, opinionated.)
- **D4 — [Low] Trie viz layer absent** (renderer/visualizer/replayer/vizdemo). This is the **already-planned next slice**, not an oversight; the substrate's `EventRenderer` seam is simply still unproven for the N-ary case (which that slice validates). `Palette`'s terminal detection is generic infra trapped in `hashmap.viz` (move only when a 2nd renderer wants color). Consolidate the event-grammar conventions into `substrate/package-info` as the "recipe" for a new vocabulary.

**Well-designed (called out):** `StructureEvent.after()` earns its keep as a typed contract; `TreeEventSink` is a model-internal seam done right; the `SubstrateReuseTest` "one generic `renderAll` drives both structures" is the right kind of proof; snapshot-per-event scales coherently across bucket/chain/tree, cells, and N-ary labelled edges because each structure owns its snapshot vocabulary.

---

## 4. Test coverage (gates are inverted in strength)

The ArrayList is the gold standard (exact event grammars + mid-slide frame contents). The RB tree is adversarially gated at the **unit** level but not the **map** level; the trie is gated at the **structure** level but its **event stream — the whole point — is only label-checked, never frame-checked.**

| Sev | Gap (a regression that stays green) | Location |
|---|---|---|
| High | **Trie event `after()` snapshots + `path` fields never asserted** — tests tag by label, drop `path()`, and the invariant checker uses a *fresh* `snapshot()`, not the event-carried one. | `RadixTrie` emit sites |
| Med | Chain **insertion order across resize** unpinned (head-prepend regression → green). | `TeachingHashMap` resize |
| Med | **Treeify announce-frame / Untreeify settle-frame contents** unasserted. | `TeachingHashMap` treeify/untreeify |
| Med | **RB invariants never checked on live `map.table[i]`** post-treeify / tree-put / RB-delete / `splitTreeBin` (color-only corruption survives `get`). | map-level RB |
| Med | **Auto-resize from the tree-bin put path** untested (delete the line → green). | `TeachingHashMap` doPut tree branch |
| Med | **`setValue`** entirely unpinned (map silent-loss + trie UOE). | map & trie entrySet |
| Med | **Null key in a treeified bucket** never exercised (all treeify tests use Integer keys in bucket 0). | tree bins |
| Low | Replayer edge inputs (`b` at frame 0 → would `IndexOutOfBounds` without the clamp; empty stream; next-past-end). | `substrate/viz/Replayer` |
| Low | Tied-hash keys treeified through the map API; inherited `putAll`/`equals`/`hashCode`; `keysWithPrefix` on empty trie / `""`-key; `resize()` at `MAXIMUM_CAPACITY` (unreachable + unextracted, unlike the list's testable `newCapacity`). | various |

(Deferred — batch #4, template from the ArrayList's exact-grammar + mid-frame tests.)

---

## Recommended sequencing

1. ✅ **Quick consistency batch** — reconcile console-logger Javadocs (CO6); add `TrieSnapshot` validation (CO3); give trie `Put`/`Remove` a `path` (CO4); fix `MergeEdge` formatter dropping `path` + add layering Javadoc (CO5). *(PR #11, merged.)*
2. ✅ **`EventDispatcher<E>` extraction** (D1) — the one real design improvement; also resolves CO7 and the exception-message drift. *(PR #12, merged.)*
3. ✅ **Listener-exception safety** (C1) — chosen: document-only (the contract now lives on `EventDispatcher`/`StructureEventListener`). *(PR #12, merged.)*
4. ✅ **Test-coverage hardening** (§4) — closed the trie event-frame gap (highest value), plus live-RB-bin invariants, tree-bin-path auto-resize, resize chain-order, treeify/untreeify frames, null key in a tree bin, `setValue`, `putAll`, `keysWithPrefix` edges, and `Replayer` input edges. *(This pass; +17 tests.)* Not closed (deliberate): `resize()` at `MAXIMUM_CAPACITY` (needs a `newCapacity`-style extraction to be testable — a production change, deferred).
5. ⬜ **Naming symmetry rename** (D2) + the **`Descend`/narration decision** (D3) — more opinionated; worth a discussion. Several D4 items fold naturally into the planned trie-visualizer slice.
