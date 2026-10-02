package com.codecontext.core.resolver;

import java.util.Objects;

/**
 * Result produced by a ResolutionHandler in the Chain of Responsibility.
 */
public record ResolutionResult(
        String resolvedFqcn,
        boolean isResolvedLocally,
        String handlerName
) {
    public ResolutionResult {
        Objects.requireNonNull(resolvedFqcn, "resolvedFqcn cannot be null");
        Objects.requireNonNull(handlerName, "handlerName cannot be null");
    }
}