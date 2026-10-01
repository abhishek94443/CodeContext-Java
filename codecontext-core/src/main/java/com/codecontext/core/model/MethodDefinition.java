package com.codecontext.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable declaration of a method or constructor.
 */
public record MethodDefinition(
    String name,
    String signature,
    String returnType,
    List<String> parameterTypes,
    List<String> parameterNames,
    Set<String> modifiers,
    boolean isConstructor,
    SourceRange range
) {
    public MethodDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        returnType = returnType == null ? "void" : returnType;
        parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        parameterNames = parameterNames == null ? List.of() : List.copyOf(parameterNames);
        modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers);
    }
}