package com.codecontext.core.graph.analytics;

import java.util.List;
import java.util.Objects;

/**
 * Summary of circular dependency analysis across the codebase.
 */
public record CycleReport(
        List<CyclePath> cycles,
        int totalComponents,
        boolean isTruncated
) {
    public CycleReport {
        Objects.requireNonNull(cycles, "cycles cannot be null");
        cycles = List.copyOf(cycles);
    }

    public boolean hasCycles() {
        return !cycles.isEmpty();
    }
}
