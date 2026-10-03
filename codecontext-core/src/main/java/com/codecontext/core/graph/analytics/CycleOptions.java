package com.codecontext.core.graph.analytics;

/**
 * Safety limits to bound elementary cycle enumeration and prevent NP-hard explosion (ADR-007).
 */
public record CycleOptions(
        int maxTotalCycles,
        int maxCyclesPerComponent,
        int maxCycleLength,
        boolean includeSelfLoops
) {
    public CycleOptions(int maxTotalCycles, int maxCyclesPerComponent, int maxCycleLength) {
        this(maxTotalCycles, maxCyclesPerComponent, maxCycleLength, false);
    }

    public static CycleOptions defaults() {
        return new CycleOptions(50, 10, 10, false);
    }
}
