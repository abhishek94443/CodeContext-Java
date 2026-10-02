package com.codecontext.core.graph;

/**
 * Configuration options for building the in-memory dependency graph.
 */
public record GraphBuildOptions(
        boolean filterBoundaryNodes
) {
    public static GraphBuildOptions defaults() {
        return new GraphBuildOptions(false);
    }

    public static GraphBuildOptions excludeBoundaryNodes() {
        return new GraphBuildOptions(true);
    }
}
