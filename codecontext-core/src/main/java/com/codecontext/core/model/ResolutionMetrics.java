package com.codecontext.core.model;

/**
 * Resolution metrics for a compilation unit or scan run.
 */
public record ResolutionMetrics(
        int totalInvocations,
        int resolvedInternal,
        int resolvedExternal,
        int unresolvable
) {
    public double resolutionRatio() {
        if (totalInvocations <= 0) {
            return 1.0;
        }
        return (double) (resolvedInternal + resolvedExternal) / totalInvocations;
    }
}