package com.codecontext.core.graph.model;

import com.codecontext.core.model.InvocationKind;
import com.codecontext.core.model.SourceRange;

import java.util.Objects;

/**
 * Immutable call site details attached to a dependency graph edge.
 */
public record InvocationDetail(
        String callerMethodName,
        String targetMethodName,
        String targetSignature,
        InvocationKind kind,
        SourceRange range
) {
    public InvocationDetail {
        Objects.requireNonNull(callerMethodName, "callerMethodName cannot be null");
        Objects.requireNonNull(targetMethodName, "targetMethodName cannot be null");
        targetSignature = targetSignature == null ? targetMethodName + "()" : targetSignature;
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(range, "range cannot be null");
    }

    public InvocationDetail(String callerMethodName, String targetMethodName, String targetSignature, SourceRange range) {
        this(callerMethodName, targetMethodName, targetSignature, InvocationKind.METHOD_CALL, range);
    }
}
