package com.codecontext.core.git;

import java.util.Objects;

/**
 * Immutable temporal evolution and author entropy metrics for a source file.
 */
public record GitEvolutionMetric(
        String filePath,
        int commitCount,
        int linesAdded,
        int linesDeleted,
        int uniqueAuthors,
        double busFactorRisk
) {
    public GitEvolutionMetric {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        if (commitCount < 0) throw new IllegalArgumentException("commitCount cannot be negative: " + commitCount);
        if (linesAdded < 0) throw new IllegalArgumentException("linesAdded cannot be negative: " + linesAdded);
        if (linesDeleted < 0) throw new IllegalArgumentException("linesDeleted cannot be negative: " + linesDeleted);
        if (uniqueAuthors < 0) throw new IllegalArgumentException("uniqueAuthors cannot be negative: " + uniqueAuthors);
        if (busFactorRisk < 0.0 || busFactorRisk > 1.0) {
            throw new IllegalArgumentException("busFactorRisk must be in [0.0, 1.0], was: " + busFactorRisk);
        }
    }
}
