/**
 * Structure-neutral red-black tree rebalancing kernel. The rotation, recolor, and
 * insert/delete fixup math operate over a self-typed {@link RbNode} and report
 * changes through an {@link RbEventSink}. Ordering, search, and node identity are
 * supplied by each consumer (e.g. {@code TeachingTreeSet}); only the rebalancing is
 * shared. First consumer: the TreeSet. The HashMap's own red-black tree is a
 * deferred migration candidate onto this kernel.
 */
package com.gimlism.translucent.substrate.rbtree;
