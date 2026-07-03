/**
 * Teaching ArrayList with event instrumentation.
 *
 * <p>An instrumented dynamic array for teaching: it mirrors the structurally
 * important parts of {@code java.util.ArrayList} (capacity vs size, 1.5× amortized
 * growth with lazy allocation, O(n) shift on insert/remove) and emits an immutable
 * event on every internal state change. Sibling of the teaching HashMap; shares no
 * types with it (the generic substrate is extracted in a later refactor slice).
 */
package com.gimlism.translucent.arraylist;
