package com.codecontext.core.resolver;

import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.index.SymbolLookup;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Context passed along the Chain of Responsibility for type and invocation resolution.
 */
public record ResolutionContext(
        String targetName,
        String currentPackage,
        Set<String> sameFileTypes,
        List<String> explicitImports,
        List<String> wildcardImports,
        SymbolLookup symbolLookup,
        TypeHierarchyIndex hierarchyIndex
) {
    public ResolutionContext {
        Objects.requireNonNull(targetName, "targetName cannot be null");
        currentPackage = currentPackage == null ? "" : currentPackage;
        sameFileTypes = sameFileTypes == null ? Set.of() : Set.copyOf(sameFileTypes);
        explicitImports = explicitImports == null ? List.of() : List.copyOf(explicitImports);
        wildcardImports = wildcardImports == null ? List.of() : List.copyOf(wildcardImports);
        Objects.requireNonNull(symbolLookup, "symbolLookup cannot be null");
        Objects.requireNonNull(hierarchyIndex, "hierarchyIndex cannot be null");
    }
}