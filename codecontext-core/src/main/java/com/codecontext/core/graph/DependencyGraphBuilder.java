package com.codecontext.core.graph;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.InvocationReference;
import com.codecontext.core.model.ParsedCompilationUnit;
import com.codecontext.core.model.TypeDefinition;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.*;

/**
 * Builds an in-memory JGraphT directed multigraph from Phase 1 compilation units.
 * Adheres to ADR-005: Directed edge U -> V denotes 'U invokes V' (Caller -> Callee).
 */
public class DependencyGraphBuilder {

    private static final Set<String> KNOWN_BOUNDARY_PREFIXES = Set.of(
            "java.", "javax.", "jakarta.", "org.springframework.", "org.apache.", "com.fasterxml.jackson."
    );

    public DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> build(
            List<ParsedCompilationUnit> units,
            GraphBuildOptions options
    ) {
        Objects.requireNonNull(units, "units cannot be null");
        GraphBuildOptions opts = options == null ? GraphBuildOptions.defaults() : options;

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        Map<String, TypeVertex> vertexRegistry = new HashMap<>();

        // 1. Register all declared types as vertices
        for (ParsedCompilationUnit unit : units) {
            for (TypeDefinition typeDef : unit.declaredTypes()) {
                TypeVertex vertex = TypeVertex.of(
                        typeDef.fqcn(),
                        typeDef.simpleName(),
                        typeDef.packageName(),
                        typeDef.kind(),
                        unit.path()
                );
                graph.addVertex(vertex);
                vertexRegistry.put(vertex.fqcn(), vertex);
            }
        }

        // 2. Add edges for invocations
        for (ParsedCompilationUnit unit : units) {
            for (InvocationReference invocation : unit.invocations()) {
                String callerFqcn = invocation.callerFqcn();
                String targetFqcn = invocation.targetFqcn();

                TypeVertex callerVertex = vertexRegistry.get(callerFqcn);
                if (callerVertex == null) {
                    if (isBoundary(callerFqcn)) {
                        if (opts.filterBoundaryNodes()) {
                            continue;
                        }
                    }
                    callerVertex = getOrCreateBoundaryVertex(graph, vertexRegistry, callerFqcn);
                }

                TypeVertex targetVertex = vertexRegistry.get(targetFqcn);
                if (targetVertex == null) {
                    if (opts.filterBoundaryNodes() || isBoundary(targetFqcn)) {
                        if (opts.filterBoundaryNodes()) {
                            continue; // Skip external boundary target
                        }
                    }
                    targetVertex = getOrCreateBoundaryVertex(graph, vertexRegistry, targetFqcn);
                }

                InvocationDetail detail = new InvocationDetail(
                        invocation.callerMethodName(),
                        invocation.targetMethodName(),
                        invocation.targetSignature(),
                        invocation.kind(),
                        invocation.range()
                );

                DependencyEdge existingEdge = graph.getEdge(callerVertex, targetVertex);
                if (existingEdge == null) {
                    DependencyEdge newEdge = new DependencyEdge(detail);
                    graph.addEdge(callerVertex, targetVertex, newEdge);
                    graph.setEdgeWeight(newEdge, newEdge.getWeight());
                } else {
                    existingEdge.recordCall(detail);
                    graph.setEdgeWeight(existingEdge, existingEdge.getWeight());
                }
            }
        }

        return graph;
    }

    private static boolean isBoundary(String fqcn) {
        if (fqcn == null) return false;
        for (String prefix : KNOWN_BOUNDARY_PREFIXES) {
            if (fqcn.startsWith(prefix)) return true;
        }
        return false;
    }

    private static TypeVertex getOrCreateBoundaryVertex(
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            Map<String, TypeVertex> vertexRegistry,
            String fqcn
    ) {
        return vertexRegistry.computeIfAbsent(fqcn, key -> {
            int lastDot = key.lastIndexOf('.');
            String pkg = lastDot > 0 ? key.substring(0, lastDot) : "";
            String simple = lastDot > 0 ? key.substring(lastDot + 1) : key;
            TypeVertex v = TypeVertex.boundary(key, simple, pkg);
            graph.addVertex(v);
            return v;
        });
    }
}
