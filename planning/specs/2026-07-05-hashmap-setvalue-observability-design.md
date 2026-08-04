# Teaching HashMap — `setValue` Observability ("Slice 4") — Design

**Status:** Draft 2026-07-05
**Builds on:** the complete HashMap (PRs #1–3) and its event stream. No data-structure
changes — this closes the one remaining *silent mutation* in the map's public surface,
the gap flagged as future work since the Slice-3 review.

## Goal

Make `Map.Entry.setValue(...)`, called on an entry obtained from `entrySet()`, an
**observable, write-through** mutation: it emits an event and always lands on the live
map. This also fixes the inherited `replaceAll` (silent for the same reason) and removes
a latent *lost-write* hazard.

## The one gap (why it's one bug, not two)

`Node implements Map.Entry`, and `EntryIterator.next()` hands back the raw `Node`. Its
`setValue` writes the `value` field directly:

- **No event** — the map mutates invisibly to listeners and visualizers.
- **Lost write** — treeify / untreeify / resize *copy* a bucket's nodes into fresh
  `TreeNode`/`Node` instances. An entry captured before such a conversion aliases a
  now-detached node; a later `setValue` writes to the orphan, the live map is unchanged,
  and no exception is thrown.

`replaceAll` is silent *only because* its inherited default iterates `entrySet()` and
`setValue`s each entry. **Every other inherited mutator already emits** — `putIfAbsent`,
`replace(k,v)`, `replace(k,ov,nv)`, `merge`, `compute`, `computeIfAbsent`,
`computeIfPresent`, `putAll` (→ `put`), and `clear` (→ entrySet `iterator.remove` →
`remove`) all route through the emitting `put`/`remove`. So the slice reduces to a single
task: **make `setValue` observable and correct.**

Re-finding the live node by key at write time fixes both faces of the bug at once — it
cannot write to a stale node, and it can route through the emitting replace path.

## Resolved decisions

| Question | Decision |
|---|---|
| Event emitted | **Reuse `Put`** — `Put(key, newValue, oldValue, bucketIndex, newEntry=false, after)`. A value replacement is structurally identical to a put over an existing key; the event, the formatter, both visualizers, and the narration model already render it. No new event type. |
| `replaceAll` shape | **A burst of `Put(newEntry=false)`, one per entry.** No override — it falls out of the wrapped `setValue` by construction, consistent with `putAll` being a burst of `Put`s. |
| `setValue` on a vanished key | **Throw `IllegalStateException`.** More honest than the JDK's silent lost-write, and it composes correctly: `Map.replaceAll`'s default wraps a `setValue` ISE into a `ConcurrentModificationException`, so it only ever fires under genuine concurrent modification. |
| `getValue()` | Returns the value **cached at `next()` time** — no live re-read. Keeps a single ISE path (only `setValue` re-finds). |
| `modCount` | **Not bumped** by `setValue` — a value replacement is not a structural change. Load-bearing: it lets `replaceAll` iterate-and-`setValue` every entry without CME-ing its own open iteration. |

## Design

### Component 1 — `LiveEntry`, a write-through wrapper

A private final class `LiveEntry implements Map.Entry<K, V>`:

- Fields: `key` and `cachedValue` (captured at `next()` time).
- `getKey()` → `key`; `getValue()` → `cachedValue`.
- `setValue(v)` → delegates to `setValueThroughEntry(key, v)`, updates `cachedValue` to
  `v`, and returns the value `setValueThroughEntry` reports — the **live node's** old value
  at write time. This can differ from `getValue()`/`cachedValue` if the key was mutated
  through the map since iteration, matching `java.util.HashMap`'s live-entry `setValue`
  semantics (the returned value is the one actually replaced on the live map).
- `equals` / `hashCode` per the `Map.Entry` contract, using `cachedValue`, so
  `entrySet().contains(...)` and set semantics are preserved.

### Component 2 — the map-side write-through

```
private V setValueThroughEntry(K key, V newValue):
    beginMutation()                      // re-entrancy guard, as put/remove
    try:
        Node<K,V> live = findNode(key)   // uniform over chain and tree bins
        if live == null:
            throw new IllegalStateException("entry no longer in map")
        V old = live.value
        live.value = newValue            // NO modCount bump
        int i = indexFor(hash(key), table.length)
        emit(new Put(key, newValue, old, i, false /*newEntry*/, snapshot()))
        return old
    finally:
        dispatcher.endMutation()
```

This mirrors `doPut`'s existing replace branch exactly. `findNode` already dispatches
chain vs. tree bins, and `TreeNode extends Node`, so `live.value = newValue` works on
both. Bracketing in `beginMutation`/`endMutation` means a listener cannot re-enter via
`setValue` during dispatch; each `setValue` brackets itself (no nesting, since
`replaceAll` is inherited and never calls `beginMutation`).

### Component 3 — the iterator change

`EntryIterator.next()` returns `new LiveEntry(lastReturned.key, lastReturned.value)`
instead of the raw `lastReturned`. **`lastReturned` remains a raw `Node` internally**, so
`EntryIterator.remove()` — which unlinks by `lastReturned.key` — is untouched.

### What comes for free

- `replaceAll` → a burst of `Put(newEntry=false)`, no override needed.
- The lost-write hazard is gone: re-find can't write to a detached node.
- `keySet()` / `values()` views are unaffected (they never exposed `setValue`; their
  removal already routes through the emitting `EntryIterator.remove`).

### Docs

Delete the "Known limitation (this slice)" caveat in the `TeachingHashMap` Javadoc
(the paragraph documenting the silent `setValue` / `replaceAll` / lost-write behaviour)
and replace it with a one-line note that entry-view writes are observable and route
through the map's mutators.

## Event-frame check (the recurring bug pattern)

The `Put` here is emitted **after** `live.value` is assigned and while the map is
otherwise fully committed (no size or structural change), so its `snapshot()` shows the
settled state. Consistent with the rule that any mid-operation event must see
fully-committed state before `snapshot()` runs.

## Testing

- **Emits one `Put`:** `setValue` on a live entry emits exactly one `Put` with
  `newEntry=false`, correct `previousValue`, the new value visible in `after()`; the
  return value is the old value.
- **Regression — treeify boundary:** capture an entry, force a treeify, then `setValue`;
  assert the *live* map reflects the write and an event fired (the old orphaned-node
  lost-write repro).
- **Vanished key:** `setValue` after the key was removed throws `IllegalStateException`.
- **`replaceAll`:** emits N `Put(newEntry=false)` events over a populated map and does not
  throw; a `setValue` does **not** bump `modCount` (an open iterator survives one).
- **Re-entrancy:** a listener that calls `setValue` from within event dispatch is rejected
  by the guard.

## Out of scope

- Any data-structure change. This is purely observability + a correctness fix on an
  existing path.
- The "compare to real JDK" mode (which would document this map's honest ISE vs. the
  JDK's silent lost-write as one of its divergences) — separate future work.
