package com.gimlism.translucent.hashmap.core;

import java.util.Map;
import java.util.Objects;

/**
 * Shared {@link Map.Entry} {@code equals}/{@code hashCode}/{@code toString} for the
 * node types. {@link ChainNode} and {@link TreeNode} no longer share a class base
 * and these Object-derived methods cannot be interface {@code default}s, so both
 * delegate here to avoid a second copy.
 */
final class Entries {
    private Entries() { }

    static boolean equals(Map.Entry<?, ?> self, Object o) {
        if (!(o instanceof Map.Entry<?, ?> e)) return false;
        return Objects.equals(self.getKey(), e.getKey())
                && Objects.equals(self.getValue(), e.getValue());
    }

    static int hashCode(Map.Entry<?, ?> self) {
        return Objects.hashCode(self.getKey()) ^ Objects.hashCode(self.getValue());
    }

    static String toString(Map.Entry<?, ?> self) {
        return self.getKey() + "=" + self.getValue();
    }
}
