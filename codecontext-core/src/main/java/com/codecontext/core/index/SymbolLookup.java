package com.codecontext.core.index;

import com.codecontext.core.model.TypeDefinition;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only interface for querying repository symbols (ISP compliance).
 */
public interface SymbolLookup {

    /**
     * Looks up a declared type by its Fully Qualified Class Name (FQCN).
     */
    Optional<TypeDefinition> findByFqcn(String fqcn);

    /**
     * Returns an unmodifiable set of all types declared within the given package.
     */
    Set<TypeDefinition> findByPackage(String packageName);

    /**
     * Returns an unmodifiable set of all types matching the given simple class name.
     */
    Set<TypeDefinition> findBySimpleName(String simpleName);

    /**
     * Returns an unmodifiable collection of all types registered in this symbol table.
     */
    Collection<TypeDefinition> allTypes();

    /**
     * Returns the total count of unique types registered in this symbol table.
     */
    int size();
}