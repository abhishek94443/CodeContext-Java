package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.*;

/**
 * Computes weighted PageRank centrality across the dependency graph.
 * Ranks internal types by afferent coupling gravity (incoming authority).
 */
public class PageRankEngine {

    public List<HotspotMetric> compute(
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            PageRankOptions options
    ) {
        Objects.requireNonNull(graph, "graph cannot be null");
        PageRankOptions opts = options == null ? PageRankOptions.defaults() : options;

        Set<TypeVertex> vertices = graph.vertexSet();
        if (vertices.isEmpty()) {
            return List.of();
        }

        // Run JGraphT PageRank algorithm
        PageRank<TypeVertex, DependencyEdge> pageRank = new PageRank<>(
                graph,
                opts.dampingFactor(),
                opts.maxIterations(),
                opts.tolerance()
        );

        Map<TypeVertex, Double> scores = pageRank.getScores();

        // Exclude boundary nodes from the final hotspot rankings
        List<Map.Entry<TypeVertex, Double>> internalScores = scores.entrySet().stream()
                .filter(e -> !e.getKey().isBoundaryNode())
                .sorted(Map.Entry.<TypeVertex, Double>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().fqcn()))
                .toList();

        if (internalScores.isEmpty()) {
            return List.of();
        }

        double maxScore = internalScores.get(0).getValue();
        double minScore = internalScores.get(internalScores.size() - 1).getValue();

        List<HotspotMetric> result = new ArrayList<>(internalScores.size());
        int rank = 1;
        int n = internalScores.size();

        for (int i = 0; i < n; i++) {
            Map.Entry<TypeVertex, Double> entry = internalScores.get(i);
            TypeVertex vertex = entry.getKey();
            double raw = entry.getValue();

            double percentile;
            if (n == 1 || (maxScore - minScore < 1e-9)) {
                percentile = 100.0;
            } else {
                percentile = 100.0 * (raw - minScore) / (maxScore - minScore);
            }

            int inDegree = graph.inDegreeOf(vertex);
            int outDegree = graph.outDegreeOf(vertex);

            result.add(new HotspotMetric(
                    vertex.fqcn(),
                    vertex.filePath().orElse(null),
                    raw,
                    percentile,
                    inDegree,
                    outDegree,
                    rank++
            ));
        }

        return Collections.unmodifiableList(result);
    }
}
