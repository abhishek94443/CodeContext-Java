package com.codecontext.core.graph.analytics;

/**
 * Tunable options for PageRank centrality computation.
 */
public record PageRankOptions(
        double dampingFactor,
        int maxIterations,
        double tolerance
) {
    public static PageRankOptions defaults() {
        return new PageRankOptions(0.85, 100, 1e-5);
    }
}
