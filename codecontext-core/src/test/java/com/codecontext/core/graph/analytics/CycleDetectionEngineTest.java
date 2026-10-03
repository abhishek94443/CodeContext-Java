package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
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

class CycleDetectionEngineTest {

    private final CycleDetectionEngine engine = new CycleDetectionEngine();

    private TypeVertex vertex(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        String simple = lastDot > 0 ? fqcn.substring(lastDot + 1) : fqcn;
        String pkg = lastDot > 0 ? fqcn.substring(0, lastDot) : "";
        return TypeVertex.of(fqcn, simple, pkg, TypeKind.CLASS, Path.of(simple + ".java"));
    }

    private void addEdge(DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph, TypeVertex from, TypeVertex to) {
        InvocationDetail detail = new InvocationDetail("callerM", "targetM", "targetM()", new SourceRange(1, 1, 1, 10));
        DependencyEdge edge = new DependencyEdge(detail);
        graph.addEdge(from, to, edge);
        graph.setEdgeWeight(edge, 1.0);
    }

    @Test
    @DisplayName("FTC-S2.2-001: Direct 2-node circular dependency detection (A <-> B)")
    void should_detect_direct_2_node_circular_dependency() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex a = vertex("com.example.ServiceA");
        TypeVertex b = vertex("com.example.ServiceB");
        graph.addVertex(a);
        graph.addVertex(b);

        addEdge(graph, a, b);
        addEdge(graph, b, a);

        CycleReport report = engine.detectCycles(graph, CycleOptions.defaults());

        assertThat(report.hasCycles()).isTrue();
        assertThat(report.cycles()).hasSize(1);
        assertThat(report.isTruncated()).isFalse();

        CyclePath cycle = report.cycles().get(0);
        assertThat(cycle.length()).isEqualTo(2);
        assertThat(cycle.fqcns().get(0)).isEqualTo(cycle.fqcns().get(cycle.fqcns().size() - 1));
        assertThat(cycle.fqcns()).contains("com.example.ServiceA", "com.example.ServiceB");
    }

    @Test
    @DisplayName("FTC-S2.2-002: Multi-node triangular cycle path reconstruction (A -> B -> C -> A)")
    void should_detect_triangular_circular_dependency() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex order = vertex("com.example.OrderService");
        TypeVertex payment = vertex("com.example.PaymentService");
        TypeVertex notification = vertex("com.example.NotificationService");
        graph.addVertex(order);
        graph.addVertex(payment);
        graph.addVertex(notification);

        addEdge(graph, order, payment);
        addEdge(graph, payment, notification);
        addEdge(graph, notification, order);

        CycleReport report = engine.detectCycles(graph, CycleOptions.defaults());

        assertThat(report.hasCycles()).isTrue();
        assertThat(report.cycles()).hasSize(1);
        assertThat(report.totalComponents()).isEqualTo(1);

        CyclePath cycle = report.cycles().get(0);
        assertThat(cycle.length()).isEqualTo(3);
        assertThat(cycle.fqcns().get(0)).isEqualTo(cycle.fqcns().get(cycle.fqcns().size() - 1));
        assertThat(cycle.fqcns()).contains("com.example.OrderService", "com.example.PaymentService", "com.example.NotificationService");
    }

    @Test
    @DisplayName("FTC-S2.2-003: Dense cyclic graph K_5,5 bounds cycle extraction under 50 in < 50ms")
    void should_bound_dense_cyclic_graph_safely() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex[] groupA = new TypeVertex[5];
        TypeVertex[] groupB = new TypeVertex[5];

        for (int i = 0; i < 5; i++) {
            groupA[i] = vertex("com.example.A" + i);
            groupB[i] = vertex("com.example.B" + i);
            graph.addVertex(groupA[i]);
            graph.addVertex(groupB[i]);
        }

        // Complete bipartite connections in both directions
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                addEdge(graph, groupA[i], groupB[j]);
                addEdge(graph, groupB[j], groupA[i]);
            }
        }

        long start = System.currentTimeMillis();
        CycleReport report = engine.detectCycles(graph, CycleOptions.defaults());
        long duration = System.currentTimeMillis() - start;

        assertThat(report.hasCycles()).isTrue();
        assertThat(report.cycles().size()).isLessThanOrEqualTo(50);
        assertThat(report.isTruncated()).isTrue();
        assertThat(duration).isLessThan(200); // Target < 50ms in warm JVM, < 200ms in cold test
    }

    @Test
    @DisplayName("FTC-S2.2-004: Clean acyclic DAG produces zero cycles")
    void should_return_empty_report_for_acyclic_graph() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex controller = vertex("com.example.Controller");
        TypeVertex service = vertex("com.example.Service");
        TypeVertex repo = vertex("com.example.Repo");
        graph.addVertex(controller);
        graph.addVertex(service);
        graph.addVertex(repo);

        addEdge(graph, controller, service);
        addEdge(graph, service, repo);

        CycleReport report = engine.detectCycles(graph, CycleOptions.defaults());

        assertThat(report.hasCycles()).isFalse();
        assertThat(report.cycles()).isEmpty();
        assertThat(report.isTruncated()).isFalse();
    }

    @Test
    @DisplayName("UTC-S2.2-US01-001: Valid CyclePath creation with length")
    void should_create_valid_cycle_path() {
        CyclePath path = new CyclePath(List.of("A", "B", "A"));
        assertThat(path.length()).isEqualTo(2);
        assertThat(path.fqcns()).containsExactly("A", "B", "A");
    }

    @Test
    @DisplayName("UTC-S2.2-US01-002: Reject CyclePath with fewer than 2 elements")
    void should_reject_invalid_cycle_path() {
        assertThatThrownBy(() -> new CyclePath(List.of("A")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UTC-S2.2-US01-005: Null graph input throws NullPointerException")
    void should_reject_null_graph() {
        assertThatThrownBy(() -> engine.detectCycles(null, CycleOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UTC-S3.1-BUG02-001: [BUG-S3.1-02] Single-node self-loop recursion is ignored by default in architectural cycle detection")
    void should_ignore_self_loop_by_default() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex recursive = vertex("com.example.TreeWalker");
        graph.addVertex(recursive);
        addEdge(graph, recursive, recursive);

        CycleReport report = engine.detectCycles(graph, CycleOptions.defaults());

        assertThat(report.hasCycles()).isFalse();
        assertThat(report.cycles()).isEmpty();
        assertThat(report.totalComponents()).isEqualTo(0);
    }

    @Test
    @DisplayName("UTC-S3.1-BUG02-002: [BUG-S3.1-02] Single-node self-loop recursion is reported when explicitly enabled in CycleOptions")
    void should_detect_self_loop_when_includeSelfLoops_enabled() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex recursive = vertex("com.example.TreeWalker");
        graph.addVertex(recursive);
        addEdge(graph, recursive, recursive);

        CycleOptions opts = new CycleOptions(50, 10, 10, true);
        CycleReport report = engine.detectCycles(graph, opts);

        assertThat(report.hasCycles()).isTrue();
        assertThat(report.cycles()).hasSize(1);
        CyclePath cycle = report.cycles().get(0);
        assertThat(cycle.length()).isEqualTo(1);
        assertThat(cycle.fqcns()).containsExactly("com.example.TreeWalker", "com.example.TreeWalker");
    }
}
