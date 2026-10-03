package com.codecontext.core.graph;

import com.codecontext.core.graph.analytics.CycleDetectionEngine;
import com.codecontext.core.graph.analytics.CycleOptions;
import com.codecontext.core.graph.analytics.CycleReport;
import com.codecontext.core.graph.analytics.PageRankEngine;
import com.codecontext.core.graph.analytics.PageRankOptions;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.graph.risk.CompositeRiskEngine;
import com.codecontext.core.graph.risk.CompositeRiskScore;
import com.codecontext.core.model.*;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class Phase2EndToEndStressTest {

    @Test
    @DisplayName("FTC-S2.3-005: Full Phase 2 pipeline on 5,000 classes finishes in < 2.0s under 512MB heap")
    void full_phase2_pipeline_stress_gate() {
        int classCount = 5000;
        int invocationsPerClass = 4;
        Random rng = new Random(99);

        List<ParsedCompilationUnit> units = new ArrayList<>(classCount);

        for (int i = 0; i < classCount; i++) {
            String fqcn = "com.enterprise.pkg" + (i % 50) + ".Class" + i;
            String simpleName = "Class" + i;
            String pkg = "com.enterprise.pkg" + (i % 50);

            List<InvocationReference> calls = new ArrayList<>(invocationsPerClass);
            for (int k = 0; k < invocationsPerClass; k++) {
                int targetIdx = rng.nextInt(classCount);
                String targetFqcn = "com.enterprise.pkg" + (targetIdx % 50) + ".Class" + targetIdx;
                calls.add(new InvocationReference(
                        fqcn,
                        "execute",
                        targetFqcn,
                        "process",
                        "process()",
                        InvocationKind.METHOD_CALL,
                        new SourceRange(10, 1, 10, 20),
                        true
                ));
            }

            TypeDefinition typeDef = new TypeDefinition(
                    fqcn,
                    simpleName,
                    pkg,
                    TypeKind.CLASS,
                    Path.of(simpleName + ".java"),
                    Optional.empty(),
                    Set.of(),
                    List.of(),
                    List.of(),
                    Set.of(),
                    new SourceRange(1, 1, 100, 1)
            );

            units.add(new ParsedCompilationUnit(
                    Path.of(simpleName + ".java"),
                    pkg,
                    List.of(),
                    List.of(typeDef),
                    calls,
                    new ResolutionMetrics(calls.size(), calls.size(), 0, 0),
                    List.of()
            ));
        }

        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        PageRankEngine prEngine = new PageRankEngine();
        CycleDetectionEngine cycleEngine = new CycleDetectionEngine();
        CompositeRiskEngine riskEngine = new CompositeRiskEngine();

        long start = System.currentTimeMillis();

        // 1. Build Graph
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        // 2. PageRank Centrality
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());

        // 3. Cycle Detection
        CycleReport cycleReport = cycleEngine.detectCycles(graph, CycleOptions.defaults());

        // 4. Composite Risk Scores (Git absent dynamic reweighting)
        List<CompositeRiskScore> riskScores = riskEngine.calculate(hotspots, cycleReport, Map.of(), false);

        long totalDurationMs = System.currentTimeMillis() - start;

        System.out.println("Phase 2 Full Pipeline Stress Gate (5,000 Classes):");
        System.out.println(" - Vertices: " + graph.vertexSet().size());
        System.out.println(" - Edges: " + graph.edgeSet().size());
        System.out.println(" - Hotspots computed: " + hotspots.size());
        System.out.println(" - Cycles detected: " + cycleReport.cycles().size() + " (truncated: " + cycleReport.isTruncated() + ")");
        System.out.println(" - Risk scores generated: " + riskScores.size());
        System.out.println(" - Total Pipeline Time: " + totalDurationMs + " ms (Target < 2,000 ms)");

        assertThat(graph.vertexSet()).hasSize(classCount);
        assertThat(hotspots).hasSize(classCount);
        assertThat(riskScores).hasSize(classCount);
        assertThat(totalDurationMs).isLessThan(2000); // Quality gate: < 2.0s
    }
}
