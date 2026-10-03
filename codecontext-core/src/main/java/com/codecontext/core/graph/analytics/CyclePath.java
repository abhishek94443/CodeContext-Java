package com.codecontext.core.graph.analytics;

import java.util.List;
import java.util.Objects;

/**
 * Immutable cycle path representing a circular invocation chain (e.g. A -> B -> C -> A).
 */
public record CyclePath(
        List<String> fqcns,
        int length
) {
    public CyclePath {
        Objects.requireNonNull(fqcns, "fqcns cannot be null");
        if (fqcns.size() < 2) {
            throw new IllegalArgumentException("A cycle path must contain at least 2 entries. Size was: " + fqcns.size());
        }
        fqcns = List.copyOf(fqcns);
    }

    public CyclePath(List<String> fqcns) {
        this(fqcns, fqcns.size() - 1);
    }
}
