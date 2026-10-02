package com.codecontext.core.graph.model;

import com.codecontext.core.model.TypeKind;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable vertex representing a type (class, interface, record, enum) in the dependency graph.
 */
public record TypeVertex(
        String fqcn,
        String simpleName,
        String packageName,
        TypeKind kind,
        Optional<Path> filePath,
        boolean isBoundaryNode
) {
    public TypeVertex {
        Objects.requireNonNull(fqcn, "fqcn cannot be null");
        Objects.requireNonNull(simpleName, "simpleName cannot be null");
        packageName = packageName == null ? "" : packageName;
        Objects.requireNonNull(kind, "kind cannot be null");
        filePath = filePath == null ? Optional.empty() : filePath;
    }

    public static TypeVertex of(String fqcn, String simpleName, String packageName, TypeKind kind, Path filePath) {
        return new TypeVertex(fqcn, simpleName, packageName, kind, Optional.ofNullable(filePath), false);
    }

    public static TypeVertex boundary(String fqcn, String simpleName, String packageName) {
        return new TypeVertex(fqcn, simpleName, packageName, TypeKind.CLASS, Optional.empty(), true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeVertex other)) return false;
        return Objects.equals(fqcn, other.fqcn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fqcn);
    }

    @Override
    public String toString() {
        return fqcn + (isBoundaryNode ? " (boundary)" : "");
    }
}
