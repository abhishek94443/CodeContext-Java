package com.codecontext.core.hierarchy;

import com.codecontext.core.index.SymbolLookup;
import com.codecontext.core.model.MethodDefinition;
import com.codecontext.core.model.TypeDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Type Hierarchy Index (Pass 1.5).
 * Maintains superclass and interface DAG relationships with cycle detection
 * and depth bounding to prevent infinite recursion on corrupt code.
 */
public class TypeHierarchyIndex {

    private static final int MAX_HIERARCHY_DEPTH = 20;

    private final SymbolLookup symbolLookup;
    private final Map<String, String> directSuperclasses = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> directInterfaces = new ConcurrentHashMap<>();

    public TypeHierarchyIndex(SymbolLookup symbolLookup) {
        this.symbolLookup = Objects.requireNonNull(symbolLookup, "symbolLookup cannot be null");
    }

    /**
     * Index the inheritance relationships of a TypeDefinition.
     */
    public void index(TypeDefinition typeDefinition) {
        if (typeDefinition == null || typeDefinition.fqcn() == null) {
            return;
        }

        String fqcn = typeDefinition.fqcn();

        if (typeDefinition.superclass() != null && typeDefinition.superclass().isPresent()) {
            String sc = typeDefinition.superclass().get();
            if (!sc.isBlank()) {
                directSuperclasses.put(fqcn, sc);
            }
        }

        if (typeDefinition.interfaces() != null && !typeDefinition.interfaces().isEmpty()) {
            Set<String> ifaces = Collections.newSetFromMap(new ConcurrentHashMap<>());
            ifaces.addAll(typeDefinition.interfaces());
            directInterfaces.put(fqcn, Collections.unmodifiableSet(ifaces));
        }
    }

    /**
     * Find direct superclass of the given type FQCN, if declared.
     */
    public Optional<String> findDirectSuperclass(String fqcn) {
        return Optional.ofNullable(directSuperclasses.get(fqcn));
    }

    /**
     * Find direct interfaces implemented or extended by the given type FQCN.
     */
    public Set<String> findDirectInterfaces(String fqcn) {
        return directInterfaces.getOrDefault(fqcn, Collections.emptySet());
    }

    /**
     * Find all transitive supertypes in topological order (subclasses before superclasses, direct before ancestors).
     * Capped at MAX_HIERARCHY_DEPTH (20) with cycle detection.
     */
    public List<String> findAllSupertypes(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        visited.add(fqcn);

        // Enqueue direct superclass
        String superclass = directSuperclasses.get(fqcn);
        if (superclass != null && !superclass.equals(fqcn)) {
            queue.add(superclass);
        }

        // Enqueue direct interfaces
        Set<String> ifaces = directInterfaces.get(fqcn);
        if (ifaces != null) {
            for (String iface : ifaces) {
                if (!iface.equals(fqcn)) {
                    queue.add(iface);
                }
            }
        }

        while (!queue.isEmpty() && result.size() < MAX_HIERARCHY_DEPTH) {
            String current = queue.poll();
            if (visited.add(current)) {
                result.add(current);

                // Add next level ancestors
                String nextSuper = directSuperclasses.get(current);
                if (nextSuper != null && !visited.contains(nextSuper)) {
                    queue.add(nextSuper);
                }

                Set<String> nextIfaces = directInterfaces.get(current);
                if (nextIfaces != null) {
                    for (String ni : nextIfaces) {
                        if (!visited.contains(ni)) {
                            queue.add(ni);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Find a method by name in the type hierarchy starting from the given type.
     * Follows Java method resolution precedence:
     * 1. Subtype's own declaration (most specific)
     * 2. Direct superclass declarations
     * 3. Transitive supertypes / interfaces in topological order.
     */
    public Optional<MethodDefinition> findMethodInHierarchy(String fqcn, String methodName) {
        if (fqcn == null || methodName == null) {
            return Optional.empty();
        }

        // 1. Check current type
        Optional<TypeDefinition> currentTypeOpt = symbolLookup.findByFqcn(fqcn);
        if (currentTypeOpt.isPresent()) {
            for (MethodDefinition m : currentTypeOpt.get().methods()) {
                if (m.name().equals(methodName)) {
                    return Optional.of(m);
                }
            }
        }

        // 2. Traverse supertypes in topological order
        List<String> supertypes = findAllSupertypes(fqcn);
        for (String superFqcn : supertypes) {
            Optional<TypeDefinition> superTypeOpt = symbolLookup.findByFqcn(superFqcn);
            if (superTypeOpt.isPresent()) {
                for (MethodDefinition m : superTypeOpt.get().methods()) {
                    if (m.name().equals(methodName)) {
                        return Optional.of(m);
                    }
                }
            }
        }

        return Optional.empty();
    }
    /**
     * Find direct subclasses extending the given type FQCN.
     */
    public Set<String> findDirectSubclasses(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return Collections.emptySet();
        Set<String> subclasses = new HashSet<>();
        for (Map.Entry<String, String> entry : directSuperclasses.entrySet()) {
            String superName = entry.getValue();
            if (superName.equals(fqcn) || superName.endsWith("." + fqcn) || fqcn.endsWith("." + superName)) {
                subclasses.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(subclasses);
    }

    /**
     * Find direct implementors of the given interface FQCN.
     */
    public Set<String> findDirectImplementors(String ifaceFqcn) {
        if (ifaceFqcn == null || ifaceFqcn.isBlank()) return Collections.emptySet();
        Set<String> implementors = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : directInterfaces.entrySet()) {
            for (String iface : entry.getValue()) {
                if (iface.equals(ifaceFqcn) || iface.endsWith("." + ifaceFqcn) || ifaceFqcn.endsWith("." + iface)) {
                    implementors.add(entry.getKey());
                }
            }
        }
        return Collections.unmodifiableSet(implementors);
    }
}