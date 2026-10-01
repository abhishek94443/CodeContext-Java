package com.codecontext.core.parser;

import com.codecontext.core.model.TypeDefinition;

import java.util.List;

/**
 * Result of parsing a single source file in Pass 1.
 */
public record ParseResult(
    List<TypeDefinition> typesDiscovered,
    List<String> errors
) {
    public ParseResult {
        typesDiscovered = typesDiscovered == null ? List.of() : List.copyOf(typesDiscovered);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}