package com.codecontext.core.model;

import java.util.Objects;

/**
 * Immutable record representing a call site (method invocation, constructor call, etc.).
 */
public record InvocationReference(
        String callerFqcn,
        String callerMethodName,
        String targetFqcn,
        String targetMethodName,
        String targetSignature,
        InvocationKind kind,
        SourceRange range,
        boolean isResolvedLocally
) {
    public InvocationReference {
        Objects.requireNonNull(callerFqcn, "callerFqcn cannot be null");
        Objects.requireNonNull(callerMethodName, "callerMethodName cannot be null");
        Objects.requireNonNull(targetFqcn, "targetFqcn cannot be null");
        Objects.requireNonNull(targetMethodName, "targetMethodName cannot be null");
        targetSignature = targetSignature == null ? targetMethodName + "()" : targetSignature;
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(range, "range cannot be null");
    }
}