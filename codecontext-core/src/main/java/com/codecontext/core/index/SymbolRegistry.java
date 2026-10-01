package com.codecontext.core.index;

import com.codecontext.core.model.TypeDefinition;

/**
 * Mutation interface for cataloging repository declarations into the symbol table.
 */
public interface SymbolRegistry extends SymbolLookup {

    /**
     * Registers or updates a type definition in the symbol table.
     */
    void register(TypeDefinition type);

    /**
     * Clears all registered symbols across all indices.
     */
    void clear();
}