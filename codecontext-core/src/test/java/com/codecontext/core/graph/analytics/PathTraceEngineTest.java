package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.InvocationKind;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeKind;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathTraceEngineTest {

    private DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph;
    private PathTraceEngine engine;

    private TypeVertex a;
    private TypeVertex b;
    private TypeVertex c;
    private TypeVertex d;
    private TypeVertex isolated;

    @BeforeEach
    void setUp() {
        graph = new DefaultDirectedWeightedGraph<>(DependencyEdge.class);
        engine = new PathTraceEngine();

        a = TypeVertex.of("com.example.A", "A", "com.example", TypeKind.CLASS, Path.of("A.java"));
        b = TypeVertex.of("com.example.B", "B", "com.example", TypeKind.CLASS, Path.of("B.java"));
        c = TypeVertex.of("com.example.C", "C", "com.example", TypeKind.CLASS, Path.of("C.java"));
        d = TypeVertex.of("com.example.D", "D", "com.example", TypeKind.CLASS, Path.of("D.java"));
        isolated = TypeVertex.of("com.example.Iso", "Iso", "com.example", TypeKind.CLASS, Path.of("Iso.java"));

        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addVertex(d);
        graph.addVertex(isolated);

        // Topology:
        // A -> B -> D
        // A -> C -> D
        addEdge(a, b, "handle", "process", 10);
        addEdge(b, d, "process", "execute", 25);
        addEdge(a, c, "handle", "transform", 12);
        addEdge(c, d, "transform", "execute", 30);
    }

    private void addEdge(TypeVertex src, TypeVertex tgt, String callerMethod, String targetMethod, int line) {
        InvocationDetail detail = new InvocationDetail(
                callerMethod,
                targetMethod,
                targetMethod + "()",
                InvocationKind.METHOD_CALL,
                new SourceRange(line, 1, line, 20)
        );
        DependencyEdge edge = new DependencyEdge(detail);
        graph.addEdge(src, tgt, edge);
    }

    @Test
    @DisplayName("UTC-S3.3-US02-001: should trace direct path between adjacent classes")
    void shouldTraceDirectPath() {
        var result = engine.tracePath(graph, "A", "B", 10);

        assertThat(result.pathFound()).isTrue();
        assertThat(result.hopCount()).isEqualTo(1);
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).source()).isEqualTo(a);
        assertThat(result.steps().get(0).target()).isEqualTo(b);
    }

    @Test
    @DisplayName("UTC-S3.3-US02-002: should trace shortest path in multi-hop diamond graph")
    void shouldTraceShortestPathInDiamond() {
        var result = engine.tracePath(graph, "A", "D", 10);

        assertThat(result.pathFound()).isTrue();
        assertThat(result.hopCount()).isEqualTo(2);
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(0).source()).isEqualTo(a);
        assertThat(result.steps().get(1).target()).isEqualTo(d);
    }

    @Test
    @DisplayName("UTC-S3.3-US02-003: should return pathFound=false for disconnected component")
    void shouldReportNoPathWhenDisconnected() {
        var result = engine.tracePath(graph, "A", "Iso", 10);

        assertThat(result.pathFound()).isFalse();
        assertThat(result.message()).contains("No execution path exists");
    }

    @Test
    @DisplayName("UTC-S3.3-US02-004: should return notFound if source or target class does not exist")
    void shouldReportNotFoundForMissingClass() {
        var result = engine.tracePath(graph, "Missing", "D", 10);
        assertThat(result.pathFound()).isFalse();
        assertThat(result.message()).contains("Source class not found");

        var result2 = engine.tracePath(graph, "A", "MissingTarget", 10);
        assertThat(result2.pathFound()).isFalse();
        assertThat(result2.message()).contains("Target class not found");
    }

    @Test
    @DisplayName("UTC-S3.3-US02-005: should report notFound if path exceeds max hops")
    void shouldRespectMaxHopsCap() {
        var result = engine.tracePath(graph, "A", "D", 1); // A -> D requires 2 hops

        assertThat(result.pathFound()).isFalse();
        assertThat(result.message()).contains("exceeds maximum allowed hops");
    }
}
