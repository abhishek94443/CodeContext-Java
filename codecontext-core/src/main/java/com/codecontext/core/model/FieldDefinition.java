package com.codecontext.core.model;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable declaration of a member field.
 */
public record FieldDefinition(
    String name,
    String type,
    Set<String> modifiers,
    SourceRange range
) {
    public FieldDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers);
    }
}