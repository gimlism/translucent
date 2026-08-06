# ArrayList

A growable array: an object array plus a size, copied into a bigger array when it fills.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `add(element)` | `Grow`? → `Append` |
| `add(index, element)` | `Grow`? → `Shift`×n → `Insert` |
| `set(index, element)` | `Set` |
| `remove(index)` | `Shift`×n → `RemoveAt` |
| `get(index)` | nothing — reads are silent |

`Grow` fires only when the backing array is full: capacity is not a property of the list you asked
for, which is the point of watching it. `Shift` fires once per element moved, so inserting at the
front of a long list is visibly more work than appending to it.

Reads being silent here is not a rule that holds across the library — `TeachingTreeSet.contains`
narrates every comparison it makes. See `docs/guide/treeset.md`.

## Read alongside

- `src/main/java/com/gimlism/translucent/arraylist/core/TeachingArrayList.java` — the algorithm
- `src/main/java/com/gimlism/translucent/arraylist/events/` — the six event types

## Watch it

- `docs/viz/list.html` — open in a browser, no JDK needed
- demos 1–6 in the [README](../../README.md#arraylist--a-growable-array)
