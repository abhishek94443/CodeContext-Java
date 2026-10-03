package com.codecontext.core.graph.analytics;

import java.util.*;

/**
 * Result of blast radius impact analysis on a target component.
 */
public record ImpactAnalysisResult(
        String targetFqcn,
        Set<String> directCallers,
        Map<String, Integer> transitiveCallerDepths,
        double cumulativeRisk,
        List<String> recommendedTestScope
) {
    public ImpactAnalysisResult {
        Objects.requireNonNull(targetFqcn, "targetFqcn cannot be null");
        directCallers = directCallers == null ? Set.of() : Set.copyOf(directCallers);
        transitiveCallerDepths = transitiveCallerDepths == null ? Map.of() : Map.copyOf(transitiveCallerDepths);
        if (cumulativeRisk < 0.0) {
            throw new IllegalArgumentException("cumulativeRisk cannot be negative: " + cumulativeRisk);
        }
        recommendedTestScope = recommendedTestScope == null ? List.of() : List.copyOf(recommendedTestScope);
    }
}
