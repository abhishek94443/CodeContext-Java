package com.codecontext.core.graph.export;

import com.codecontext.core.git.GitEvolutionMetric;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskScore;
import com.codecontext.core.hierarchy.TypeHierarchyIndex;
import com.codecontext.core.model.TypeDefinition;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Payload data for standalone HTML architecture report generation.
 * Enriched with zero-waste code intelligence and git evolution dynamics.
 */
public record HtmlReportPayload(
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
        List<HotspotMetric> hotspots,
        CycleReport cycleReport,
        List<CompositeRiskScore> riskScores,
        Map<String, TypeDefinition> typeDefinitions,
        Map<String, GitEvolutionMetric> gitMetrics,
        TypeHierarchyIndex hierarchyIndex
) {
    public HtmlReportPayload {
        Objects.requireNonNull(graph, "graph cannot be null");
        hotspots = hotspots == null ? List.of() : List.copyOf(hotspots);
        cycleReport = cycleReport == null ? new CycleReport(List.of(), 0, false) : cycleReport;
        riskScores = riskScores == null ? List.of() : List.copyOf(riskScores);
        typeDefinitions = typeDefinitions == null ? Map.of() : Map.copyOf(typeDefinitions);
        gitMetrics = gitMetrics == null ? Map.of() : Map.copyOf(gitMetrics);
    }

    public HtmlReportPayload(
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            List<HotspotMetric> hotspots,
            CycleReport cycleReport,
            List<CompositeRiskScore> riskScores,
            Map<String, TypeDefinition> typeDefinitions
    ) {
        this(graph, hotspots, cycleReport, riskScores, typeDefinitions, Map.of(), null);
    }

    public HtmlReportPayload(
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            List<HotspotMetric> hotspots,
            CycleReport cycleReport,
            List<CompositeRiskScore> riskScores
    ) {
        this(graph, hotspots, cycleReport, riskScores, Map.of(), Map.of(), null);
    }
}
