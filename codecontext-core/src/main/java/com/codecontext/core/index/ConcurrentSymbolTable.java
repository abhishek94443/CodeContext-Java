package com.codecontext.core.index;

import com.codecontext.core.model.TypeDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe, lock-free implementation of SymbolRegistry and SymbolLookup.
 * Maintained with tri-index lookup by FQCN, package, and simple name.
 */
public class ConcurrentSymbolTable implements SymbolRegistry {

    private final ConcurrentMap<String, TypeDefinition> fqcnMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<TypeDefinition>> packageMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<TypeDefinition>> simpleNameMap = new ConcurrentHashMap<>();

    @Override
    public void register(TypeDefinition type) {
        Objects.requireNonNull(type, "type must not be null");

        // If replacing an existing entry for this FQCN, remove the old instance from secondary indices
        TypeDefinition existing = fqcnMap.put(type.fqcn(), type);
        if (existing != null) {
            Set<TypeDefinition> pkgSet = packageMap.get(existing.packageName());
            if (pkgSet != null) {
                pkgSet.remove(existing);
            }
            Set<TypeDefinition> nameSet = simpleNameMap.get(existing.simpleName());
            if (nameSet != null) {
                nameSet.remove(existing);
            }
        }

        // Index in package map
        packageMap.computeIfAbsent(type.packageName(), k -> ConcurrentHashMap.newKeySet()).add(type);

        // Index in simple name map
        simpleNameMap.computeIfAbsent(type.simpleName(), k -> ConcurrentHashMap.newKeySet()).add(type);
    }

    @Override
    public Optional<TypeDefinition> findByFqcn(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fqcnMap.get(fqcn));
    }

    @Override
    public Set<TypeDefinition> findByPackage(String packageName) {
        if (packageName == null) {
            return Set.of();
        }
        Set<TypeDefinition> set = packageMap.get(packageName);
        if (set == null || set.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public Set<TypeDefinition> findBySimpleName(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return Set.of();
        }
        Set<TypeDefinition> set = simpleNameMap.get(simpleName);
        if (set == null || set.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public Collection<TypeDefinition> allTypes() {
        return Collections.unmodifiableCollection(fqcnMap.values());
    }

    @Override
    public int size() {
        return fqcnMap.size();
    }

    @Override
    public void clear() {
        fqcnMap.clear();
        packageMap.clear();
        simpleNameMap.clear();
    }
}