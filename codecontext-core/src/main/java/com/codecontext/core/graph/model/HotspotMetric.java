package com.codecontext.core.graph.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Architectural gravity and centrality metrics for a single type vertex computed via PageRank.
 */
public record HotspotMetric(
        String fqcn,
        Optional<Path> filePath,
        double rawScore,
        double percentileScore,
        int inDegree,
        int outDegree,
        int rank
) {
    public HotspotMetric {
        Objects.requireNonNull(fqcn, "fqcn cannot be null");
        filePath = filePath == null ? Optional.empty() : filePath;
        if (rawScore < 0.0) {
            throw new IllegalArgumentException("rawScore cannot be negative: " + rawScore);
        }
        if (percentileScore < 0.0 || percentileScore > 100.0) {
            throw new IllegalArgumentException("percentileScore must be in [0.0, 100.0]: " + percentileScore);
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be >= 1: " + rank);
        }
    }

    public HotspotMetric(String fqcn, Path path, double rawScore, double percentileScore, int inDegree, int outDegree, int rank) {
        this(fqcn, Optional.ofNullable(path), rawScore, percentileScore, inDegree, outDegree, rank);
    }

    public static HotspotMetric of(String fqcn, Path path, double rawScore, double percentileScore, int inDegree, int outDegree, int rank) {
        return new HotspotMetric(fqcn, Optional.ofNullable(path), rawScore, percentileScore, inDegree, outDegree, rank);
    }
}
