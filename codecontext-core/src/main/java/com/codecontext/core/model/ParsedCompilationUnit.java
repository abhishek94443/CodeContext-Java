package com.codecontext.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Complete immutable output of Phase 1 parsing for a single compilation unit.
 */
public record ParsedCompilationUnit(
        Path path,
        String packageName,
        List<String> imports,
        List<TypeDefinition> declaredTypes,
        List<InvocationReference> invocations,
        ResolutionMetrics metrics,
        List<String> parseErrors
) {
    public ParsedCompilationUnit {
        Objects.requireNonNull(path, "path cannot be null");
        packageName = packageName == null ? "" : packageName;
        imports = imports == null ? List.of() : List.copyOf(imports);
        declaredTypes = declaredTypes == null ? List.of() : List.copyOf(declaredTypes);
        invocations = invocations == null ? List.of() : List.copyOf(invocations);
        metrics = metrics == null ? new ResolutionMetrics(0, 0, 0, 0) : metrics;
        parseErrors = parseErrors == null ? List.of() : List.copyOf(parseErrors);
    }
}