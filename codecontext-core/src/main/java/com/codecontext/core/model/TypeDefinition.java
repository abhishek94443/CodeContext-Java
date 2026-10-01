package com.codecontext.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable declaration of a Java type (class, interface, record, enum, annotation).
 */
public record TypeDefinition(
    String fqcn,
    String simpleName,
    String packageName,
    TypeKind kind,
    Path sourceFilePath,
    Optional<String> superclass,
    Set<String> interfaces,
    List<MethodDefinition> methods,
    List<FieldDefinition> fields,
    Set<String> annotations,
    SourceRange range
) {
    public TypeDefinition {
        Objects.requireNonNull(fqcn, "fqcn must not be null");
        Objects.requireNonNull(simpleName, "simpleName must not be null");
        packageName = packageName == null ? "" : packageName;
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(sourceFilePath, "sourceFilePath must not be null");
        superclass = superclass == null ? Optional.empty() : superclass;
        interfaces = interfaces == null ? Set.of() : Set.copyOf(interfaces);
        methods = methods == null ? List.of() : List.copyOf(methods);
        fields = fields == null ? List.of() : List.copyOf(fields);
        annotations = annotations == null ? Set.of() : Set.copyOf(annotations);
    }

    /**
     * Returns the file path with all backslashes normalized to standard POSIX forward slashes.
     */
    public String normalizedPathString() {
        return sourceFilePath.toString().replace('\\', '/');
    }
}