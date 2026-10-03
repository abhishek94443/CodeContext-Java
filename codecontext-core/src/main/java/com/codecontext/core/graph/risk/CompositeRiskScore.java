package com.codecontext.core.graph.risk;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Unified 0-100 composite architectural and evolutionary risk score.
 */
public record CompositeRiskScore(
        String fqcn,
        Optional<Path> filePath,
        double overallScore,
        RiskLevel level,
        double pageRankFactor,
        double churnFactor,
        double cycleFactor,
        double busFactor
) {
    public CompositeRiskScore {
        Objects.requireNonNull(fqcn, "fqcn cannot be null");
        filePath = filePath == null ? Optional.empty() : filePath;
        Objects.requireNonNull(level, "level cannot be null");
        if (overallScore < 0.0 || overallScore > 100.0) {
            throw new IllegalArgumentException("overallScore must be in [0.0, 100.0], was: " + overallScore);
        }
    }
}
