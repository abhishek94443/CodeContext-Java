package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.TypeVertex;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.*;

/**
 * Detects circular dependencies using strongly connected components (SCC)
 * with bounded elementary cycle path extraction adhering to ADR-007.
 */
public class CycleDetectionEngine {

    public CycleReport detectCycles(
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            CycleOptions options
    ) {
        Objects.requireNonNull(graph, "graph cannot be null");
        CycleOptions opts = options == null ? CycleOptions.defaults() : options;

        if (graph.vertexSet().isEmpty()) {
            return new CycleReport(List.of(), 0, false);
        }

        // 1. Find strongly connected components in O(V + E) time
        KosarajuStrongConnectivityInspector<TypeVertex, DependencyEdge> inspector =
                new KosarajuStrongConnectivityInspector<>(graph);

        List<Set<TypeVertex>> components = inspector.stronglyConnectedSets();

        List<CyclePath> detectedCycles = new ArrayList<>();
        int cyclicComponentsCount = 0;
        boolean[] truncated = new boolean[]{false};

        for (Set<TypeVertex> component : components) {
            if (component.size() == 1) {
                // Ignore self-loop recursion by default in architectural cycle analysis (BUG-S3.1-02)
                if (opts.includeSelfLoops()) {
                    TypeVertex singleVertex = component.iterator().next();
                    if (graph.containsEdge(singleVertex, singleVertex)) {
                        cyclicComponentsCount++;
                        detectedCycles.add(new CyclePath(List.of(singleVertex.fqcn(), singleVertex.fqcn())));
                        if (detectedCycles.size() >= opts.maxTotalCycles()) {
                            truncated[0] = true;
                            break;
                        }
                    }
                }
                continue;
            }

            cyclicComponentsCount++;
            AsSubgraph<TypeVertex, DependencyEdge> subgraph = new AsSubgraph<>(graph, component);

            int[] componentCycleCount = new int[]{0};
            List<TypeVertex> sortedVertices = new ArrayList<>(component);
            sortedVertices.sort(Comparator.comparing(TypeVertex::fqcn));

            for (TypeVertex startVertex : sortedVertices) {
                if (detectedCycles.size() >= opts.maxTotalCycles() || componentCycleCount[0] >= opts.maxCyclesPerComponent()) {
                    truncated[0] = true;
                    break;
                }

                List<TypeVertex> currentPath = new ArrayList<>();
                Set<TypeVertex> visitedOnPath = new HashSet<>();
                currentPath.add(startVertex);
                visitedOnPath.add(startVertex);

                findCyclesDfs(
                        subgraph,
                        startVertex,
                        startVertex,
                        currentPath,
                        visitedOnPath,
                        detectedCycles,
                        opts,
                        componentCycleCount,
                        truncated
                );
            }

            if (detectedCycles.size() >= opts.maxTotalCycles()) {
                truncated[0] = true;
                break;
            }
        }

        return new CycleReport(
                detectedCycles,
                cyclicComponentsCount,
                truncated[0] || detectedCycles.size() >= opts.maxTotalCycles()
        );
    }

    private void findCyclesDfs(
            AsSubgraph<TypeVertex, DependencyEdge> subgraph,
            TypeVertex startVertex,
            TypeVertex currentVertex,
            List<TypeVertex> currentPath,
            Set<TypeVertex> visitedOnPath,
            List<CyclePath> detectedCycles,
            CycleOptions opts,
            int[] componentCycleCount,
            boolean[] truncated
    ) {
        if (detectedCycles.size() >= opts.maxTotalCycles() || componentCycleCount[0] >= opts.maxCyclesPerComponent()) {
            truncated[0] = true;
            return;
        }

        if (currentPath.size() > opts.maxCycleLength()) {
            truncated[0] = true;
            return;
        }

        for (DependencyEdge edge : subgraph.outgoingEdgesOf(currentVertex)) {
            TypeVertex next = subgraph.getEdgeTarget(edge);

            if (next.equals(startVertex) && currentPath.size() > 1) {
                List<String> fqcns = new ArrayList<>(currentPath.stream().map(TypeVertex::fqcn).toList());
                fqcns.add(startVertex.fqcn());

                if (!isDuplicateCycle(detectedCycles, fqcns)) {
                    detectedCycles.add(new CyclePath(fqcns));
                    componentCycleCount[0]++;
                }

                if (detectedCycles.size() >= opts.maxTotalCycles() || componentCycleCount[0] >= opts.maxCyclesPerComponent()) {
                    truncated[0] = true;
                    return;
                }
            } else if (!visitedOnPath.contains(next) && next.fqcn().compareTo(startVertex.fqcn()) >= 0) {
                visitedOnPath.add(next);
                currentPath.add(next);

                findCyclesDfs(
                        subgraph,
                        startVertex,
                        next,
                        currentPath,
                        visitedOnPath,
                        detectedCycles,
                        opts,
                        componentCycleCount,
                        truncated
                );

                currentPath.remove(currentPath.size() - 1);
                visitedOnPath.remove(next);
            }
        }
    }

    private boolean isDuplicateCycle(List<CyclePath> detected, List<String> fqcns) {
        Set<String> set = new HashSet<>(fqcns);
        for (CyclePath existing : detected) {
            if (existing.fqcns().size() == fqcns.size() && new HashSet<>(existing.fqcns()).equals(set)) {
                return true;
            }
        }
        return false;
    }
}
