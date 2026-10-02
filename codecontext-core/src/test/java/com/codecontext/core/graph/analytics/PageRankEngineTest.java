package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeKind;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PageRankEngineTest {

    private final PageRankEngine engine = new PageRankEngine();

    private TypeVertex createVertex(String fqcn) {
        return TypeVertex.of(fqcn, fqcn.substring(fqcn.lastIndexOf('.') + 1), "com.example", TypeKind.CLASS, Path.of(fqcn + ".java"));
    }

    @Test
    @DisplayName("FTC-S2.1-004: Star topology hub receives rank 1 with highest PageRank score")
    void should_rank_star_topology_hub_as_number_one() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex hub = createVertex("com.example.DatabaseConnector");
        graph.addVertex(hub);

        // 10 clients calling hub
        for (int i = 1; i <= 10; i++) {
            TypeVertex client = createVertex("com.example.ClientService" + i);
            graph.addVertex(client);
            DependencyEdge edge = new DependencyEdge(new InvocationDetail("call", "query", "query()", new SourceRange(1, 1, 1, 10)));
            graph.addEdge(client, hub, edge);
            graph.setEdgeWeight(edge, 1.0);
        }

        List<HotspotMetric> metrics = engine.compute(graph, PageRankOptions.defaults());

        assertThat(metrics).hasSize(11);
        HotspotMetric top = metrics.get(0);

        assertThat(top.fqcn()).isEqualTo("com.example.DatabaseConnector");
        assertThat(top.rank()).isEqualTo(1);
        assertThat(top.inDegree()).isEqualTo(10);
        assertThat(top.outDegree()).isEqualTo(0);
        assertThat(top.percentileScore()).isEqualTo(100.0);

        // Verify clients have lower score
        for (int i = 1; i < 11; i++) {
            HotspotMetric clientMetric = metrics.get(i);
            assertThat(clientMetric.rawScore()).isLessThan(top.rawScore());
            assertThat(clientMetric.inDegree()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("FTC-S2.1-005: Empty and disconnected graph numerical stability")
    void should_handle_empty_and_disconnected_graphs() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> emptyGraph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        List<HotspotMetric> emptyMetrics = engine.compute(emptyGraph, PageRankOptions.defaults());
        assertThat(emptyMetrics).isEmpty();

        // Disconnected 3 nodes
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> disconnected =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);
        disconnected.addVertex(createVertex("com.example.A"));
        disconnected.addVertex(createVertex("com.example.B"));
        disconnected.addVertex(createVertex("com.example.C"));

        List<HotspotMetric> discMetrics = engine.compute(disconnected, PageRankOptions.defaults());
        assertThat(discMetrics).hasSize(3);

        for (HotspotMetric m : discMetrics) {
            assertThat(Double.isNaN(m.rawScore())).isFalse();
            assertThat(Double.isInfinite(m.rawScore())).isFalse();
            assertThat(m.percentileScore()).isEqualTo(100.0); // uniform
        }
    }

    @Test
    @DisplayName("FTC-S2.1-007: Cyclic dependency pair terminates stably with equal scores")
    void should_stably_converge_on_cyclic_graph() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = createVertex("com.example.ServiceA");
        TypeVertex b = createVertex("com.example.ServiceB");
        graph.addVertex(a);
        graph.addVertex(b);

        DependencyEdge e1 = new DependencyEdge(new InvocationDetail("m1", "m2", "m2()", new SourceRange(1, 1, 1, 10)));
        DependencyEdge e2 = new DependencyEdge(new InvocationDetail("m2", "m1", "m1()", new SourceRange(2, 1, 2, 10)));
        graph.addEdge(a, b, e1);
        graph.addEdge(b, a, e2);
        graph.setEdgeWeight(e1, 1.0);
        graph.setEdgeWeight(e2, 1.0);

        List<HotspotMetric> metrics = engine.compute(graph, PageRankOptions.defaults());

        assertThat(metrics).hasSize(2);
        assertThat(metrics.get(0).rawScore()).isCloseTo(metrics.get(1).rawScore(), within(1e-4));
    }

    @Test
    @DisplayName("UTC-S2.1-US02-001: Null graph throws NullPointerException")
    void should_reject_null_graph() {
        assertThatThrownBy(() -> engine.compute(null, PageRankOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UTC-S2.1-US02-003: Linear pipeline A -> B -> C ranks C highest afferently")
    void should_rank_terminal_callee_highest_in_linear_pipeline() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = createVertex("com.example.A");
        TypeVertex b = createVertex("com.example.B");
        TypeVertex c = createVertex("com.example.C");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);

        DependencyEdge e1 = new DependencyEdge(new InvocationDetail("a", "b", "b()", new SourceRange(1, 1, 1, 10)));
        DependencyEdge e2 = new DependencyEdge(new InvocationDetail("b", "c", "c()", new SourceRange(2, 1, 2, 10)));
        graph.addEdge(a, b, e1);
        graph.addEdge(b, c, e2);
        graph.setEdgeWeight(e1, 1.0);
        graph.setEdgeWeight(e2, 1.0);

        List<HotspotMetric> metrics = engine.compute(graph, PageRankOptions.defaults());

        assertThat(metrics).hasSize(3);
        // C receives authority from B (which receives from A)
        assertThat(metrics.get(0).fqcn()).isEqualTo("com.example.C");
        assertThat(metrics.get(1).fqcn()).isEqualTo("com.example.B");
        assertThat(metrics.get(2).fqcn()).isEqualTo("com.example.A");
    }

    @Test
    @DisplayName("UTC-S2.1-US02-006: Boundary nodes excluded from hotspot output")
    void should_exclude_boundary_nodes_from_hotspot_ranking() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex internal = createVertex("com.example.Service");
        TypeVertex boundary = TypeVertex.boundary("java.util.List", "List", "java.util");

        graph.addVertex(internal);
        graph.addVertex(boundary);

        DependencyEdge edge = new DependencyEdge(new InvocationDetail("call", "size", "size()", new SourceRange(1, 1, 1, 10)));
        graph.addEdge(internal, boundary, edge);

        List<HotspotMetric> metrics = engine.compute(graph, PageRankOptions.defaults());

        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0).fqcn()).isEqualTo("com.example.Service");
    }
}
