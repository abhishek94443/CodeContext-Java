package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.EdgeReversedGraph;

import java.util.*;

/**
 * Computes upstream blast radius using a zero-copy transposed graph (EdgeReversedGraph)
 * and distance-decayed cumulative risk scoring.
 */
public class BlastRadiusEngine {

    public ImpactAnalysisResult analyzeImpact(
            String targetFqcn,
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            Map<String, HotspotMetric> metrics,
            int maxDepth
    ) {
        Objects.requireNonNull(targetFqcn, "targetFqcn cannot be null");
        Objects.requireNonNull(graph, "graph cannot be null");
        Map<String, HotspotMetric> hotspotMetrics = metrics == null ? Map.of() : metrics;
        int depthLimit = maxDepth <= 0 ? 10 : maxDepth;

        TypeVertex target = graph.vertexSet().stream()
                .filter(v -> v.fqcn().equals(targetFqcn))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return new ImpactAnalysisResult(targetFqcn, Set.of(), Map.of(), 0.0, List.of());
        }

        // Zero-copy transposed view: caller -> callee becomes callee -> caller
        EdgeReversedGraph<TypeVertex, DependencyEdge> reversedGraph = new EdgeReversedGraph<>(graph);

        Set<String> directCallers = new LinkedHashSet<>();
        Map<String, Integer> transitiveCallerDepths = new LinkedHashMap<>();
        List<String> testScope = new ArrayList<>();

        // 1-hop direct callers
        for (DependencyEdge edge : reversedGraph.outgoingEdgesOf(target)) {
            TypeVertex caller = reversedGraph.getEdgeTarget(edge);
            directCallers.add(caller.fqcn());
        }

        // BFS traversal for transitive blast radius with visited guard against circular calls
        Queue<TypeVertex> queue = new ArrayDeque<>();
        Map<TypeVertex, Integer> depthMap = new HashMap<>();

        for (DependencyEdge edge : reversedGraph.outgoingEdgesOf(target)) {
            TypeVertex caller = reversedGraph.getEdgeTarget(edge);
            queue.add(caller);
            depthMap.put(caller, 1);
            transitiveCallerDepths.put(caller.fqcn(), 1);
            checkAndAddTestScope(caller.fqcn(), testScope);
        }

        while (!queue.isEmpty()) {
            TypeVertex current = queue.poll();
            int currentDepth = depthMap.get(current);

            if (currentDepth >= depthLimit) {
                continue;
            }

            for (DependencyEdge edge : reversedGraph.outgoingEdgesOf(current)) {
                TypeVertex upstreamCaller = reversedGraph.getEdgeTarget(edge);
                if (!depthMap.containsKey(upstreamCaller) && !upstreamCaller.equals(target)) {
                    int nextDepth = currentDepth + 1;
                    depthMap.put(upstreamCaller, nextDepth);
                    transitiveCallerDepths.put(upstreamCaller.fqcn(), nextDepth);
                    checkAndAddTestScope(upstreamCaller.fqcn(), testScope);
                    queue.add(upstreamCaller);
                }
            }
        }

        // Calculate distance-decayed cumulative risk: Sum( PR(u) / 2^(depth - 1) )
        double cumulativeRisk = 0.0;
        for (Map.Entry<String, Integer> entry : transitiveCallerDepths.entrySet()) {
            String callerFqcn = entry.getKey();
            int depth = entry.getValue();

            HotspotMetric metric = hotspotMetrics.get(callerFqcn);
            double prScore = metric != null ? metric.rawScore() : 0.01;
            double factor = Math.pow(2.0, depth - 1);
            cumulativeRisk += (prScore / factor);
        }

        return new ImpactAnalysisResult(
                targetFqcn,
                directCallers,
                transitiveCallerDepths,
                cumulativeRisk,
                testScope
        );
    }

    private void checkAndAddTestScope(String fqcn, List<String> testScope) {
        if (fqcn == null) return;
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        if (simpleName.endsWith("Test") || simpleName.endsWith("Tests")
                || simpleName.endsWith("IT") || simpleName.endsWith("TestCase")
                || fqcn.contains(".test.")) {
            if (!testScope.contains(fqcn)) {
                testScope.add(fqcn);
            }
        }
    }
}
