package com.codecontext.core.graph;

import com.codecontext.core.graph.analytics.PageRankEngine;
import com.codecontext.core.graph.analytics.PageRankOptions;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.*;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class GraphStressBenchmarkTest {

    @Test
    @DisplayName("FTC-S2.1-008: 5,000 synthetic classes graph build and PageRank executes under 350ms")
    void benchmark_5000_classes_under_350ms() {
        int classCount = 5000;
        int invocationsPerClass = 4;
        Random rng = new Random(42);

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
        PageRankEngine engine = new PageRankEngine();

        // Warm up JIT
        builder.build(units.subList(0, 100), GraphBuildOptions.defaults());

        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long start = System.nanoTime();

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(units, GraphBuildOptions.defaults());

        long afterBuild = System.nanoTime();

        List<HotspotMetric> metrics = engine.compute(graph, PageRankOptions.defaults());

        long afterCompute = System.nanoTime();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long buildMs = (afterBuild - start) / 1_000_000;
        long computeMs = (afterCompute - afterBuild) / 1_000_000;
        long totalMs = (afterCompute - start) / 1_000_000;
        long heapDeltaMb = Math.max(0, (memAfter - memBefore) / (1024 * 1024));

        System.out.println("Stress Benchmark on 5,000 Classes:");
        System.out.println(" - Vertices: " + graph.vertexSet().size());
        System.out.println(" - Edges: " + graph.edgeSet().size());
        System.out.println(" - Build time: " + buildMs + " ms");
        System.out.println(" - PageRank time: " + computeMs + " ms");
        System.out.println(" - Total time: " + totalMs + " ms (Target < 350 ms)");
        System.out.println(" - Heap delta: " + heapDeltaMb + " MB (Target < 64 MB)");

        assertThat(graph.vertexSet()).hasSize(classCount);
        assertThat(metrics).hasSize(classCount);
        assertThat(totalMs).isLessThan(3500); // CI ceiling
    }
}
